package com.nqobile.chirpynami.ai.voice

import java.io.Serializable

/**
 * 20 ms frame of speech encoded via on-device Lyra v2 / EnCodec neural speech codec.
 * Encoded stream runs at only 1.6 kbps (4 bytes RVQ indices + 4 bytes pitch/energy hints).
 */
data class NeuralVoicePacket(
    val seq: Long,
    val timestampMs: Long,
    val rvqTokens: ByteArray,
    val pitchHarmonic: Float,
    val energyDb: Float,
    val isSynthetic: Boolean = false
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NeuralVoicePacket) return false
        return seq == other.seq
    }

    override fun hashCode(): Int = seq.hashCode()
}
