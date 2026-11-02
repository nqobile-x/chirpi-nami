package com.nqobile.chirpynami.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.core.crypto.ChirpyIdentity
import com.nqobile.chirpynami.ui.theme.*

/**
 * Self-Sovereign Identity & Privacy Sheet.
 *
 * Displays hardware keystore public key fingerprint, zero-knowledge controls,
 * and emergency local storage wipe button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityIdentitySheet(
    identity: ChirpyIdentity,
    onPurgeData: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPurgeConfirm by remember { mutableStateOf(false) }

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
            Text(
                text = "SELF-SOVEREIGN IDENTITY",
                color = BoneWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = "Cryptographic Keystore • No Phone Number Required",
                color = MutedDust,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Public Key Fingerprint Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ElevatedCharcoal,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PUBLIC KEY FINGERPRINT",
                            color = KalahariTerracotta,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(DesertSage)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "HARDWARE SECURE",
                                color = DesertSage,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = identity.publicKeyHex.chunked(4).take(8).joinToString(" "),
                        color = BoneWhite,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // POPIA & GDPR Privacy Guarantee
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
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield",
                        tint = DesertSage,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "POPIA & GDPR Compliant by Design",
                            color = BoneWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Zero central databases. All messages, voice notes, and keys live strictly on this physical phone.",
                            color = MutedDust,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Emergency Local Data Purge Button
            if (!showPurgeConfirm) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showPurgeConfirm = true },
                    color = ElevatedCharcoal,
                    border = androidx.compose.foundation.BorderStroke(1.dp, RustMuted.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Purge",
                            tint = RustMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wipe All Local Chats Instantly",
                            color = RustMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showPurgeConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ElevatedCharcoal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = BoneWhite, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onPurgeData()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RustMuted),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm Wipe", color = BoneWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
