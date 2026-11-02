package com.nqobile.chirpynami.call

import com.nqobile.chirpynami.core.call.ChirpyAudioEngine
import com.nqobile.chirpynami.core.call.ImaAdpcmCodec
import com.nqobile.chirpynami.core.mesh.ChirpyCallAction
import com.nqobile.chirpynami.core.mesh.ChirpyWireCodec
import com.nqobile.chirpynami.core.mesh.ChirpyWireFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class ChirpyCallCodecTest {

    private val frame = ChirpyAudioEngine.FRAME_SAMPLES
    private val rate = ChirpyAudioEngine.SAMPLE_RATE

    /** A voice-like test tone: 220 Hz fundamental plus two harmonics. */
    private fun voiceLike(offset: Int) = ShortArray(frame) {
        val t = (offset + it).toDouble() / rate
        (6000 * sin(2 * PI * 220 * t) + 3000 * sin(2 * PI * 660 * t) + 1500 * sin(2 * PI * 1320 * t)).toInt().toShort()
    }

    private fun snrDb(reference: ShortArray, decoded: ShortArray): Double {
        var signal = 0.0
        var noise = 0.0
        for (i in reference.indices) {
            signal += reference[i].toDouble() * reference[i]
            val e = (reference[i] - decoded[i]).toDouble()
            noise += e * e
        }
        return 10 * log10(signal / noise)
    }

    @Test
    fun adpcmPacketIsFourToOneAndIntelligible() {
        val encoder = ImaAdpcmCodec.Encoder()
        var worst = Double.MAX_VALUE
        for (n in 0 until 25) { // one second of audio
            val pcm = voiceLike(n * frame)
            val packet = encoder.encode(pcm)
            assertEquals(3 + frame / 2, packet.size) // 163 bytes per 40 ms = 32.6 kbps
            if (n > 0) worst = minOf(worst, snrDb(pcm, ImaAdpcmCodec.decode(packet, frame)))
        }
        assertTrue("SNR too low: $worst dB", worst > 20.0)
    }

    @Test
    fun packetsDecodeIndependentlyAfterLoss() {
        val encoder = ImaAdpcmCodec.Encoder()
        val packets = (0 until 10).map { encoder.encode(voiceLike(it * frame)) }
        // Drop packets 3..6: packet 7 must still decode cleanly from its own header.
        val pcm7 = voiceLike(7 * frame)
        assertTrue(snrDb(pcm7, ImaAdpcmCodec.decode(packets[7], frame)) > 20.0)
    }

    @Test
    fun callAndMediaFramesRoundTrip() {
        val call = ChirpyWireFrame.Call(-42L, ChirpyCallAction.ACCEPT)
        assertEquals(call, ChirpyWireCodec.decode(ChirpyWireCodec.encode(call)))

        val payload = ByteArray(191) { it.toByte() }
        val media = ChirpyWireCodec.decode(ChirpyWireCodec.encode(ChirpyWireFrame.Media(7L, 1234, payload)))
            as ChirpyWireFrame.Media
        assertEquals(7L, media.callId)
        assertEquals(1234, media.seq)
        assertArrayEquals(payload, media.payload)
    }
}
