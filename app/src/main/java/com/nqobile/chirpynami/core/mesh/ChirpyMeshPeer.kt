package com.nqobile.chirpynami.core.mesh

import java.io.Serializable

enum class ChirpyRadioKind(val label: String) {
    WIFI_DIRECT_NOUGAT("Wi-Fi Direct (Nougat)"), // Primary high-bandwidth radio on Android 7.0 (API 24/25)
    WIFI_AWARE_NAN("Wi-Fi Aware"),               // Modern high-bandwidth radio on Android 8.0+ (API 26+)
    BLE_MESH("Nearby radio (BLE / Wi-Fi)"),      // Pocket-to-pocket via Nearby Connections
    LOCAL_BRIDGE("Local bridge"),                // TCP link over an ADB bridge (phone <-> emulator testing)
    INTERNET_RELAY("Relay")                      // Nostr/libp2p fallback
}

data class ChirpyMeshPeer(
    val peerIdHex: String,
    val alias: String,
    val rssiDbm: Int,
    val radioKind: ChirpyRadioKind,
    val distanceMeters: Double = calculateDistance(rssiDbm),
    val supportsGhostBand: Boolean = true,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
) : Serializable {

    val isZeroDataRadio: Boolean
        get() = radioKind != ChirpyRadioKind.INTERNET_RELAY

    companion object {
        fun calculateDistance(rssi: Int, txPowerAtOneMeter: Int = -59): Double {
            if (rssi == 0) return -1.0
            val ratio = rssi.toDouble() / txPowerAtOneMeter.toDouble()
            return if (ratio < 1.0) {
                Math.pow(ratio, 10.0)
            } else {
                (0.89976) * Math.pow(ratio, 7.7095) + 0.111
            }
        }
    }
}
