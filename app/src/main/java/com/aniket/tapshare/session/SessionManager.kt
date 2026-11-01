package com.aniket.tapshare.session

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.aniket.tapshare.data.MyCardStore
import com.aniket.tapshare.discovery.DiscoveryEngine
import com.aniket.tapshare.discovery.DiscoveryEvent
import com.aniket.tapshare.handshake.ContactCard
import com.aniket.tapshare.handshake.ItemInfo
import com.aniket.tapshare.handshake.SessionCodec
import com.aniket.tapshare.handshake.SessionMessage
import com.aniket.tapshare.handshake.SessionMode
import com.aniket.tapshare.handshake.iAmAdvertiser
import com.aniket.tapshare.share.Outbox
import com.aniket.tapshare.share.OutgoingItem
import com.aniket.tapshare.transfer.NearbyTransport
import com.aniket.tapshare.transfer.ReceivedFileSaver
import com.aniket.tapshare.ui.animation.Haptics
import com.aniket.tapshare.util.TapLog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException

/**
 * One share session = one tap. Flow:
 *
 *  Tap ─► Touching (glow + haptic, Nearby connecting) ─► after ~1 s:
 *    • CONTACT ⇄ CONTACT: ContactExchange (our poster, Receive Only / Share) ─► ContactReceived / Finished
 *    • SEND → other phone: Sending (waiting) ─► other taps Accept ─► Sending (progress) ─► Finished(SENT)
 *    • other phone SENDs → us: Incoming (Accept / Decline) ─► progress ─► Finished(RECEIVED)
 *
 * Nothing personal leaves the phone before the user acts: Hello carries only the display name and
 * a tiny photo (like AirDrop); phone/email only after "Share"; files only after the receiver taps Accept.
 */
class SessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val discovery: DiscoveryEngine,
    private val myCard: MyCardStore,
    private val outbox: Outbox,
    private val haptics: Haptics,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val transport = NearbyTransport(context)

    // --- per-session data (reset in begin()) ---
    private var peerId: String? = null
    private var mode = SessionMode.CONTACT
    private var glowDone = false
    private var connected = false
    private var peer: PeerInfo? = null
    private var peerHello: SessionMessage.Hello? = null
    private val pending = ArrayList<SessionMessage>()
    private var myChoice: ContactChoice? = null
    private var theirChoice: ContactChoice? = null
    private var theirCard: ContactCard? = null
    private var sendingItems: List<OutgoingItem> = emptyList()
    private val outgoing = HashMap<Long, OutgoingTransfer>()
    private val incomingHeaders = HashMap<Long, SessionMessage.FileHeader>()
    private val incomingFiles = HashMap<Long, Payload>()
    private val incomingBytes = HashMap<Long, Long>()
    private val savedUris = ArrayList<String>()
    private var accepted = false
    private var timeoutJob: Job? = null
    private var glowJob: Job? = null
    private var closeJob: Job? = null

    private class OutgoingTransfer(val index: Int, val size: Long, val pfd: ParcelFileDescriptor) {
        var bytes: Long = 0
        var done = false
    }

    /** True while TapShare is on screen (v1: taps only start sessions when the app is open). */
    private var foreground = false

    init {
        scope.launch {
            discovery.events.collect { event ->
                when (event) {
                    is DiscoveryEvent.Tap -> onTap(event.peerId)
                    is DiscoveryEvent.Apart -> onApart(event.peerId)
                }
            }
        }
    }

    fun setForeground(visible: Boolean) {
        foreground = visible
        discovery.setTapsEnabled(visible && _state.value == SessionState.Idle)
    }

    // ================================================================ start / glow

    private fun onTap(tappedPeer: String) {
        if (_state.value != SessionState.Idle) return
        if (!foreground) {
            discovery.endSession() // v1: app must be open; don't keep advertising "triggered"
            return
        }
        begin(tappedPeer)
    }

    private fun begin(tappedPeer: String) {
        closeJob?.cancel()
        resetSessionData()
        peerId = tappedPeer
        sendingItems = outbox.items.value
        mode = if (sendingItems.isEmpty()) SessionMode.CONTACT else SessionMode.SEND
        discovery.setTapsEnabled(false)
        haptics.tap()
        _state.value = SessionState.Touching(mode, SystemClock.elapsedRealtime())
        TapLog.i("Session: start ($mode) with ${tappedPeer.take(8)}")

        val myId = discovery.myId
        transport.connect(myId, tappedPeer, iAmAdvertiser(myId, tappedPeer), transportListener)

        timeoutJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (!connected) fail(FailReason.CONNECT_TIMEOUT)
        }
        glowJob = scope.launch {
            delay(GLOW_MS)
            if (_state.value is SessionState.Touching) {
                glowDone = true
                // Free Bluetooth for Nearby Connections while we talk to the other phone.
                discovery.hold()
                refreshAfterGlow()
            }
        }
    }

    /** Pulling the phones apart during the glow cancels quietly (like iOS). */
    private fun onApart(apartPeer: String) {
        if (apartPeer == peerId && _state.value is SessionState.Touching) {
            TapLog.i("Session: pulled apart during glow, cancelling")
            transport.close()
            endToIdle()
        }
    }

    /** Decide which screen to show once the glow is over (and again whenever Hello arrives). */
    private fun refreshAfterGlow() {
        if (!glowDone) return
        val hello = peerHello
        when {
            mode == SessionMode.SEND && hello?.mode == SessionMode.SEND -> fail(FailReason.BOTH_SENDING)
            mode == SessionMode.SEND -> _state.value = SessionState.Sending(
                peer = peer,
                items = sendingItems.map { it.info },
                phase = when {
                    !connected || hello == null -> SendPhase.CONNECTING
                    accepted -> SendPhase.TRANSFERRING
                    else -> SendPhase.WAITING_FOR_ACCEPT
                },
                progress = sendProgress(),
            )
            hello?.mode == SessionMode.SEND -> _state.value = SessionState.Incoming(
                peer = peer ?: PeerInfo(hello.name, hello.avatarJpeg),
                items = hello.items,
                previewJpeg = hello.previewJpeg,
                accepted = accepted,
                progress = receiveProgress(),
            )
            else -> updateContactState()
        }
    }

    // ================================================================ transport callbacks

    private val transportListener = object : NearbyTransport.Listener {
        override fun onConnected() {
            connected = true
            timeoutJob?.cancel()
            haptics.connected()
            scope.launch {
                val hello = SessionMessage.Hello(
                    name = myCard.card.value.name.trim().ifEmpty { android.os.Build.MODEL },
                    avatarJpeg = myCard.photoJpeg(MyCardStore.AVATAR_PX, MyCardStore.AVATAR_MAX_BYTES),
                    mode = mode,
                    items = sendingItems.map { it.info },
                    previewJpeg = if (mode == SessionMode.SEND) outbox.previewJpeg() else null,
                )
                sendNow(hello)
                pending.forEach(::sendNow)
                pending.clear()
                refreshAfterGlow()
            }
        }

        override fun onFailed(statusCode: Int) {
            if (isOver()) return
            fail(mapNearbyError(statusCode), statusCode)
        }

        override fun onDisconnected() {
            if (isOver()) return
            // Sender closes as soon as its files are delivered; we may still be saving them.
            val expected = peerHello?.items?.size ?: 0
            if (accepted && expected > 0 && completedIncoming.size >= expected) return
            // The other side closing right after a completed contact exchange is normal.
            fail(FailReason.CONNECTION_LOST)
        }

        override fun onMessage(bytes: ByteArray) {
            val message = try {
                SessionCodec.decode(bytes)
            } catch (e: IOException) {
                TapLog.w("Session: bad message", e)
                fail(FailReason.VERSION_MISMATCH)
                return
            }
            handle(message)
        }

        override fun onFilePayload(payload: Payload) {
            incomingFiles[payload.id] = payload
        }

        override fun onTransferUpdate(update: PayloadTransferUpdate) {
            if (outgoing.containsKey(update.payloadId)) onOutgoingUpdate(update) else onIncomingUpdate(update)
        }
    }

    private fun handle(message: SessionMessage) {
        when (message) {
            is SessionMessage.Hello -> {
                peerHello = message
                peer = PeerInfo(message.name, message.avatarJpeg)
                TapLog.i("Session: hello from ${message.name} (${message.mode}, ${message.items.size} items)")
                refreshAfterGlow()
            }
            is SessionMessage.Card -> {
                theirCard = message.card
                theirChoice = ContactChoice.SHARE
                updateContactState()
            }
            SessionMessage.ReceiveOnly -> {
                theirChoice = ContactChoice.RECEIVE_ONLY
                updateContactState()
            }
            SessionMessage.Accept -> if (mode == SessionMode.SEND) startSendingFiles()
            SessionMessage.Decline -> finish(Outcome.DECLINED_BY_PEER)
            is SessionMessage.FileHeader -> {
                incomingHeaders[message.payloadId] = message
                maybeSaveIncoming(message.payloadId)
            }
            SessionMessage.Cancel -> {
                outgoing.keys.forEach(transport::cancelPayload)
                finish(Outcome.CANCELLED_BY_PEER)
            }
        }
    }

    // ================================================================ contact exchange

    /** "Share" on the poster. The UI only enables it once My Card has a name. */
    fun shareMyCard() {
        if (myChoice != null || _state.value !is SessionState.ContactExchange) return
        myChoice = ContactChoice.SHARE
        // Show "sharing…" right away, but only move on (and maybe close) after the card is queued.
        _state.value = SessionState.ContactExchange(peer, connected, ContactChoice.SHARE)
        scope.launch {
            send(SessionMessage.Card(myCard.toContactCard()))
            updateContactState()
        }
    }

    fun receiveOnly() {
        if (myChoice != null || _state.value !is SessionState.ContactExchange) return
        myChoice = ContactChoice.RECEIVE_ONLY
        send(SessionMessage.ReceiveOnly)
        updateContactState()
    }

    private fun updateContactState() {
        if (!glowDone || mode != SessionMode.CONTACT || peerHello?.mode == SessionMode.SEND || isOver()) return
        val mine = myChoice
        val card = theirCard
        when {
            mine == null -> _state.value = SessionState.ContactExchange(peer, connected, null)
            card != null -> {
                if (_state.value !is SessionState.ContactReceived) haptics.success()
                _state.value = SessionState.ContactReceived(card, iSharedMine = mine == ContactChoice.SHARE)
                closeTransportSoon()
            }
            theirChoice == ContactChoice.RECEIVE_ONLY ->
                finish(if (mine == ContactChoice.SHARE) Outcome.CARD_SENT else Outcome.NOTHING_SHARED)
            else -> _state.value = SessionState.ContactExchange(peer, connected, mine)
        }
    }

    // ================================================================ sending files

    fun cancel() {
        if (isOver() || _state.value == SessionState.Idle) return
        send(SessionMessage.Cancel)
        outgoing.keys.forEach(transport::cancelPayload)
        transport.close()
        endToIdle()
    }

    private fun startSendingFiles() {
        if (accepted) return
        accepted = true
        TapLog.i("Session: receiver accepted, sending ${sendingItems.size} item(s)")
        sendingItems.forEachIndexed { index, item ->
            val pfd = try {
                context.contentResolver.openFileDescriptor(item.uri, "r")
            } catch (e: FileNotFoundException) {
                null
            } catch (e: SecurityException) {
                null
            }
            if (pfd == null) {
                TapLog.w("Session: can't open ${item.name}")
                send(SessionMessage.Cancel)
                fail(FailReason.READ_FAILED)
                return
            }
            val payload = Payload.fromFile(pfd)
            val size = if (item.size > 0) item.size else pfd.statSize.coerceAtLeast(0)
            outgoing[payload.id] = OutgoingTransfer(index, size, pfd)
            send(SessionMessage.FileHeader(payload.id, index, item.name, item.mime, size))
            transport.send(payload)
        }
        refreshAfterGlow()
    }

    private fun onOutgoingUpdate(update: PayloadTransferUpdate) {
        val t = outgoing[update.payloadId] ?: return
        when (update.status) {
            PayloadTransferUpdate.Status.IN_PROGRESS -> t.bytes = update.bytesTransferred
            PayloadTransferUpdate.Status.SUCCESS -> {
                t.bytes = t.size
                t.done = true
                runCatching { t.pfd.close() }
            }
            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                runCatching { t.pfd.close() }
                if (!isOver()) fail(FailReason.CONNECTION_LOST)
                return
            }
        }
        if (outgoing.values.all { it.done }) {
            outbox.clear()
            finish(Outcome.SENT)
        } else {
            refreshAfterGlow()
        }
    }

    private fun sendProgress(): Progress = Progress(
        filesDone = outgoing.values.count { it.done },
        filesTotal = sendingItems.size,
        bytesDone = outgoing.values.sumOf { it.bytes },
        bytesTotal = sendingItems.sumOf { it.size.coerceAtLeast(0) },
    )

    // ================================================================ receiving files

    fun acceptIncoming() {
        if (accepted || _state.value !is SessionState.Incoming) return
        accepted = true
        send(SessionMessage.Accept)
        refreshAfterGlow()
    }

    fun declineIncoming() {
        if (_state.value !is SessionState.Incoming) return
        send(SessionMessage.Decline)
        finish(Outcome.DECLINED_BY_ME)
    }

    private fun onIncomingUpdate(update: PayloadTransferUpdate) {
        val id = update.payloadId
        if (!incomingHeaders.containsKey(id) && !incomingFiles.containsKey(id)) return // a small message
        when (update.status) {
            PayloadTransferUpdate.Status.IN_PROGRESS -> {
                incomingBytes[id] = update.bytesTransferred
                refreshAfterGlow()
            }
            PayloadTransferUpdate.Status.SUCCESS -> {
                incomingBytes[id] = update.totalBytes
                completedIncoming.add(id)
                maybeSaveIncoming(id)
            }
            PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED ->
                if (!isOver()) fail(FailReason.CONNECTION_LOST)
        }
    }

    private val completedIncoming = HashSet<Long>()
    private val savingOrSaved = HashSet<Long>()

    /** Save once we have the header (name/type), the payload and the "complete" update. */
    private fun maybeSaveIncoming(id: Long) {
        val header = incomingHeaders[id] ?: return
        val payload = incomingFiles[id] ?: return
        if (id !in completedIncoming || !savingOrSaved.add(id)) return
        val source: Uri = payload.asFile()?.asUri() ?: run {
            fail(FailReason.SAVE_FAILED)
            return
        }
        scope.launch {
            when (val result = ReceivedFileSaver.save(context, source, header.name, header.mime)) {
                is ReceivedFileSaver.Result.Saved -> {
                    savedUris.add(result.uri.toString())
                    val expected = peerHello?.items?.size ?: 0
                    if (savedUris.size >= expected && expected > 0) finish(Outcome.RECEIVED) else refreshAfterGlow()
                }
                ReceivedFileSaver.Result.NoSpace -> fail(FailReason.NO_SPACE)
                ReceivedFileSaver.Result.Failed -> fail(FailReason.SAVE_FAILED)
            }
        }
    }

    private fun receiveProgress(): Progress {
        val items = peerHello?.items.orEmpty()
        return Progress(
            filesDone = savedUris.size,
            filesTotal = items.size,
            bytesDone = incomingBytes.values.sum(),
            bytesTotal = items.sumOf { it.size.coerceAtLeast(0) },
        )
    }

    // ================================================================ end

    /** "Done" on a finished/failed screen, or "Done" after a received card. */
    fun dismiss() {
        transport.close()
        endToIdle()
    }

    private fun finish(outcome: Outcome) {
        if (isOver()) return
        timeoutJob?.cancel()
        when (outcome) {
            Outcome.SENT, Outcome.RECEIVED, Outcome.CARD_SENT -> haptics.success()
            else -> haptics.connected()
        }
        TapLog.i("Session: finished $outcome")
        _state.value = SessionState.Finished(
            outcome = outcome,
            peerName = peer?.name,
            items = if (mode == SessionMode.SEND) sendingItems.map { it.info } else peerHello?.items.orEmpty(),
            savedUris = savedUris.toList(),
        )
        closeTransportSoon()
    }

    private fun fail(reason: FailReason, code: Int = 0) {
        if (isOver()) return
        timeoutJob?.cancel()
        glowJob?.cancel()
        haptics.error()
        TapLog.w("Session: failed $reason code=$code")
        outgoing.values.forEach { runCatching { it.pfd.close() } }
        transport.close()
        glowDone = true
        discovery.release()
        discovery.endSession()
        _state.value = SessionState.Failed(reason, peer?.name, code)
    }

    /** Give the last messages a moment to leave before closing the link. */
    private fun closeTransportSoon() {
        discovery.release()
        discovery.endSession()
        closeJob?.cancel()
        closeJob = scope.launch {
            delay(CLOSE_DELAY_MS)
            transport.close()
        }
    }

    private fun endToIdle() {
        timeoutJob?.cancel()
        glowJob?.cancel()
        outgoing.values.forEach { runCatching { it.pfd.close() } }
        discovery.release()
        discovery.endSession()
        _state.value = SessionState.Idle
        discovery.setTapsEnabled(foreground)
    }

    private fun isOver(): Boolean = _state.value.let {
        it is SessionState.Finished || it is SessionState.Failed || it is SessionState.ContactReceived
    }

    // ================================================================ helpers

    private fun send(message: SessionMessage) {
        if (connected) sendNow(message) else pending.add(message)
    }

    private fun sendNow(message: SessionMessage) {
        val bytes = SessionCodec.encode(message)
        if (bytes.size > SessionCodec.MAX_MESSAGE_BYTES) {
            TapLog.e("Session: message too big (${bytes.size} B), dropping")
            return
        }
        transport.send(bytes)
    }

    private fun resetSessionData() {
        peerId = null
        glowDone = false
        connected = false
        peer = null
        peerHello = null
        pending.clear()
        myChoice = null
        theirChoice = null
        theirCard = null
        sendingItems = emptyList()
        outgoing.clear()
        incomingHeaders.clear()
        incomingFiles.clear()
        incomingBytes.clear()
        completedIncoming.clear()
        savingOrSaved.clear()
        savedUris.clear()
        accepted = false
    }

    private fun mapNearbyError(code: Int): FailReason = when (code) {
        ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
        ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
        ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES,
        ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
        ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE,
        CommonStatusCodes.API_NOT_CONNECTED,
        ConnectionResult.SERVICE_MISSING,
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
        ConnectionResult.SERVICE_DISABLED,
        -> FailReason.NEARBY_UNAVAILABLE
        else -> FailReason.NEARBY_ERROR
    }

    private companion object {
        /** iOS shows the poster about a second after the touch. */
        const val GLOW_MS = 950L
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val CLOSE_DELAY_MS = 2_000L
    }
}
