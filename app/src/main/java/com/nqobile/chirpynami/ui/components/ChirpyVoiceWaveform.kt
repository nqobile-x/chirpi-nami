package com.nqobile.chirpynami.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.ui.theme.*
import kotlin.random.Random

/**
 * Tactile Acoustic Voice Note Waveform Player.
 *
 * Visualizes 1.6 kbps Lyra neural speech notes with an animated playhead,
 * amplitude bars, and duration counter. Zero neon, warm terracotta and stone finish.
 */
@Composable
fun ChirpyVoicePlayer(
    durationSeconds: Int = 18,
    isMine: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }

    // Static sample amplitudes for the voice note
    val amplitudes = remember {
        val r = Random(durationSeconds * 31)
        List(24) { r.nextFloat().coerceIn(0.2f, 1.0f) }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val step = 0.02f
            while (isPlaying && playbackProgress < 1f) {
                kotlinx.coroutines.delay(50)
                playbackProgress += step
            }
            if (playbackProgress >= 1f) {
                playbackProgress = 0f
                isPlaying = false
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause Circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isMine) KalahariTerracotta else ElevatedCharcoal)
                .border(1.dp, if (isMine) KalahariTerracotta else HairlineStone, CircleShape)
                .clickable { isPlaying = !isPlaying },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = BoneWhite,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Waveform Bars Canvas
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
        ) {
            val barCount = amplitudes.size
            val barWidth = 3.dp.toPx()
            val totalSpacing = size.width - (barCount * barWidth)
            val gap = totalSpacing / (barCount - 1)

            amplitudes.forEachIndexed { i, amp ->
                val x = i * (barWidth + gap)
                val barHeight = size.height * amp
                val y = (size.height - barHeight) / 2f

                val isPlayed = (i.toFloat() / barCount) <= playbackProgress
                val barColor = if (isPlayed) {
                    KalahariTerracotta
                } else {
                    SubduedAsh
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Duration / Codec Tag
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "0:${durationSeconds.toString().padStart(2, '0')}",
                color = BoneWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "1.6 kbps",
                color = RawOchre,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Tactile "Hold-to-Chirp" Voice Recorder.
 */
@Composable
fun ChirpyVoiceRecorderButton(
    onRecordComplete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordTimeSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordTimeSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordTimeSeconds++
            }
        }
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isRecording) RustMuted else ElevatedCharcoal)
            .border(1.dp, if (isRecording) RustMuted else HairlineStone, RoundedCornerShape(10.dp))
            .clickable {
                if (isRecording) {
                    onRecordComplete(recordTimeSeconds)
                    isRecording = false
                } else {
                    isRecording = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Chirp Voice Note",
            tint = if (isRecording) BoneWhite else MutedDust,
            modifier = Modifier.size(20.dp)
        )
    }
}
