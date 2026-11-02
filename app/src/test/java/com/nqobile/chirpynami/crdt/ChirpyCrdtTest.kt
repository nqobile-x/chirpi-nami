package com.nqobile.chirpynami.crdt

import com.nqobile.chirpynami.core.crdt.ChirpyCrdtLog
import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.crdt.ChirpyPayloadKind
import org.junit.Assert.*
import org.junit.Test

class ChirpyCrdtTest {

    @Test
    fun testOutboxCreationAndLamportProgression() {
        val log = ChirpyCrdtLog("node_jozi")
        val msg = log.createOutboxMessage("node_gaborone", "Sharp!".toByteArray(), ByteArray(32))

        assertEquals("node_jozi", msg.senderId)
        assertEquals("node_gaborone", msg.recipientId)
        assertEquals(1L, msg.lamportTimestamp)
        assertEquals(ChirpyDeliveryStatus.OUTBOX, msg.status)
        assertEquals(1L, log.getLocalVectorClock().clockMap["node_jozi"])
    }

    @Test
    fun testDeterministicCausalMerge() {
        val logA = ChirpyCrdtLog("node_A")
        val logB = ChirpyCrdtLog("node_B")

        val mA1 = logA.createOutboxMessage("node_B", "First".toByteArray(), ByteArray(32))
        val mA2 = logA.createOutboxMessage("node_B", "Second".toByteArray(), ByteArray(32))
        val mB1 = logB.createOutboxMessage("node_A", "Concurrent from B".toByteArray(), ByteArray(32))

        val logC = ChirpyCrdtLog("node_C")
        logC.mergeIncoming(listOf(mA2, mB1, mA1))

        val timeline = logC.getTimeline()
        assertEquals(3, timeline.size)

        val idx1 = timeline.indexOfFirst { it.messageId == mA1.messageId }
        val idx2 = timeline.indexOfFirst { it.messageId == mA2.messageId }
        assertTrue("mA1 must precede mA2", idx1 < idx2)
    }

    @Test
    fun testStatusTransitions() {
        val log = ChirpyCrdtLog("my_device")
        val msg = log.createOutboxMessage("peer_francistown", "Dumela".toByteArray(), ByteArray(32))

        assertEquals(ChirpyDeliveryStatus.OUTBOX, msg.status)

        log.updateStatus(msg.messageId, ChirpyDeliveryStatus.IN_FLIGHT_MESH)
        assertEquals(ChirpyDeliveryStatus.IN_FLIGHT_MESH, log.getTimeline().first().status)

        log.updateStatus(msg.messageId, ChirpyDeliveryStatus.DELIVERED)
        assertEquals(ChirpyDeliveryStatus.DELIVERED, log.getTimeline().first().status)

        log.updateStatus(msg.messageId, ChirpyDeliveryStatus.READ)
        assertEquals(ChirpyDeliveryStatus.READ, log.getTimeline().first().status)
    }
}
