package com.nqobile.chirpynami.core.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline phone-to-phone radio transport over Google Nearby Connections.
 *
 * Nearby advertises and discovers over Bluetooth LE / Classic, then upgrades the link
 * to Wi-Fi Direct or hotspot bandwidth on its own, with zero mobile data.
 * P2P_CLUSTER lets every handset hold several links at once, forming the mesh.
 */
class ChirpyNearbyTransport(
    context: Context,
    private val localNodeId: String,
    private val localAlias: String,
    private val listener: ChirpyTransportListener
) {
    companion object {
        private const val TAG = "ChirpyNearby"
        private const val SERVICE_ID = "com.nqobile.chirpynami.mesh"
        private val STRATEGY = Strategy.P2P_CLUSTER

        /** Short, stable tag carried in the endpoint name so only one side dials. */
        fun nodeTag(nodeId: String): String =
            MessageDigest.getInstance("SHA-256").digest(nodeId.toByteArray())
                .take(6).joinToString("") { "%02x".format(it) }
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val localTag = nodeTag(localNodeId)
    private val endpointName = "${localAlias.take(24)}|$localTag"

    /** Nearby endpoint id -> Chirpi node id, once HELLO has been received. */
    private val endpointNodes = ConcurrentHashMap<String, String>()
    private val pendingEndpoints = ConcurrentHashMap.newKeySet<String>()

    var lastError: String? = null
        private set

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            when (val frame = ChirpyWireCodec.decode(bytes)) {
                is ChirpyWireFrame.Hello -> {
                    endpointNodes[endpointId] = frame.nodeId
                    listener.onLinkUp(frame.nodeId, frame.alias, ChirpyRadioKind.BLE_MESH, -60) { out ->
                        client.sendPayload(endpointId, Payload.fromBytes(out))
                    }
                }
                null -> Unit
                else -> endpointNodes[endpointId]?.let { listener.onFrame(it, frame) }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            pendingEndpoints.remove(endpointId)
            if (resolution.status.isSuccess) {
                val hello = ChirpyWireCodec.encode(ChirpyWireFrame.Hello(localNodeId, localAlias))
                client.sendPayload(endpointId, Payload.fromBytes(hello))
            } else {
                Log.w(TAG, "Connection to $endpointId failed: ${resolution.status}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpointNodes.remove(endpointId)?.let { listener.onLinkDown(it) }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val remoteTag = info.endpointName.substringAfterLast('|', "")
            // Both phones discover each other; the lower tag dials to avoid a connection race.
            if (remoteTag.isNotEmpty() && remoteTag < localTag) return
            if (endpointNodes.containsKey(endpointId) || !pendingEndpoints.add(endpointId)) return
            client.requestConnection(endpointName, endpointId, lifecycleCallback)
                .addOnFailureListener {
                    pendingEndpoints.remove(endpointId)
                    Log.w(TAG, "requestConnection failed", it)
                }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    fun start() {
        client.startAdvertising(
            endpointName, SERVICE_ID, lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnFailureListener { lastError = "Advertising: ${it.message}"; Log.w(TAG, "advertise failed", it) }

        client.startDiscovery(
            SERVICE_ID, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnFailureListener { lastError = "Discovery: ${it.message}"; Log.w(TAG, "discovery failed", it) }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        endpointNodes.values.forEach { listener.onLinkDown(it) }
        endpointNodes.clear()
        pendingEndpoints.clear()
    }
}
