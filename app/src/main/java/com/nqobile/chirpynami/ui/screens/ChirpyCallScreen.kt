package com.nqobile.chirpynami.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.nqobile.chirpynami.ai.video.GhostBandLandmarks
import com.nqobile.chirpynami.ai.video.GhostBandMeshCodec
import com.nqobile.chirpynami.core.call.ChirpyAudioEngine
import com.nqobile.chirpynami.ui.components.GhostBandAvatar
import com.nqobile.chirpynami.core.mesh.ChirpyMeshController.CallPhase
import com.nqobile.chirpynami.core.mesh.ChirpyMeshController.CallState
import com.nqobile.chirpynami.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Live offline voice call.
 *
 * Medium-format viewfinder styling, with telemetry that is measured, not decorative:
 * the bitrate and concealment figures come straight from the audio engine every second.
 */
@Composable
fun ChirpyCallScreen(
    call: CallState,
    statsProvider: () -> ChirpyAudioEngine.Stats?,
    onAccept: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onClose: () -> Unit,
    remoteFace: GhostBandLandmarks? = null,
    localFace: GhostBandLandmarks? = null,
    onToggleVideo: () -> Unit = {},
    onCameraBound: (androidx.lifecycle.LifecycleOwner) -> Unit = {},
    onCameraReleased: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onToggleVideo()
    }
    val requestVideoToggle = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (call.videoOn || granted) onToggleVideo() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // The camera lives exactly as long as video is on in a connected call on this screen.
    val cameraActive = call.phase == CallPhase.CONNECTED && call.videoOn
    DisposableEffect(cameraActive, lifecycleOwner) {
        if (cameraActive) onCameraBound(lifecycleOwner)
        onDispose { if (cameraActive) onCameraReleased() }
    }

    // A remote face counts as live while frames keep arriving.
    var remoteLive by remember { mutableStateOf(false) }
    LaunchedEffect(remoteFace) {
        remoteLive = remoteFace != null
        delay(1500)
        remoteLive = false
    }

    var elapsedSec by remember { mutableLongStateOf(0L) }
    var kbps by remember { mutableIntStateOf(0) }
    var concealPct by remember { mutableIntStateOf(0) }
    var micAvailable by remember { mutableStateOf(true) }

    LaunchedEffect(call.phase, call.callId) {
        var lastBytes = 0L
        while (call.phase == CallPhase.CONNECTED) {
            elapsedSec = (System.currentTimeMillis() - call.connectedAtMs) / 1000
            statsProvider()?.let { st ->
                val total = st.sentBytes + st.receivedBytes
                kbps = (((total - lastBytes) * 8) / 1000).toInt()
                lastBytes = total
                val frames = st.packetsPlayed + st.packetsConcealed
                concealPct = if (frames > 0) ((st.packetsConcealed * 100) / frames).toInt() else 0
                micAvailable = st.micAvailable
            }
            delay(1000)
        }
    }

    val statusLine = when (call.phase) {
        CallPhase.OUTGOING_RINGING -> "Calling over radio..."
        CallPhase.INCOMING_RINGING -> "Incoming offline call"
        CallPhase.CONNECTED -> "%d:%02d".format(elapsedSec / 60, elapsedSec % 60) +
            if (micAvailable) "" else " • microphone unavailable"
        CallPhase.ENDED -> call.endReason ?: "Call ended"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VolcanicUmber)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main Viewfinder Frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 70.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                shape = RoundedCornerShape(24.dp),
                color = MilledGraphite,
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Photographic Viewfinder Corner Brackets
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bracketLen = 22f
                        val pad = 24f
                        val w = size.width
                        val h = size.height

                        // Top-Left
                        drawLine(SubduedAsh, Offset(pad, pad), Offset(pad + bracketLen, pad), 1.5f)
                        drawLine(SubduedAsh, Offset(pad, pad), Offset(pad, pad + bracketLen), 1.5f)

                        // Top-Right
                        drawLine(SubduedAsh, Offset(w - pad, pad), Offset(w - pad - bracketLen, pad), 1.5f)
                        drawLine(SubduedAsh, Offset(w - pad, pad), Offset(w - pad, pad + bracketLen), 1.5f)

                        // Bottom-Left
                        drawLine(SubduedAsh, Offset(pad, h - pad), Offset(pad + bracketLen, h - pad), 1.5f)
                        drawLine(SubduedAsh, Offset(pad, h - pad), Offset(pad, h - pad - bracketLen), 1.5f)

                        // Bottom-Right
                        drawLine(SubduedAsh, Offset(w - pad, h - pad), Offset(w - pad - bracketLen, h - pad), 1.5f)
                        drawLine(SubduedAsh, Offset(w - pad, h - pad), Offset(w - pad, h - pad - bracketLen), 1.5f)
                    }

                    if (remoteLive) {
                        GhostBandAvatar(face = remoteFace, modifier = Modifier.fillMaxSize().padding(28.dp))
                        Text(
                            text = "${call.peerAlias} • GhostBand ${GhostBandMeshCodec.FRAME_BYTES} B/frame",
                            color = MutedDust,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                        )
                    } else Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(ElevatedCharcoal)
                                .border(
                                    1.dp,
                                    if (call.phase == CallPhase.CONNECTED) DesertSage else KalahariTerracotta.copy(alpha = 0.6f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = call.peerAlias.take(1).uppercase(),
                                color = BoneWhite,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = call.peerAlias,
                            color = BoneWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = statusLine,
                            color = if (call.phase == CallPhase.ENDED) RawOchre else MutedDust,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Self view: your own tracked face, mirrored, so you can see what the other side receives
        if (cameraActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 84.dp, end = 36.dp)
                    .size(width = 96.dp, height = 124.dp),
                shape = RoundedCornerShape(14.dp),
                color = VolcanicUmber,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (localFace != null) DesertSage else HairlineStone)
            ) {
                if (localFace != null) {
                    GhostBandAvatar(face = localFace, mirror = true, modifier = Modifier.fillMaxSize().padding(6.dp))
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Finding your face", color = SubduedAsh, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Top telemetry: measured live, end-to-end encrypted
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 28.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryPill(
                text = if (call.phase == CallPhase.CONNECTED) "LIVE: $kbps kbps" else "OFFLINE RADIO CALL",
                dot = if (call.phase == CallPhase.CONNECTED) DesertSage else SubduedAsh
            )
            TelemetryPill(
                text = if (call.phase == CallPhase.CONNECTED) "CONCEALED: $concealPct%" else "E2E ENCRYPTED",
                dot = null
            )
        }

        // Bottom controls
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            color = MilledGraphite,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (call.phase) {
                    CallPhase.INCOMING_RINGING -> {
                        CallButton(Icons.Default.CallEnd, "Decline", KalahariTerracotta, onEnd)
                        CallButton(Icons.Default.Call, "Accept", DesertSage, onAccept)
                    }
                    CallPhase.ENDED -> {
                        CallButton(Icons.Default.ArrowBack, "Back to chat", ElevatedCharcoal, onClose)
                    }
                    else -> {
                        if (call.phase == CallPhase.CONNECTED) {
                            ToggleButton(
                                icon = if (call.videoOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = "GhostBand video",
                                active = call.videoOn,
                                activeColor = DesertSage,
                                onClick = requestVideoToggle
                            )
                        }
                        ToggleButton(
                            icon = if (call.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "Mute",
                            active = call.muted,
                            activeColor = RustMuted,
                            onClick = onToggleMute
                        )
                        ToggleButton(
                            icon = if (call.speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            label = "Speaker",
                            active = call.speakerOn,
                            activeColor = KalahariTerracotta,
                            onClick = onToggleSpeaker
                        )
                        CallButton(Icons.Default.CallEnd, "End call", KalahariTerracotta, onEnd)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryPill(text: String, dot: Color?) {
    Surface(
        color = ElevatedCharcoal,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dot != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dot)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = if (dot != null) BoneWhite else RawOchre,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = BoneWhite, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) activeColor.copy(alpha = 0.18f) else ElevatedCharcoal)
            .border(1.dp, if (active) activeColor else HairlineStone, RoundedCornerShape(12.dp))
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if (active) activeColor else MutedDust, modifier = Modifier.size(20.dp))
    }
}
