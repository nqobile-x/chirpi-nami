package com.nqobile.chirpynami.ai.voice

import java.util.concurrent.ConcurrentSkipListMap

/**
 * Adaptive AI Jitter Buffer for Chirpy-NAMi.
 *
 * Coordinates directly with [GenerativePlcEngine] to guarantee uninterrupted 50 fps (20 ms)
 * audio delivery to the device's audio hardware.
 */
class AdaptiveJitterBuffer(
    private val plcEngine: GenerativePlcEngine
) {
    private val bufferQueue = ConcurrentSkipListMap<Long, NeuralVoicePacket>()
    private var nextExpectedSeq: Long = -1L

    var packetsReceived: Long = 0L
        private set
    var packetsSynthesized: Long = 0L
        private set

    fun push(packet: NeuralVoicePacket) {
        packetsReceived++
        bufferQueue[packet.seq] = packet
        if (nextExpectedSeq == -1L) {
            nextExpectedSeq = packet.seq
        }
    }

    /**
     * Called every 20 ms by the Nougat AudioTrack playout thread.
     * Guaranteed to ALWAYS return a voice packet (authentic or AI-synthesized),
     * completely eliminating playback gaps and stuttering.
     */
    fun popNextFrame(clockMs: Long): NeuralVoicePacket {
        if (nextExpectedSeq == -1L) {
            return plcEngine.synthesizeFrame(0L, clockMs)
        }

        val target = nextExpectedSeq
        val real = bufferQueue.remove(target)

        val frame: NeuralVoicePacket = if (real != null) {
            plcEngine.onPacketReceived(real)
            real
        } else {
            packetsSynthesized++
            plcEngine.synthesizeFrame(target, clockMs)
        }

        nextExpectedSeq = target + 1L
        return frame
    }

    val concealmentRatio: Double
        get() = if (packetsReceived + packetsSynthesized == 0L) 0.0
        else (packetsSynthesized.toDouble() / (packetsReceived + packetsSynthesized).toDouble()) * 100.0

    fun reset() {
        bufferQueue.clear()
        nextExpectedSeq = -1L
        plcEngine.reset()
    }
}
