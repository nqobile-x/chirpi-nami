package com.nqobile.chirpynami.ai

import com.nqobile.chirpynami.ai.video.GhostBandMeshCodec
import com.nqobile.chirpynami.ai.video.GhostBandTopology
import com.nqobile.chirpynami.ai.video.Landmark3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GhostBandMeshCodecTest {

    /** A synthetic face: points on an ellipse occupying part of the camera frame. */
    private fun syntheticFace(mouthOpen: Float = 0f): List<Landmark3D> {
        val n = GhostBandTopology.SUBSET.size
        val points = MutableList(n) { i ->
            val a = 2 * Math.PI * i / n
            Landmark3D((0.5 + 0.18 * cos(a)).toFloat(), (0.45 + 0.24 * sin(a)).toFloat(), 0f)
        }
        // Shape the inner lip contour so mouth openness is measurable.
        val c = GhostBandTopology.contour("lipsInner")
        val start = GhostBandTopology.CONTOUR_OFFSETS[c]
        val size = GhostBandTopology.CONTOURS[c].meshIndices.size
        for (k in 0 until size) {
            val a = 2 * Math.PI * k / size
            points[start + k] = Landmark3D(
                (0.5 + 0.05 * cos(a)).toFloat(),
                (0.6 + 0.03 * mouthOpen * sin(a)).toFloat(),
                0f
            )
        }
        return points
    }

    @Test
    fun topologyIsValid() {
        val subset = GhostBandTopology.SUBSET
        assertEquals(139, subset.size)
        assertEquals("indices must be unique", subset.size, subset.toSet().size)
        assertTrue(subset.all { it in 0 until 468 })
    }

    @Test
    fun frameIsCompactAndPrecise() {
        val face = syntheticFace()
        val packet = GhostBandMeshCodec.encode(42, face)
        assertEquals(GhostBandMeshCodec.FRAME_BYTES, packet.size)
        assertTrue("frame too large: ${packet.size}", packet.size <= 300)
        // 12 fps -> kbps
        assertTrue(packet.size * 8 * 12 / 1000 <= 30)

        val decoded = assertNotNull(GhostBandMeshCodec.decode(packet)).let { GhostBandMeshCodec.decode(packet)!! }
        assertEquals(42L, decoded.frameIndex)
        val faceWidth = face.maxOf { it.x } - face.minOf { it.x }
        val worst = face.indices.maxOf { i ->
            maxOf(abs(face[i].x - decoded.landmarks[i].x), abs(face[i].y - decoded.landmarks[i].y))
        }
        assertTrue("error $worst exceeds 0.5% of face width", worst < faceWidth * 0.005f)
    }

    @Test
    fun mouthOpennessSurvivesTheWire() {
        val closed = GhostBandMeshCodec.decode(GhostBandMeshCodec.encode(1, syntheticFace(0f)))!!
        val open = GhostBandMeshCodec.decode(GhostBandMeshCodec.encode(2, syntheticFace(1f)))!!
        assertTrue(open.mouthOpen > closed.mouthOpen + 0.5f)
    }
}
