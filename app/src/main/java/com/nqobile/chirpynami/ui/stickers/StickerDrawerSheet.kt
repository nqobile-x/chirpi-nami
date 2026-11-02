package com.nqobile.chirpynami.ui.stickers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.ui.theme.*

/**
 * Tactile Offline Sticker Drawer.
 *
 * Finished in warm graphite, stone dividers, and muted terracotta accents.
 * 100% offline, zero mobile data needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerDrawerSheet(
    onStickerPicked: (ChirpySticker) -> Unit,
    onDismiss: () -> Unit
) {
    var activeCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Slang", "Expressive", "Commute", "Daily Life")

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Street Expressions",
                    color = BoneWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "0 MB Data • P2P Cached",
                    color = DesertSage,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = activeCategory == cat
                    Surface(
                        color = if (isSelected) KalahariTerracotta else ElevatedCharcoal,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) KalahariTerracotta else HairlineStone
                        ),
                        modifier = Modifier.clickable { activeCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) BoneWhite else MutedDust,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val itemsToDisplay = if (activeCategory == "All") {
                ChirpyStickerCatalog.sadcStreetPack
            } else {
                ChirpyStickerCatalog.sadcStreetPack.filter { it.category == activeCategory }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                items(itemsToDisplay) { sticker ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onStickerPicked(sticker)
                                onDismiss()
                            },
                        color = ElevatedCharcoal,
                        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = sticker.emoji, fontSize = 26.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = sticker.title,
                                color = BoneWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
