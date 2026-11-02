package com.nqobile.chirpynami.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.nqobile.chirpynami.ai.video.GhostBandLandmarks
import com.nqobile.chirpynami.ai.video.GhostBandTopology
import com.nqobile.chirpynami.ai.video.NeuralAvatarRenderer
import com.nqobile.chirpynami.ui.theme.*

/**
 * Draws a GhostBand face from transmitted geometry: a warm line-portrait in the app palette.
 *
 * Incoming frames (~12 fps) feed [NeuralAvatarRenderer], which eases between them every display
 * frame, so blinks, speech and head turns move smoothly instead of stepping.
 */
@Composable
fun GhostBandAvatar(
    face: GhostBandLandmarks?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    val renderer = remember { NeuralAvatarRenderer(referencePhotoHash = "ghostband") }
    var smoothed by remember { mutableStateOf<GhostBandLandmarks?>(null) }

    LaunchedEffect(face) { face?.let { renderer.updateTelemetry(it) } }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 1f / 60f else (now - lastNanos) / 1_000_000_000f
                lastNanos = now
                smoothed = renderer.stepRenderLoop(dt)
            }
        }
    }

    Canvas(modifier) {
        val f = smoothed ?: return@Canvas
        drawFace(f, mirror)
    }
}

private fun DrawScope.drawFace(face: GhostBandLandmarks, mirror: Boolean) {
    val pts = face.landmarks
    if (pts.size != GhostBandTopology.SUBSET.size) return

    // Frame the face: scale so it fills ~72% of the height, keep a damped share of its
    // position in the camera frame so head movement is still visible.
    val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }; val maxY = pts.maxOf { it.y }
    val faceH = (maxY - minY).coerceAtLeast(0.05f)
    val scale = size.height * 0.72f / faceH
    val cx = (minX + maxX) / 2f
    val cy = (minY + maxY) / 2f
    val driftX = (cx - 0.5f) * size.width * 0.35f * (if (mirror) -1f else 1f)
    val driftY = (cy - 0.5f) * size.height * 0.25f

    fun map(i: Int): Offset {
        val p = pts[i]
        val dx = (p.x - cx) * scale * (if (mirror) -1f else 1f)
        return Offset(size.width / 2f + dx + driftX, size.height / 2f + (p.y - cy) * scale + driftY)
    }

    fun contourPath(name: String): Path {
        val c = GhostBandTopology.contour(name)
        val contour = GhostBandTopology.CONTOURS[c]
        val start = GhostBandTopology.CONTOUR_OFFSETS[c]
        val points = List(contour.meshIndices.size) { map(start + it) }
        return smoothPath(points, contour.closed)
    }

    val stroke = size.minDimension * 0.012f
    val oval = contourPath("faceOval")
    drawPath(oval, ElevatedCharcoal)
    drawPath(oval, KalahariTerracotta, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

    drawPath(contourPath("browLeft"), BoneWhite.copy(alpha = 0.85f))
    drawPath(contourPath("browRight"), BoneWhite.copy(alpha = 0.85f))

    for (eye in listOf("eyeLeft", "eyeRight")) {
        val path = contourPath(eye)
        drawPath(path, BoneWhite)
        // Pupil, clipped to the lids so it disappears naturally on a blink.
        val c = GhostBandTopology.contour(eye)
        val start = GhostBandTopology.CONTOUR_OFFSETS[c]
        val eyePts = List(GhostBandTopology.CONTOURS[c].meshIndices.size) { map(start + it) }
        val center = Offset(eyePts.map { it.x }.average().toFloat(), eyePts.map { it.y }.average().toFloat())
        val width = eyePts.maxOf { it.x } - eyePts.minOf { it.x }
        clipPath(path) { drawCircle(VolcanicUmber, radius = width * 0.22f, center = center) }
        drawPath(path, MutedDust, style = Stroke(stroke * 0.5f))
    }

    val noseStyle = Stroke(stroke * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(contourPath("noseBridge"), MutedDust, style = noseStyle)
    drawPath(contourPath("nostrils"), MutedDust, style = noseStyle)

    drawPath(contourPath("lipsOuter"), KalahariTerracotta)
    drawPath(contourPath("lipsInner"), VolcanicUmber)
}

/** Quadratic smoothing through segment midpoints, so 8-bit points read as soft curves. */
private fun smoothPath(points: List<Offset>, closed: Boolean): Path = Path().apply {
    if (points.size < 3) {
        points.firstOrNull()?.let { moveTo(it.x, it.y) }
        points.drop(1).forEach { lineTo(it.x, it.y) }
        return@apply
    }
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    if (closed) {
        val start = mid(points.last(), points.first())
        moveTo(start.x, start.y)
        for (i in points.indices) {
            val p = points[i]
            val m = mid(p, points[(i + 1) % points.size])
            quadraticTo(p.x, p.y, m.x, m.y)
        }
        close()
    } else {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size - 1) {
            val m = mid(points[i], points[i + 1])
            quadraticTo(points[i].x, points[i].y, m.x, m.y)
        }
        lineTo(points.last().x, points.last().y)
    }
}
