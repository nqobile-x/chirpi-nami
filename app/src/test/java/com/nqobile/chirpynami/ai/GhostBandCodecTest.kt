package com.nqobile.chirpynami.ai

import com.nqobile.chirpynami.ai.video.GhostBandLandmarks
import com.nqobile.chirpynami.ai.video.GhostBandVideoCodec
import com.nqobile.chirpynami.ai.video.Landmark3D
import org.junit.Assert.*
import org.junit.Test

class GhostBandCodecTest {

    @Test
    fun testGhostBandUltraLowBandwidthCompression() {
        val codec = GhostBandVideoCodec()

        val samplePoints = (0 until 68).map { i ->
            Landmark3D(i * 0.01f - 0.34f, i * 0.012f - 0.4f, 0.02f)
        }

        val frame = GhostBandLandmarks(
            frameIndex = 1L,
            timestampMs = 1000L,
            pitchDeg = 4.0f,
            yawDeg = -6.0f,
            rollDeg = 1.0f,
            eyeBlinkL = 0.0f,
            eyeBlinkR = 0.0f,
            mouthOpen = 0.4f,
            smile = 0.7f,
            landmarks = samplePoints
        )

        val compressedBytes = codec.compress(frame)

        // Strict bandwidth threshold: Must be under 450 bytes (standard is ~200-250 bytes)
        assertTrue(
            "Compressed frame size was ${compressedBytes.size} bytes; must be under 450 bytes",
            compressedBytes.size < 450
        )

        val decompressed = codec.decompress(compressedBytes, lastDecoded = null)
        assertEquals(frame.frameIndex, decompressed.frameIndex)
        assertEquals(frame.pitchDeg, decompressed.pitchDeg, 1.0f)
        assertEquals(frame.mouthOpen, decompressed.mouthOpen, 0.05f)
        assertEquals(samplePoints.size, decompressed.landmarks.size)
    }
}
