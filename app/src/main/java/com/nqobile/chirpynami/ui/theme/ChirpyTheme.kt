package com.nqobile.chirpynami.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Chirpy-NAMi Tactile Acoustic Palette.
 *
 * Inspired by Southern African geology, warm sandveld, terracotta clay, and precision analogue audio gear.
 * Strictly zero neon, zero glowing cyber-cyan, zero generic AI tropes.
 */

// Foundations: Earth, Stone & Volcanic Umber
val VolcanicUmber = Color(0xFF141312)       // Natural warm dark stone
val MilledGraphite = Color(0xFF1C1A18)      // Ceramic/graphite surface
val ElevatedCharcoal = Color(0xFF24211E)    // Raised card / container
val HairlineStone = Color(0xFF2E2A25)       // Subtle tactile border divider

// Human & Organic Earth Accents
val KalahariTerracotta = Color(0xFFC8532F)  // Warm clay red (primary accent)
val RawOchre = Color(0xFFD48B28)            // Sandveld warm ochre (secondary accent)
val DesertSage = Color(0xFF4A7C59)          // Quiet acacia green (connected / verified)
val RustMuted = Color(0xFF9E4738)           // Offline / error state

// Typography: Bone, Parchment & Dust
val BoneWhite = Color(0xFFF5EFEB)           // High-contrast warm text
val MutedDust = Color(0xFFA1998E)           // Secondary typography / telemetry
val SubduedAsh = Color(0xFF6E6860)          // Tertiary labels / hairpins

val ChirpyColorScheme: ColorScheme = darkColorScheme(
    primary = KalahariTerracotta,
    onPrimary = BoneWhite,
    secondary = RawOchre,
    onSecondary = VolcanicUmber,
    tertiary = DesertSage,
    background = VolcanicUmber,
    onBackground = BoneWhite,
    surface = MilledGraphite,
    onSurface = BoneWhite,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = MutedDust,
    error = RustMuted,
    onError = BoneWhite
)
