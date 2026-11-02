package com.nqobile.chirpynami.core.crdt

import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Chirpy-NAMi Conflict-Free Replicated Data Type (CRDT) Chat Timeline.
 *
 * Provides eventual consistency across intermittent offline encounters.
 * Messages from different taxi routes or BLE hops resolve into identical chronological order.
 */
class ChirpyCrdtLog(
    val localNodeId: String
) {
    private val lamportClock = AtomicLong(0L)
    private var vectorClock = VectorClock(mapOf(localNodeId to 0L))
    private val timelineMap = ConcurrentSkipListMap<String, ChirpyMessage>()

    fun getLocalVectorClock(): VectorClock = vectorClock

    fun createOutboxMessage(
        recipientId: String,
        ciphertext: ByteArray,
        signature: ByteArray,
        payloadKind: ChirpyPayloadKind = ChirpyPayloadKind.TEXT
    ): ChirpyMessage {
        val nextLamport = lamportClock.incrementAndGet()
        vectorClock = vectorClock.increment(localNodeId)

        val idString = "$localNodeId:$nextLamport:${System.currentTimeMillis()}"
        val messageId = java.security.MessageDigest.getInstance("SHA-256")
            .digest(idString.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val msg = ChirpyMessage(
            messageId = messageId,
            senderId = localNodeId,
            recipientId = recipientId,
            ciphertext = ciphertext,
            signature = signature,
            lamportTimestamp = nextLamport,
            vectorClock = vectorClock,
            payloadKind = payloadKind,
            status = ChirpyDeliveryStatus.OUTBOX,
            meshHops = 0
        )

        val key = makeOrderKey(msg)
        timelineMap[key] = msg
        return msg
    }

    fun mergeIncoming(messages: List<ChirpyMessage>): List<ChirpyMessage> {
        val newlyAdded = mutableListOf<ChirpyMessage>()
        for (m in messages) {
            val key = makeOrderKey(m)
            if (!timelineMap.containsKey(key)) {
                timelineMap[key] = m
                newlyAdded.add(m)
                lamportClock.updateAndGet { current -> maxOf(current, m.lamportTimestamp) }
                vectorClock = vectorClock.merge(m.vectorClock)
            }
        }
        return newlyAdded
    }

    fun getTimeline(): List<ChirpyMessage> = timelineMap.values.toList()

    fun updateStatus(messageId: String, newStatus: ChirpyDeliveryStatus) {
        for ((key, msg) in timelineMap) {
            if (msg.messageId == messageId) {
                if (msg.status.ordinal < newStatus.ordinal) {
                    timelineMap[key] = msg.copy(status = newStatus)
                }
                break
            }
        }
    }

    private fun makeOrderKey(msg: ChirpyMessage): String {
        return "%016d_%s_%s".format(msg.lamportTimestamp, msg.senderId, msg.messageId)
    }
}
