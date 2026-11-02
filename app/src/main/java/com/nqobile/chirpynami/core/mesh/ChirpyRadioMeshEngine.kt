package com.nqobile.chirpynami.core.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Chirpy-NAMi Multi-Radio Mesh Engine.
 *
 * Provides transparent, hardware-adaptive radio routing:
 * - Detects Android 7.0 / 7.1 Nougat (API 24/25) -> Activates Wi-Fi Direct (P2P) + BLE
 * - Detects Android 8.0+ (API 26+) -> Activates Wi-Fi Aware (NAN) + BLE
 * - Automatically merges discovered peers into a unified real-time list.
 */
class ChirpyRadioMeshEngine(
    val localNodeId: String,
    val localAlias: String,
    val deviceApiLevel: Int
) {
    private val _discoveredPeers = MutableStateFlow<List<ChirpyMeshPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<ChirpyMeshPeer>> = _discoveredPeers.asStateFlow()

    private val peerCache = ConcurrentHashMap<String, ChirpyMeshPeer>()

    val activePrimaryRadio: ChirpyRadioKind = if (deviceApiLevel >= 26) {
        ChirpyRadioKind.WIFI_AWARE_NAN
    } else {
        ChirpyRadioKind.WIFI_DIRECT_NOUGAT
    }

    var isMeshActive: Boolean = false
        private set

    fun startMesh() {
        isMeshActive = true
    }

    fun stopMesh() {
        isMeshActive = false
        peerCache.clear()
        _discoveredPeers.value = emptyList()
    }

    fun onPeerDiscovered(peerId: String, name: String, rssi: Int, transportOverride: ChirpyRadioKind? = null) {
        val radio = transportOverride ?: activePrimaryRadio
        val peer = ChirpyMeshPeer(
            peerIdHex = peerId,
            alias = name,
            rssiDbm = rssi,
            radioKind = radio
        )
        peerCache[peerId] = peer
        _discoveredPeers.value = peerCache.values.toList().sortedByDescending { it.rssiDbm }
    }

    fun onPeerLost(peerId: String) {
        peerCache.remove(peerId)
        _discoveredPeers.value = peerCache.values.toList().sortedByDescending { it.rssiDbm }
    }
}
