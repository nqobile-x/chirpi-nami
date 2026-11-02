package com.nqobile.chirpynami.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.crdt.ChirpyMessage
import com.nqobile.chirpynami.core.crdt.ChirpyPayloadKind
import com.nqobile.chirpynami.core.mesh.ChirpyMeshPeer
import com.nqobile.chirpynami.ui.components.ChirpyVoicePlayer
import com.nqobile.chirpynami.ui.components.ChirpyVoiceRecorderButton
import com.nqobile.chirpynami.ui.components.MeshHopInspectorSheet
import com.nqobile.chirpynami.ui.stickers.ChirpySticker
import com.nqobile.chirpynami.ui.stickers.StickerDrawerSheet
import com.nqobile.chirpynami.ui.theme.*

/**
 * Editorial Acoustic Chat Screen.
 *
 * Finished in warm volcanic umber, tactile graphite surfaces, and stone dividers.
 * Features 1.6 kbps neural audio voice player, hold-to-chirp recorder,
 * SADC street expression drawer, and interactive sneakernet hop inspector.
 */
@Composable
fun ChirpyChatScreen(
    peer: ChirpyMeshPeer,
    messages: List<ChirpyMessage>,
    isLinked: Boolean,
    relayOnline: Boolean = false,
    displayText: (ChirpyMessage) -> String,
    onSendMessage: (String) -> Unit,
    onSendSticker: (ChirpySticker) -> Unit,
    onSendVoiceNote: (Int) -> Unit = {},
    onStartCall: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftText by remember { mutableStateOf("") }
    var showStickers by remember { mutableStateOf(false) }
    var inspectedMessage by remember { mutableStateOf<ChirpyMessage?>(null) }

    // SADC Sticker Drawer
    if (showStickers) {
        StickerDrawerSheet(
            onStickerPicked = { sticker ->
                onSendSticker(sticker)
                showStickers = false
            },
            onDismiss = { showStickers = false }
        )
    }

    // Physical Mesh Hop Inspector Sheet
    inspectedMessage?.let { msg ->
        MeshHopInspectorSheet(
            message = msg,
            onDismiss = { inspectedMessage = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VolcanicUmber)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Chat Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MilledGraphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = BoneWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElevatedCharcoal)
                            .border(1.dp, HairlineStone, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = peer.alias.take(1).uppercase(),
                            color = BoneWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = peer.alias,
                            color = BoneWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isLinked || relayOnline) DesertSage else SubduedAsh)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    isLinked -> "Linked • ${peer.radioKind.label}"
                                    relayOnline -> "Via internet relay • delivers when they're online"
                                    else -> "Out of range • chirps will queue"
                                },
                                color = MutedDust,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Call Action Button
                IconButton(
                    onClick = onStartCall,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElevatedCharcoal)
                        .border(1.dp, HairlineStone, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Acoustic Call",
                        tint = KalahariTerracotta,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Message Stream
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                val isMine = msg.senderId != peer.peerIdHex
                val alignment = if (isMine) Alignment.End else Alignment.Start
                val bubbleBg = if (isMine) ElevatedCharcoal else MilledGraphite
                val borderCol = if (isMine) KalahariTerracotta.copy(alpha = 0.4f) else HairlineStone

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = alignment
                ) {
                    Surface(
                        color = bubbleBg,
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMine) 14.dp else 2.dp,
                            bottomEnd = if (isMine) 2.dp else 14.dp
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                        modifier = Modifier.widthIn(max = 290.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            when (msg.payloadKind) {
                                ChirpyPayloadKind.VOICE_NOTE -> {
                                    ChirpyVoicePlayer(
                                        durationSeconds = Regex("""\d+""").find(displayText(msg))?.value?.toIntOrNull() ?: 0,
                                        isMine = isMine
                                    )
                                }
                                ChirpyPayloadKind.STICKER -> {
                                    Text(
                                        text = displayText(msg),
                                        fontSize = 32.sp
                                    )
                                }
                                else -> {
                                    Text(
                                        text = displayText(msg),
                                        color = BoneWhite,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Clickable Physical Mesh Hop badge
                                if (msg.meshHops > 0) {
                                    Text(
                                        text = "${msg.meshHops} hops • ",
                                        color = RawOchre,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { inspectedMessage = msg }
                                    )
                                }

                                Text(
                                    text = when (msg.status) {
                                        ChirpyDeliveryStatus.OUTBOX -> "Queued"
                                        ChirpyDeliveryStatus.IN_FLIGHT_MESH -> "In transit"
                                        ChirpyDeliveryStatus.DELIVERED -> "Delivered"
                                        ChirpyDeliveryStatus.READ -> "Read"
                                    },
                                    color = when (msg.status) {
                                        ChirpyDeliveryStatus.READ -> DesertSage
                                        ChirpyDeliveryStatus.DELIVERED -> MutedDust
                                        else -> SubduedAsh
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tactile Input Dock
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MilledGraphite,
            border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SADC Stickers Drawer Toggle
                IconButton(
                    onClick = { showStickers = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "SADC Stickers",
                        tint = MutedDust,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Text Input Field
                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    placeholder = {
                        Text(
                            text = "Zero-data encrypted chirp...",
                            color = SubduedAsh,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ElevatedCharcoal,
                        unfocusedContainerColor = ElevatedCharcoal,
                        focusedBorderColor = KalahariTerracotta,
                        unfocusedBorderColor = HairlineStone,
                        focusedTextColor = BoneWhite,
                        unfocusedTextColor = BoneWhite
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(6.dp))

                // If text is present, show Send button; otherwise show Voice Recorder button!
                if (draftText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onSendMessage(draftText)
                            draftText = ""
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KalahariTerracotta)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = BoneWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Tactile Voice Note Chirp Recorder
                    ChirpyVoiceRecorderButton(
                        onRecordComplete = { duration ->
                            onSendVoiceNote(duration)
                        }
                    )
                }
            }
        }
    }
}
