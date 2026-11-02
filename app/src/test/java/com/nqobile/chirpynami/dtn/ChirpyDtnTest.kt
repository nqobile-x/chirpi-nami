package com.nqobile.chirpynami.dtn

import com.nqobile.chirpynami.core.crdt.ChirpyCrdtLog
import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.dtn.ChirpyBloomFilter
import com.nqobile.chirpynami.core.dtn.ChirpyDtnRouter
import org.junit.Assert.*
import org.junit.Test

class ChirpyDtnTest {

    @Test
    fun testBloomFilterFiltering() {
        val filter = ChirpyBloomFilter(1024, 3)
        val id = "bundle_kopfontein_01"

        assertFalse(filter.mightContain(id))
        filter.add(id)
        assertTrue(filter.mightContain(id))
    }

    @Test
    fun testCrossBorderMinibusTaxiBundleCarrying() {
        val logSender = ChirpyCrdtLog("node_jozi")
        val routerSender = ChirpyDtnRouter("node_jozi", logSender)

        val logRecipient = ChirpyCrdtLog("node_gaborone")
        val routerRecipient = ChirpyDtnRouter("node_gaborone", logRecipient)

        val msg = logSender.createOutboxMessage("node_gaborone", "Parcel on taxi".toByteArray(), ByteArray(32))
        val bundle = routerSender.storeOutboxBundle(msg)

        assertEquals(0, bundle.message.meshHops)

        // Commuter taxi takes bundle across Kopfontein border post
        val hoppedBundle = bundle.carryThroughNode("taxi_n1_transkalahari")
        assertEquals(1, hoppedBundle.message.meshHops)
        assertTrue(hoppedBundle.carrierTrail.contains("taxi_n1_transkalahari"))

        // Recipient device encounters taxi in Gaborone
        val delivered = routerRecipient.ingestPeerBundles(listOf(hoppedBundle), "taxi_n1_transkalahari")
        assertEquals(1, delivered.size)
        assertEquals(ChirpyDeliveryStatus.DELIVERED, delivered.first().status)
        assertEquals(2, delivered.first().meshHops)
    }
}
