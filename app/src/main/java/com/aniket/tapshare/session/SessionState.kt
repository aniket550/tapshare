package com.aniket.tapshare.session

import com.aniket.tapshare.handshake.ContactCard
import com.aniket.tapshare.handshake.ItemInfo
import com.aniket.tapshare.handshake.SessionMode

/** The other phone, as it introduced itself (display name + small photo). */
data class PeerInfo(val name: String, val avatarJpeg: ByteArray?) {
    override fun equals(other: Any?): Boolean = other is PeerInfo && name == other.name &&
        (avatarJpeg?.contentEquals(other.avatarJpeg) ?: (other.avatarJpeg == null))

    override fun hashCode(): Int = name.hashCode()
}

enum class ContactChoice { SHARE, RECEIVE_ONLY }

/** Progress of a photo/file transfer (both directions). */
data class Progress(val filesDone: Int, val filesTotal: Int, val bytesDone: Long, val bytesTotal: Long) {
    val fraction: Float
        get() = when {
            bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
            filesTotal > 0 -> filesDone.toFloat() / filesTotal
            else -> 0f
        }
}

enum class Outcome {
    /** Our photos/files arrived on the other phone. */
    SENT,

    /** We received photos/files (saved). */
    RECEIVED,

    /** We shared our card; they chose Receive Only. */
    CARD_SENT,

    /** Both chose Receive Only. */
    NOTHING_SHARED,

    DECLINED_BY_PEER,
    DECLINED_BY_ME,
    CANCELLED_BY_PEER,
}

enum class FailReason {
    /** Phones didn't manage to connect in time (other phone not ready / too far / radio busy). */
    CONNECT_TIMEOUT,

    /** Link dropped in the middle. */
    CONNECTION_LOST,

    /** Nearby/Play services error (code in [SessionState.Failed.code]). */
    NEARBY_ERROR,

    /** Google Play services missing/outdated, or a permission Nearby needs is missing. */
    NEARBY_UNAVAILABLE,

    /** Both phones had photos/files queued. */
    BOTH_SENDING,

    /** Other phone runs a different TapShare version. */
    VERSION_MISMATCH,

    /** We couldn't open one of our files to send. */
    READ_FAILED,

    /** A received file couldn't be saved. */
    SAVE_FAILED,
    NO_SPACE,
}

sealed interface SessionState {
    data object Idle : SessionState

    /** Phones touched: glow animation plays (~1 s) while Nearby connects in the background. */
    data class Touching(val mode: SessionMode, val startedAtMs: Long) : SessionState

    /** NameDrop: our own poster with Receive Only / Share. [peer] is null until the other phone says hello. */
    data class ContactExchange(
        val peer: PeerInfo?,
        val connected: Boolean,
        val myChoice: ContactChoice?,
    ) : SessionState

    /** Their card arrived (after we chose too): show it with Save / Done. */
    data class ContactReceived(val card: ContactCard, val iSharedMine: Boolean) : SessionState

    /** AirDrop sender side. */
    data class Sending(
        val peer: PeerInfo?,
        val items: List<ItemInfo>,
        val phase: SendPhase,
        val progress: Progress,
    ) : SessionState

    /** AirDrop receiver side: request, then progress after Accept. */
    data class Incoming(
        val peer: PeerInfo,
        val items: List<ItemInfo>,
        val previewJpeg: ByteArray?,
        val accepted: Boolean,
        val progress: Progress,
    ) : SessionState {
        override fun equals(other: Any?): Boolean = other is Incoming && peer == other.peer && items == other.items &&
            accepted == other.accepted && progress == other.progress &&
            (previewJpeg?.contentEquals(other.previewJpeg) ?: (other.previewJpeg == null))

        override fun hashCode(): Int = items.hashCode() * 31 + progress.hashCode()
    }

    data class Finished(
        val outcome: Outcome,
        val peerName: String?,
        val items: List<ItemInfo>,
        /** content:// Uris of saved files (RECEIVED only). */
        val savedUris: List<String>,
    ) : SessionState

    data class Failed(val reason: FailReason, val peerName: String?, val code: Int = 0) : SessionState
}

enum class SendPhase { CONNECTING, WAITING_FOR_ACCEPT, TRANSFERRING }
