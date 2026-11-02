package com.nqobile.chirpynami.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nqobile.chirpynami.ui.components.ChirpyMotionLogo
import com.nqobile.chirpynami.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Chirpy-NAMi Motion Graphics Splash & Reveal Screen.
 *
 * Provides a cinematic, tactile brand introduction with elastic spring physics,
 * soundwave pulsation, and security sovereignty credentials.
 */
@Composable
fun ChirpySplashScreen(
    onSplashFinished: () -> Unit,
    autoDismissMs: Long = 2800L
) {
    val textAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val securityBadgeAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(400)
        textAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(200)
        subtitleAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(300)
        securityBadgeAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(autoDismissMs)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VolcanicUmber),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // The Motion Graphics Acoustic Swift Logo
            ChirpyMotionLogo(
                size = 180.dp,
                animateOnLaunch = true,
                onTap = onSplashFinished
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Title Reveal
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    text = "Chirpi",
                    color = BoneWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = " Nami",
                    color = KalahariTerracotta,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle Reveal
            Text(
                text = "Zero Data • Peer-to-Peer Acoustic Mesh",
                color = MutedDust,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Security & Sovereignty Pillar Badge
            Surface(
                color = ElevatedCharcoal,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HairlineStone),
                modifier = Modifier.alpha(securityBadgeAlpha.value)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(DesertSage, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SELF-SOVEREIGN • SIGNAL DOUBLE RATCHET",
                        color = BoneWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        // Tap to Enter Hint
        Text(
            text = "TAP ANYWHERE TO ENTER",
            color = SubduedAsh,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .alpha(securityBadgeAlpha.value)
        )
    }
}
