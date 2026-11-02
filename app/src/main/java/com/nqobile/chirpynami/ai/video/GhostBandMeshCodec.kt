package com.nqobile.chirpynami.ai.video

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * The facial contours GhostBand transmits, as indices into the standard 468-point face mesh
 * (the MediaPipe / ML Kit topology). 139 points are enough to animate a recognisable face:
 * jaw line, brows, eyelids, nose and both lip lines, so blinks, speech and smiles come through.
 */
object GhostBandTopology {
    class Contour(val name: String, val meshIndices: IntArray, val closed: Boolean)

    val CONTOURS = listOf(
        Contour("faceOval", intArrayOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109), true),
        Contour("browLeft", intArrayOf(276, 283, 282, 295, 285, 336, 296, 334, 293, 300), true),
        Contour("browRight", intArrayOf(46, 53, 52, 65, 55, 107, 66, 105, 63, 70), true),
        Contour("eyeLeft", intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466), true),
        Contour("eyeRight", intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246), true),
        Contour("noseBridge", intArrayOf(168, 6, 197, 195, 5, 4), false),
        Contour("nostrils", intArrayOf(98, 97, 2, 326, 327), false),
        Contour("lipsOuter", intArrayOf(61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185), true),
        Contour("lipsInner", intArrayOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191), true)
    )

    /** Transmitted mesh indices, in wire order. */
    val SUBSET: IntArray = CONTOURS.flatMap { it.meshIndices.toList() }.toIntArray()

    /** Offset of each contour's first point within [SUBSET]. */
    val CONTOUR_OFFSETS: IntArray = CONTOURS.runningFold(0) { acc, c -> acc + c.meshIndices.size }.dropLast(1).toIntArray()

    fun contour(name: String) = CONTOURS.indexOfFirst { it.name == name }
}

/**
 * GhostBand wire format: one face frame in ~290 bytes.
 *
 *   version(1) | frame counter(2) | face box minX,minY,maxX,maxY (4 x u16, frame-normalised)
 *   | 139 x (x u8, y u8) relative to the face box
 *
 * Box-relative 8-bit coordinates give ~1/256 of face width precision (sub-millimetre on a real
 * face) while the 16-bit box still carries head movement across the frame. At 12 fps that is
 * ~28 kbps, versus 500-2,000 kbps for pixel video.
 */
object GhostBandMeshCodec {
    private const val VERSION = 1
    val FRAME_BYTES = 1 + 2 + 8 + GhostBandTopology.SUBSET.size * 2

    /** [points] are frame-normalised (0..1) x/y for each entry of [GhostBandTopology.SUBSET]. */
    fun encode(frameIndex: Long, points: List<Landmark3D>): ByteArray {
        require(points.size == GhostBandTopology.SUBSET.size)
        val minX = points.minOf { it.x }.coerceIn(0f, 1f)
        val minY = points.minOf { it.y }.coerceIn(0f, 1f)
        val maxX = points.maxOf { it.x }.coerceIn(minX + 1e-4f, 1f)
        val maxY = points.maxOf { it.y }.coerceIn(minY + 1e-4f, 1f)

        val bytes = ByteArrayOutputStream(FRAME_BYTES)
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeShort((frameIndex and 0xFFFF).toInt())
            for (v in floatArrayOf(minX, minY, maxX, maxY)) out.writeShort((v * 65535f).toInt())
            val w = maxX - minX
            val h = maxY - minY
            for (p in points) {
                out.writeByte((((p.x - minX) / w).coerceIn(0f, 1f) * 255f + 0.5f).toInt())
                out.writeByte((((p.y - minY) / h).coerceIn(0f, 1f) * 255f + 0.5f).toInt())
            }
        }
        return bytes.toByteArray()
    }

    fun decode(packet: ByteArray, timestampMs: Long = System.currentTimeMillis()): GhostBandLandmarks? = try {
        DataInputStream(ByteArrayInputStream(packet)).use { input ->
            if (input.readUnsignedByte() != VERSION) return null
            val frameIndex = input.readUnsignedShort().toLong()
            val minX = input.readUnsignedShort() / 65535f
            val minY = input.readUnsignedShort() / 65535f
            val maxX = input.readUnsignedShort() / 65535f
            val maxY = input.readUnsignedShort() / 65535f
            val points = List(GhostBandTopology.SUBSET.size) {
                Landmark3D(
                    x = minX + input.readUnsignedByte() / 255f * (maxX - minX),
                    y = minY + input.readUnsignedByte() / 255f * (maxY - minY),
                    z = 0f
                )
            }
            GhostBandLandmarks(
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                pitchDeg = 0f,
                yawDeg = 0f,
                rollDeg = 0f,
                eyeBlinkL = 1f - eyeOpenness(points, "eyeLeft"),
                eyeBlinkR = 1f - eyeOpenness(points, "eyeRight"),
                mouthOpen = mouthOpenness(points),
                smile = 0f,
                landmarks = points
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun span(points: List<Landmark3D>, contour: String): Pair<Float, Float> {
        val c = GhostBandTopology.contour(contour)
        val start = GhostBandTopology.CONTOUR_OFFSETS[c]
        val slice = points.subList(start, start + GhostBandTopology.CONTOURS[c].meshIndices.size)
        return (slice.maxOf { it.x } - slice.minOf { it.x }) to (slice.maxOf { it.y } - slice.minOf { it.y })
    }

    /** Eye height / width, normalised so a typical open eye reads ~1. */
    private fun eyeOpenness(points: List<Landmark3D>, eye: String): Float {
        val (w, h) = span(points, eye)
        return if (w <= 0f) 1f else (h / w / 0.3f).coerceIn(0f, 1f)
    }

    private fun mouthOpenness(points: List<Landmark3D>): Float {
        val (w, h) = span(points, "lipsInner")
        return if (w <= 0f) 0f else (h / w / 0.6f).coerceIn(0f, 1f)
    }
}
