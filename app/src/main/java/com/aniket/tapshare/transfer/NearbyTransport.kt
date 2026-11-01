package com.aniket.tapshare.transfer

import android.content.Context
import com.aniket.tapshare.util.TapLog
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionOptions
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionType
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * One encrypted 1-to-1 link to the phone we tapped, over Google Nearby Connections
 * (Bluetooth first, upgraded to Wi-Fi Direct/hotspot for speed). Knows nothing about
 * contacts or files — the session layer decides what to send. Callbacks arrive on the main thread.
 *
 * Both phones already know each other's random BLE ID from the tap, so:
 *  - Nearby endpoint name = our BLE ID (no personal data),
 *  - the phone with the smaller ID advertises, the other discovers and connects,
 *  - any endpoint whose name isn't the tapped phone is ignored/rejected.
 */
class NearbyTransport(context: Context) {

    interface Listener {
        fun onConnected()
        fun onFailed(statusCode: Int)
        fun onDisconnected()
        fun onMessage(bytes: ByteArray)
        fun onFilePayload(payload: Payload)
        fun onTransferUpdate(update: PayloadTransferUpdate)
    }

    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private var listener: Listener? = null
    private var peerName: String? = null
    private var myName: String = ""
    private var endpointId: String? = null
    private var requested = false
    private var connectionType = ConnectionType.BALANCED

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { listener?.onMessage(it) }
                Payload.Type.FILE -> listener?.onFilePayload(payload)
                else -> TapLog.w("Nearby: ignoring payload type ${payload.type}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            listener?.onTransferUpdate(update)
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (listener == null || info.endpointName != peerName) {
                TapLog.w("Nearby: rejecting unexpected endpoint ${info.endpointName}")
                client.rejectConnection(id)
                return
            }
            // The user already agreed physically (both phones held together); content needs a second "yes" in the UI.
            client.acceptConnection(id, payloadCallback).addOnFailureListener(::onTaskFailure)
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            val l = listener ?: return
            val status = result.status
            if (status.isSuccess) {
                endpointId = id
                client.stopAdvertising()
                client.stopDiscovery()
                TapLog.i("Nearby: connected")
                l.onConnected()
            } else {
                TapLog.w("Nearby: connection failed ${status.statusCode}")
                l.onFailed(status.statusCode)
            }
        }

        override fun onDisconnected(id: String) {
            if (id != endpointId) return
            endpointId = null
            TapLog.i("Nearby: disconnected")
            listener?.onDisconnected()
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (listener == null || info.endpointName != peerName || requested) return
            requested = true
            TapLog.i("Nearby: found the tapped phone, connecting")
            val options = ConnectionOptions.Builder().setConnectionType(connectionType).build()
            client.requestConnection(myName, id, lifecycle, options).addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) return@addOnFailureListener
                requested = false // allow a retry when the endpoint is found again
                onTaskFailure(e)
            }
        }

        override fun onEndpointLost(id: String) = Unit
    }

    /**
     * @param fast true = Nearby may switch Wi-Fi to a direct link for the best speed (DISRUPTIVE;
     *   internet can drop during the transfer). false = BALANCED.
     */
    fun connect(myId: String, peerId: String, advertise: Boolean, fast: Boolean, listener: Listener) {
        close()
        this.listener = listener
        myName = myId
        peerName = peerId
        requested = false
        connectionType = if (fast) ConnectionType.DISRUPTIVE else ConnectionType.BALANCED
        TapLog.i("Nearby: ${if (advertise) "advertising" else "discovering"} for $peerId (fast=$fast)")
        if (advertise) {
            val options = AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_POINT_TO_POINT)
                .setConnectionType(connectionType)
                .build()
            client.startAdvertising(myId, SERVICE_ID, lifecycle, options).addOnFailureListener(::onTaskFailure)
        } else {
            val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
            client.startDiscovery(SERVICE_ID, discoveryCallback, options).addOnFailureListener(::onTaskFailure)
        }
    }

    val isConnected: Boolean get() = endpointId != null

    /** Small message (< 30 KB). Returns false if not connected. */
    fun send(bytes: ByteArray): Boolean = send(Payload.fromBytes(bytes))

    /** Any payload (FILE payloads are announced by a FileHeader message first). */
    fun send(payload: Payload): Boolean {
        val id = endpointId ?: return false
        client.sendPayload(id, payload).addOnFailureListener(::onTaskFailure)
        return true
    }

    fun cancelPayload(payloadId: Long) {
        client.cancelPayload(payloadId)
    }

    /** Stops everything and forgets the listener (no more callbacks). Safe to call twice. */
    fun close() {
        listener = null
        endpointId = null
        peerName = null
        requested = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private fun onTaskFailure(e: Exception) {
        val code = (e as? ApiException)?.statusCode ?: ConnectionsStatusCodes.STATUS_ERROR
        if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING || code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) return
        TapLog.w("Nearby: task failed code=$code", e)
        listener?.onFailed(code)
    }

    companion object {
        const val SERVICE_ID = "com.aniket.tapshare"
    }
}
