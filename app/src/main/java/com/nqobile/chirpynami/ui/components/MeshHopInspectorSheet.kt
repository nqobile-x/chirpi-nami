package com.nqobile.chirpynami.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.core.crdt.ChirpyMessage
import com.nqobile.chirpynami.ui.theme.*

/**
 * Physical SADC Mesh Hop Inspector Sheet.
 *
 * Visualizes the delay-tolerant sneakernet journey taken by an encrypted message
 * across minibus taxis, border depots, and peer devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshHopInspectorSheet(
    message: ChirpyMessage,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MilledGraphite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SubduedAsh)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PHYSICAL MESH TRAIL",
                    color = BoneWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Surface(
                    color = ElevatedCharcoal,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
                ) {
                    Text(
                        text = "${message.meshHops} TRANSIT HOPS",
                        color = RawOchre,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Origin
            HopTimelineRow(
                step = "1",
                title = "Created on Origin Device",
                location = "Johannesburg (Gauteng)",
                transport = "Local Keystore Encryption",
                isFirst = true
            )

            // Step 2: Intermediate Courier
            if (message.meshHops > 1) {
                HopTimelineRow(
                    step = "2",
                    title = "Carried via Cross-Border Minibus Taxi",
                    location = "N1 Trans-Kalahari Corridor",
                    transport = "Bluetooth LE Mesh Sync (128-byte Bloom filter)",
                    isFirst = false
                )
            }

            // Step 3: Destination
            HopTimelineRow(
                step = if (message.meshHops > 1) "3" else "2",
                title = "Delivered to Recipient",
                location = "Gaborone (Botswana)",
                transport = "Wi-Fi Direct P2P Ingestion",
                isLast = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Cryptographic Integrity Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ElevatedCharcoal,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Zero Knowledge",
                        tint = DesertSage,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% Zero-Knowledge: Intermediate courier taxis carried only sealed cryptographic ciphertext. No personal data was visible.",
                        color = MutedDust,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HopTimelineRow(
    step: String,
    title: String,
    location: String,
    transport: String,
    isFirst: Boolean = false,
    isLast: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isLast) DesertSage else ElevatedCharcoal)
                    .border(1.dp, if (isLast) DesertSage else KalahariTerracotta, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step,
                    color = BoneWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(38.dp)
                        .background(HairlineStone)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 12.dp)) {
            Text(
                text = title,
                color = BoneWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$location • $transport",
                color = MutedDust,
                fontSize = 11.sp
            )
        }
    }
}
