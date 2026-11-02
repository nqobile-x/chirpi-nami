package com.nqobile.chirpynami.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nqobile.chirpynami.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chirpy-NAMi Motion Graphics Logo Component.
 *
 * An iconic, non-AI emblem representing "The Acoustic Swift" (Inyoni ye-NAMi).
 * Constructed using pure geometric arcs: the soaring migratory bird intertwined with
 * mutual acoustic soundwaves ("NAMi" = With Me).
 *
 * Motion Graphics Pipeline:
 * 1. Initial Reveal: Concentric acoustic soundwaves propagate outward with spring physics.
 * 2. Elastic Convergence: The soundwaves morph and snap into the bird's wings.
 * 3. Aerodynamic Breathing Loop: Gentle, continuous vertical flotation and wing flexion.
 * 4. Interactive Tactile Response: Tapping triggers a spring pulse and soundwave ripple.
 */
@Composable
fun ChirpyMotionLogo(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animateOnLaunch: Boolean = true,
    onTap: () -> Unit = {}
) {
    // Reveal & Spring Morph Animation
    val revealProgress = remember { Animatable(if (animateOnLaunch) 0f else 1f) }
    var tapTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (animateOnLaunch) {
            revealProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.65f, // Satisfying elastic bounce
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Continuous Aerodynamic Flight Loop (Subtle breathing float)
    val infiniteTransition = rememberInfiniteTransition(label = "ChirpyFlightLoop")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatY"
    )

    val wingFlexAngle by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WingFlex"
    )

    // Interactive Tap Pulse
    val tapScale by animateFloatAsState(
        targetValue = if (tapTrigger % 2 == 1) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "TapScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatY
                rotationZ = wingFlexAngle * 0.5f
                scaleX = tapScale * revealProgress.value
                scaleY = tapScale * revealProgress.value
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tapTrigger++
                        tryAwaitRelease()
                        tapTrigger++
                        onTap()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val scale = w / 100f // 100x100 canonical coordinate space

            // 1. Concentric Acoustic Waves (NAMi Mesh Ripples)
            val waveAlpha = (1f - (floatY + 5f) / 10f).coerceIn(0.15f, 0.45f) * revealProgress.value
            drawCircle(
                color = RawOchre.copy(alpha = waveAlpha * 0.5f),
                radius = (w * 0.46f),
                center = Offset(w * 0.55f, h * 0.45f),
                style = Stroke(width = 1.5f * scale)
            )
            drawCircle(
                color = KalahariTerracotta.copy(alpha = waveAlpha * 0.3f),
                radius = (w * 0.54f),
                center = Offset(w * 0.55f, h * 0.45f),
                style = Stroke(width = 1f * scale)
            )

            // 2. Secondary Acoustic Resonance Arc (Raw Ochre)
            val secondaryWave = Path().apply {
                moveTo(30f * scale, 32f * scale)
                cubicTo(38f * scale, 24f * scale, 50f * scale, 18f * scale, 64f * scale, 16f * scale)
                cubicTo(67f * scale, 16f * scale, 70f * scale, 16.2f * scale, 72f * scale, 16.6f * scale)
                cubicTo(66f * scale, 19f * scale, 60f * scale, 23f * scale, 54f * scale, 28f * scale)
                cubicTo(44f * scale, 36f * scale, 36f * scale, 46f * scale, 30f * scale, 58f * scale)
                cubicTo(29f * scale, 54f * scale, 29f * scale, 42f * scale, 30f * scale, 32f * scale)
                close()
            }
            drawPath(path = secondaryWave, color = RawOchre, style = Fill)

            // 3. Primary Soaring Swift Silhouette (Kalahari Terracotta)
            val primarySwift = Path().apply {
                moveTo(84f * scale, 22f * scale)
                cubicTo(78f * scale, 19f * scale, 68f * scale, 18f * scale, 56f * scale, 21f * scale)
                cubicTo(40f * scale, 25f * scale, 26f * scale, 34f * scale, 14f * scale, 46f * scale)
                cubicTo(24f * scale, 42f * scale, 36f * scale, 40f * scale, 48f * scale, 42f * scale)
                cubicTo(38f * scale, 50f * scale, 30f * scale, 62f * scale, 22f * scale, 78f * scale)
                cubicTo(32f * scale, 68f * scale, 44f * scale, 60f * scale, 58f * scale, 56f * scale)
                cubicTo(48f * scale, 66f * scale, 42f * scale, 78f * scale, 36f * scale, 92f * scale)
                cubicTo(46f * scale, 80f * scale, 58f * scale, 70f * scale, 72f * scale, 64f * scale)
                cubicTo(82f * scale, 59f * scale, 90f * scale, 52f * scale, 94f * scale, 42f * scale)
                cubicTo(96f * scale, 36f * scale, 94f * scale, 28f * scale, 88f * scale, 24f * scale)
                cubicTo(87f * scale, 23f * scale, 85.5f * scale, 22.5f * scale, 84f * scale, 22f * scale)
                close()
            }
            drawPath(path = primarySwift, color = KalahariTerracotta, style = Fill)

            // 4. Focal Optical Beacon (Bone White Core Eye)
            drawCircle(
                color = BoneWhite,
                radius = 2.8f * scale,
                center = Offset(84f * scale, 31f * scale)
            )

            // Tactile Stone Rim around Beacon
            drawCircle(
                color = VolcanicUmber,
                radius = 1.2f * scale,
                center = Offset(84f * scale, 31f * scale)
            )
        }
    }
}
