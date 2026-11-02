package com.nqobile.chirpynami.mesh

import com.nqobile.chirpynami.core.mesh.ChirpyRadioKind
import com.nqobile.chirpynami.core.mesh.ChirpyRadioMeshEngine
import org.junit.Assert.*
import org.junit.Test

class NougatMeshFallbackTest {

    @Test
    fun testNougatSelectsWifiDirect() {
        val engineNougat = ChirpyRadioMeshEngine("node_nougat", "Samsung_J4", deviceApiLevel = 24)
        assertEquals(ChirpyRadioKind.WIFI_DIRECT_NOUGAT, engineNougat.activePrimaryRadio)

        val engineNougatMR1 = ChirpyRadioMeshEngine("node_nougat2", "Hisense_E30", deviceApiLevel = 25)
        assertEquals(ChirpyRadioKind.WIFI_DIRECT_NOUGAT, engineNougatMR1.activePrimaryRadio)
    }

    @Test
    fun testModernAndroidSelectsWifiAware() {
        val engineOreo = ChirpyRadioMeshEngine("node_oreo", "ModernPhone", deviceApiLevel = 26)
        assertEquals(ChirpyRadioKind.WIFI_AWARE_NAN, engineOreo.activePrimaryRadio)

        val engineApi34 = ChirpyRadioMeshEngine("node_34", "Galaxy_S24", deviceApiLevel = 34)
        assertEquals(ChirpyRadioKind.WIFI_AWARE_NAN, engineApi34.activePrimaryRadio)
    }

    @Test
    fun testPeerOrderingBySignalStrength() {
        val engine = ChirpyRadioMeshEngine("local", "Me", deviceApiLevel = 24)

        engine.onPeerDiscovered("peer_far", "Far_Peer", -85)
        engine.onPeerDiscovered("peer_close", "Close_Peer", -50)

        val peers = engine.discoveredPeers.value
        assertEquals(2, peers.size)
        assertEquals("peer_close", peers[0].peerIdHex)
        assertEquals("peer_far", peers[1].peerIdHex)
    }
}
