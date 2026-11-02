package com.nqobile.chirpynami.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.core.mesh.ChirpyMeshPeer
import com.nqobile.chirpynami.core.mesh.ChirpyRadioKind
import com.nqobile.chirpynami.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactile Acoustic Compass & Peer Field.
 *
 * Designed like precision-milled analogue radio instrumentation.
 * Replaces generic glowing sci-fi radars with warm stone dials, mechanical compass ticks,
 * and bespoke typography.
 */
@Composable
fun ChirpyRadarScreen(
    peers: List<ChirpyMeshPeer>,
    recentContacts: List<ChirpyMeshPeer> = emptyList(),
    unreadCount: (String) -> Int = { 0 },
    relayEnabled: Boolean = false,
    relayOnline: Boolean = false,
    onToggleRelay: () -> Unit = {},
    onSelectPeer: (ChirpyMeshPeer) -> Unit,
    onCallPeer: (ChirpyMeshPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CompassNeedleTransition")
    val needleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "NeedleSweep"
    )

    val linkedIds = peers.map { it.peerIdHex }.toSet()
    val listedPeers = peers + recentContacts.filter { it.peerIdHex !in linkedIds }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolcanicUmber)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // App Header: Editorial & Architectural
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Chirpi",
                        color = BoneWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = " Nami",
                        color = KalahariTerracotta,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Acoustic Radio Mesh • Direct P2P",
                    color = MutedDust,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Tactile Status Pill
            Surface(
                color = ElevatedCharcoal,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(DesertSage)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${peers.size} IN FIELD",
                        color = BoneWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Long-range internet relay switch (radio stays zero-data either way)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggleRelay),
            color = MilledGraphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !relayEnabled -> SubduedAsh
                                relayOnline -> DesertSage
                                else -> RawOchre
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !relayEnabled -> "Internet relay off • radio only, zero data"
                        relayOnline -> "Internet relay on • reach contacts anywhere"
                        else -> "Internet relay connecting..."
                    },
                    color = BoneWhite,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (relayEnabled) "ON" else "OFF",
                    color = if (relayEnabled) KalahariTerracotta else MutedDust,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Precision Mechanical Radio Compass Dial
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MilledGraphite)
                .border(1.dp, HairlineStone, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minOf(size.width, size.height) * 0.42f

                // Outer bezel ring
                drawCircle(
                    color = HairlineStone,
                    radius = maxRadius,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // 24 Radial Tick Marks (Analogue dial aesthetic)
                for (i in 0 until 24) {
                    val angle = Math.toRadians((i * 15).toDouble())
                    val isCardinal = i % 6 == 0
                    val tickLen = if (isCardinal) 12f else 6f
                    val outerX = (center.x + maxRadius * cos(angle)).toFloat()
                    val outerY = (center.y + maxRadius * sin(angle)).toFloat()
                    val innerX = (center.x + (maxRadius - tickLen) * cos(angle)).toFloat()
                    val innerY = (center.y + (maxRadius - tickLen) * sin(angle)).toFloat()

                    drawLine(
                        color = if (isCardinal) KalahariTerracotta else SubduedAsh,
                        start = Offset(innerX, innerY),
                        end = Offset(outerX, outerY),
                        strokeWidth = if (isCardinal) 2f else 1f
                    )
                }

                // Range circles: 50m and 100m (dashed tactile lines)
                val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawCircle(
                    color = HairlineStone,
                    radius = maxRadius * 0.45f,
                    center = center,
                    style = Stroke(width = 1f, pathEffect = dashed)
                )
                drawCircle(
                    color = HairlineStone,
                    radius = maxRadius * 0.75f,
                    center = center,
                    style = Stroke(width = 1f, pathEffect = dashed)
                )

                // Subtle sweeping hairline needle (terracotta thread)
                val needleRad = Math.toRadians(needleAngle.toDouble())
                val needleEnd = Offset(
                    x = (center.x + (maxRadius - 14f) * cos(needleRad)).toFloat(),
                    y = (center.y + (maxRadius - 14f) * sin(needleRad)).toFloat()
                )
                drawLine(
                    color = KalahariTerracotta.copy(alpha = 0.5f),
                    start = center,
                    end = needleEnd,
                    strokeWidth = 1.5f
                )

                // Center Origin Pip
                drawCircle(color = KalahariTerracotta, radius = 5f, center = center)
                drawCircle(color = BoneWhite, radius = 2f, center = center)

                // Discovered Peer Pips (tactile bone and terracotta pins)
                peers.forEachIndexed { index, peer ->
                    val angleOffset = (index * (360f / maxOf(peers.size, 1))) + 40f
                    val rad = Math.toRadians(angleOffset.toDouble())
                    val normalizedDist = (peer.distanceMeters / 150.0).coerceIn(0.28, 0.88).toFloat()
                    val pipPos = Offset(
                        x = (center.x + maxRadius * normalizedDist * cos(rad)).toFloat(),
                        y = (center.y + maxRadius * normalizedDist * sin(rad)).toFloat()
                    )

                    // Outer halo ring
                    drawCircle(color = HairlineStone, radius = 9f, center = pipPos, style = Stroke(1.5f))
                    // Inner solid pip
                    drawCircle(color = BoneWhite, radius = 4f, center = pipPos)
                }
            }

            // Dial Footnote Labels
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FREQ: 2.4 GHz P2P",
                    color = SubduedAsh,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (relayEnabled) "RADIO + RELAY" else "0 DATA USAGE",
                    color = DesertSage,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Peer List Header
        Text(
            text = "PEERS & CHIRPS",
            color = MutedDust,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (listedPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Scanning radio spectrum...",
                        color = BoneWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Zero data needed • Radio beacon active",
                        color = DesertSage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bring another phone within 100m to chirp",
                        color = SubduedAsh,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            // Tactile Peer Cards
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            items(listedPeers, key = { it.peerIdHex }) { peer ->
                val isLinked = peer.peerIdHex in linkedIds
                val unread = unreadCount(peer.peerIdHex)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectPeer(peer) },
                    color = MilledGraphite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Monogram Stamp
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElevatedCharcoal)
                                    .border(1.dp, HairlineStone, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.alias.take(1).uppercase(),
                                    color = BoneWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = peer.alias,
                                    color = BoneWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        isLinked -> "Linked • ${peer.radioKind.label}"
                                        relayOnline -> "Via internet relay"
                                        else -> "Out of range • chirps queue"
                                    },
                                    color = if (isLinked || relayOnline) DesertSage else SubduedAsh,
                                    fontSize = 12.sp
                                )
                                if (unread > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$unread new chirp${if (unread == 1) "" else "s"}",
                                        color = RawOchre,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { onCallPeer(peer) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElevatedCharcoal)
                                .border(1.dp, HairlineStone, RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Voice Call",
                                tint = KalahariTerracotta,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
