package com.nqobile.chirpynami.ai

import com.nqobile.chirpynami.ai.voice.AdaptiveJitterBuffer
import com.nqobile.chirpynami.ai.voice.GenerativePlcEngine
import com.nqobile.chirpynami.ai.voice.NeuralVoicePacket
import org.junit.Assert.*
import org.junit.Test

class ChirpyVoiceAiTest {

    @Test
    fun testGenerativePlcContinuityOnPacketLoss() {
        val plc = GenerativePlcEngine(maxConcealFrames = 10)

        val p0 = NeuralVoicePacket(0L, 0L, byteArrayOf(1, 2, 3, 4), 160f, -12f, false)
        val p1 = NeuralVoicePacket(1L, 20L, byteArrayOf(2, 3, 4, 5), 162f, -11f, false)

        plc.onPacketReceived(p0)
        plc.onPacketReceived(p1)

        // Packet 2 is lost!
        val synth2 = plc.synthesizeFrame(expectedSeq = 2L, timestampMs = 40L)
        assertTrue(synth2.isSynthetic)
        assertEquals(2L, synth2.seq)
        assertTrue(synth2.pitchHarmonic in 100f..300f)
    }

    @Test
    fun testZeroStutterJitterBufferPlayback() {
        val plc = GenerativePlcEngine()
        val buffer = AdaptiveJitterBuffer(plc)

        val p0 = NeuralVoicePacket(0L, 0L, byteArrayOf(10, 10, 10, 10), 150f, -10f, false)
        val p2 = NeuralVoicePacket(2L, 40L, byteArrayOf(12, 12, 12, 12), 152f, -10f, false)

        // Packet 1 was dropped in transit
        buffer.push(p0)
        buffer.push(p2)

        val f0 = buffer.popNextFrame(60L)
        val f1 = buffer.popNextFrame(80L)
        val f2 = buffer.popNextFrame(100L)

        assertFalse(f0.isSynthetic)
        assertTrue("Frame 1 must be synthesized by Generative AI", f1.isSynthetic)
        assertFalse(f2.isSynthetic)

        assertEquals(1L, buffer.packetsSynthesized)
    }
}
