package com.nqobile.chirpynami.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nqobile.chirpynami.core.crdt.ChirpyPayloadKind
import com.nqobile.chirpynami.core.mesh.ChirpyMeshController
import com.nqobile.chirpynami.core.mesh.ChirpyMeshPeer
import com.nqobile.chirpynami.ui.screens.ChirpyCallScreen
import com.nqobile.chirpynami.ui.screens.ChirpyChatScreen
import com.nqobile.chirpynami.ui.screens.ChirpyRadarScreen
import com.nqobile.chirpynami.ui.screens.ChirpySplashScreen
import com.nqobile.chirpynami.ui.theme.ChirpyColorScheme
import com.nqobile.chirpynami.ui.theme.VolcanicUmber

sealed interface ChirpyNavScreen {
    data object Splash : ChirpyNavScreen
    data object Radar : ChirpyNavScreen
    data class Chat(val peer: ChirpyMeshPeer) : ChirpyNavScreen
}

class ChirpyAppActivity : ComponentActivity() {

    private lateinit var mesh: ChirpyMeshController

    private val radioPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()

    // Radios start once the user has answered; Nearby reports its own failure if a grant is missing.
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { mesh.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mesh = ChirpyMeshController.get(this)
        permissionRequest.launch(radioPermissions)

        setContent {
            MaterialTheme(colorScheme = ChirpyColorScheme) {
                var currentScreen by remember { mutableStateOf<ChirpyNavScreen>(ChirpyNavScreen.Splash) }
                val peers by mesh.meshEngine.discoveredPeers.collectAsState()
                val timeline by mesh.messages.collectAsState()
                val relayEnabled by mesh.relayEnabled.collectAsState()
                val relayOnline by mesh.relayOnline.collectAsState()
                val activeCall by mesh.call.collectAsState()
                val remoteFace by mesh.remoteFace.collectAsState()
                val localFace by mesh.localFace.collectAsState()

                // Read receipts follow whichever conversation is on screen.
                val openPeerId = (currentScreen as? ChirpyNavScreen.Chat)?.peer?.peerIdHex
                LaunchedEffect(openPeerId, timeline.size) { mesh.openChat(openPeerId) }

                // System Back gesture routing
                // While a call is up, Back only dismisses a finished call; it never drops a live one.
                androidx.activity.compose.BackHandler(enabled = activeCall != null) {
                    if (activeCall?.phase == ChirpyMeshController.CallPhase.ENDED) mesh.dismissCall()
                }

                androidx.activity.compose.BackHandler(enabled = activeCall == null && currentScreen !is ChirpyNavScreen.Radar && currentScreen !is ChirpyNavScreen.Splash) {
                    when (val s = currentScreen) {
                        is ChirpyNavScreen.Chat -> currentScreen = ChirpyNavScreen.Radar
                        else -> {}
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VolcanicUmber
                ) {
                    val call = activeCall
                    if (call != null) {
                        ChirpyCallScreen(
                            call = call,
                            statsProvider = mesh::callStats,
                            onAccept = mesh::acceptCall,
                            onEnd = { mesh.endCall() },
                            onToggleMute = { mesh.setMuted(!call.muted) },
                            onToggleSpeaker = { mesh.setSpeaker(!call.speakerOn) },
                            onClose = mesh::dismissCall,
                            remoteFace = remoteFace,
                            localFace = localFace,
                            onToggleVideo = { mesh.setVideo(!call.videoOn) },
                            onCameraBound = mesh::startFaceCapture,
                            onCameraReleased = mesh::stopFaceCapture
                        )
                    } else when (val screen = currentScreen) {
                        is ChirpyNavScreen.Splash -> {
                            ChirpySplashScreen(
                                onSplashFinished = { currentScreen = ChirpyNavScreen.Radar }
                            )
                        }
                        is ChirpyNavScreen.Radar -> {
                            ChirpyRadarScreen(
                                peers = peers,
                                recentContacts = mesh.recentContacts(timeline),
                                unreadCount = { mesh.unreadCount(timeline, it) },
                                relayEnabled = relayEnabled,
                                relayOnline = relayOnline,
                                onToggleRelay = { mesh.setRelayEnabled(!relayEnabled) },
                                onSelectPeer = { currentScreen = ChirpyNavScreen.Chat(it) },
                                onCallPeer = { mesh.startCall(it.peerIdHex, it.alias) }
                            )
                        }
                        is ChirpyNavScreen.Chat -> {
                            val peerId = screen.peer.peerIdHex
                            // Prefer the live peer (alias / radio may have changed since navigation).
                            val peer = peers.firstOrNull { it.peerIdHex == peerId } ?: screen.peer
                            ChirpyChatScreen(
                                peer = peer,
                                messages = remember(timeline, peerId) { mesh.conversationWith(peerId) },
                                isLinked = peers.any { it.peerIdHex == peerId },
                                relayOnline = relayOnline,
                                displayText = mesh::displayText,
                                onSendMessage = { text -> mesh.send(peerId, ChirpyPayloadKind.TEXT, text) },
                                onSendSticker = { sticker -> mesh.send(peerId, ChirpyPayloadKind.STICKER, sticker.emoji) },
                                onSendVoiceNote = { duration ->
                                    mesh.send(peerId, ChirpyPayloadKind.VOICE_NOTE, "Voice chirp (${duration}s)")
                                },
                                onStartCall = { mesh.startCall(peer.peerIdHex, peer.alias) },
                                onBack = { currentScreen = ChirpyNavScreen.Radar }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) mesh.stop()
    }
}
