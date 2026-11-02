package com.nqobile.chirpynami.ui.stickers

import java.io.Serializable

data class ChirpySticker(
    val id: String,
    val title: String,
    val emoji: String,
    val category: String,
    val isAnimated: Boolean = true
) : Serializable

object ChirpyStickerCatalog {
    // SADC Expressive & Street Culture Pre-Bundled Offline Pack (0 MB Data Needed)
    val sadcStreetPack = listOf(
        ChirpySticker("stk_1", "Ke Sharp!", "🤙", "Slang"),
        ChirpySticker("stk_2", "Eish!", "🤦‍♂️", "Expressive"),
        ChirpySticker("stk_3", "Yoh!", "🤯", "Expressive"),
        ChirpySticker("stk_4", "Hayi Bo!", "🙅‍♀️", "Expressive"),
        ChirpySticker("stk_5", "Dumela Rre/Mme", "🤝", "Greetings"),
        ChirpySticker("stk_6", "Sho My Chom", "👊", "Slang"),
        ChirpySticker("stk_7", "Amapiano Groove", "🕺", "Groove"),
        ChirpySticker("stk_8", "Taxi Sign: Local", "☝️", "Commute"),
        ChirpySticker("stk_9", "Taxi Sign: Town", "👉", "Commute"),
        ChirpySticker("stk_10", "Lekker Vibe", "🔥", "Slang"),
        ChirpySticker("stk_11", "Loadshedding Candle", "🕯️", "Daily Life"),
        ChirpySticker("stk_12", "Sunday Braai", "🥩", "Daily Life")
    )

    val reactionEmojis = listOf("🔥", "❤️", "😂", "🤙", "👏", "🤯", "🕯️")
}
