package com.nqobile.chirpynami.core.crdt

import java.io.Serializable

enum class ChirpyDeliveryStatus {
    OUTBOX,           // On local device with no radio link
    IN_FLIGHT_MESH,   // Being carried by commuter taxi or peer mesh
    DELIVERED,        // Arrived at recipient handset
    READ              // Opened by recipient
}

enum class ChirpyPayloadKind {
    TEXT,
    VOICE_NOTE,       // 1.6 kbps neural audio
    STICKER,          // SADC animated vector sticker
    GHOSTBAND_KEY     // Initial facial photo reference
}

data class ChirpyMessage(
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val ciphertext: ByteArray,
    val signature: ByteArray,
    val lamportTimestamp: Long,
    val vectorClock: VectorClock,
    val payloadKind: ChirpyPayloadKind = ChirpyPayloadKind.TEXT,
    val status: ChirpyDeliveryStatus = ChirpyDeliveryStatus.OUTBOX,
    val meshHops: Int = 0,
    val epochMs: Long = System.currentTimeMillis()
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChirpyMessage) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
