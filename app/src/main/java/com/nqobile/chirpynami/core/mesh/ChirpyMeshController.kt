package com.nqobile.chirpynami.core.mesh

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.nqobile.chirpynami.core.crdt.ChirpyCrdtLog
import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.crdt.ChirpyMessage
import com.nqobile.chirpynami.core.crdt.ChirpyPayloadKind
import com.nqobile.chirpynami.core.crypto.ChirpyIdentity
import com.nqobile.chirpynami.core.crypto.DoubleRatchetCipher
import com.nqobile.chirpynami.ai.video.GhostBandLandmarks
import com.nqobile.chirpynami.ai.video.GhostBandMeshCodec
import com.nqobile.chirpynami.core.call.ChirpyAudioEngine
import com.nqobile.chirpynami.core.call.ChirpyFaceCapture
import com.nqobile.chirpynami.core.relay.ChirpyNostrRelay
import com.nqobile.chirpynami.core.relay.Secp256k1Schnorr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Wires identity, the CRDT timeline and the radio transports into one live mesh node.
 *
 * - Messages are AES-GCM encrypted with an ECDH secret between the two identity keys and
 *   signed by the sender; tampered or forged frames are dropped.
 * - Anything sent while a peer is out of range stays in the outbox and is re-sent
 *   (store-and-forward) the next time a link to that peer comes up.
 * - The timeline is persisted to app storage so chats survive restarts.
 */
class ChirpyMeshController private constructor(private val context: Context) :
    ChirpyTransportListener, ChirpyNostrRelay.Listener {

    companion object {
        private const val TAG = "ChirpyMesh"
        private const val PREFS = "chirpy_identity"
        private const val CONTACTS = "chirpy_contacts"
        private const val TIMELINE_FILE = "timeline.bin"

        @Volatile private var instance: ChirpyMeshController? = null

        /** One mesh node per process, so activity recreation never double-binds the radios. */
        fun get(context: Context): ChirpyMeshController = instance ?: synchronized(this) {
            instance ?: ChirpyMeshController(context.applicationContext).also { instance = it }
        }
    }

    val identity: ChirpyIdentity = loadOrCreateIdentity()
    val localNodeId: String get() = identity.publicKeyHex

    private val crdtLog = ChirpyCrdtLog(identity.publicKeyHex)
    val meshEngine = ChirpyRadioMeshEngine(
        localNodeId = identity.publicKeyHex,
        localAlias = identity.alias,
        deviceApiLevel = Build.VERSION.SDK_INT
    )

    private val _messages = MutableStateFlow<List<ChirpyMessage>>(emptyList())
    val messages: StateFlow<List<ChirpyMessage>> = _messages.asStateFlow()

    private val links = ConcurrentHashMap<String, (ByteArray) -> Unit>()
    private val plaintextCache = ConcurrentHashMap<String, String>()
    private val secrets = ConcurrentHashMap<String, ByteArray>()
    private val random = SecureRandom()

    @Volatile private var openChatPeerId: String? = null

    private var nearby: ChirpyNearbyTransport? = null
    private val socketBridge = ChirpySocketTransport(identity.publicKeyHex, identity.alias, this)

    private val identityPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var relay: ChirpyNostrRelay? = null

    /** Internet relay switch: on by default, persisted. Off = pure radio, zero data. */
    private val _relayEnabled = MutableStateFlow(identityPrefs.getBoolean("relay_enabled", true))
    val relayEnabled: StateFlow<Boolean> = _relayEnabled.asStateFlow()
    private val _relayOnline = MutableStateFlow(false)
    val relayOnline: StateFlow<Boolean> = _relayOnline.asStateFlow()

    init {
        loadTimeline()
    }

    /** Starts the radios. Call once runtime permissions have been answered. */
    @Synchronized
    fun start() {
        if (meshEngine.isMeshActive) return
        meshEngine.startMesh()
        socketBridge.start()
        try {
            nearby = ChirpyNearbyTransport(context, identity.publicKeyHex, identity.alias, this).also { it.start() }
        } catch (e: Exception) {
            Log.w(TAG, "Nearby Connections unavailable", e)
        }
        if (_relayEnabled.value) startRelay()
    }

    @Synchronized
    fun setRelayEnabled(enabled: Boolean) {
        _relayEnabled.value = enabled
        identityPrefs.edit().putBoolean("relay_enabled", enabled).apply()
        if (enabled && meshEngine.isMeshActive) startRelay() else if (!enabled) stopRelay()
    }

    private fun startRelay() {
        if (relay != null) return
        val sinceDefault = System.currentTimeMillis() / 1000 - 7 * 24 * 3600
        relay = ChirpyNostrRelay(
            localNodeId = identity.publicKeyHex,
            sealAlias = { peerId -> seal(peerId, identity.alias.toByteArray()) },
            openAlias = { peerId, sealed -> runCatching { String(open(peerId, sealed)) }.getOrNull() },
            secretKey = relaySecretKey(),
            initialSince = identityPrefs.getLong("relay_since", sinceDefault),
            onSinceAdvanced = { identityPrefs.edit().putLong("relay_since", it).apply() },
            listener = this
        ).also { it.start() }
    }

    private fun stopRelay() {
        relay?.stop()
        relay = null
        _relayOnline.value = false
    }

    private fun relaySecretKey(): ByteArray {
        identityPrefs.getString("relay_sk", null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val sk = Secp256k1Schnorr.newSecretKey(random)
        identityPrefs.edit().putString("relay_sk", Base64.encodeToString(sk, Base64.NO_WRAP)).apply()
        return sk
    }

    /** Radio link first (free), then the internet relay; null when the peer is unreachable right now. */
    private fun routeTo(peerId: String): ((ByteArray) -> Unit)? =
        links[peerId] ?: relay?.takeIf { it.online.value }?.let { r -> { bytes: ByteArray -> r.publish(peerId, bytes) } }

    fun isRelayReachable(): Boolean = relay?.online?.value == true

    // --- Live calls (direct radio / bridge link only) -----------------------------------------

    enum class CallPhase { OUTGOING_RINGING, INCOMING_RINGING, CONNECTED, ENDED }

    data class CallState(
        val peerId: String,
        val peerAlias: String,
        val callId: Long,
        val phase: CallPhase,
        val endReason: String? = null,
        val connectedAtMs: Long = 0L,
        val muted: Boolean = false,
        val speakerOn: Boolean = true,
        val videoOn: Boolean = false
    )

    private val _call = MutableStateFlow<CallState?>(null)
    val call: StateFlow<CallState?> = _call.asStateFlow()
    private var audio: ChirpyAudioEngine? = null
    private var mediaSeq = 0
    private val callTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { Thread(it, "chirpy-call-timer").apply { isDaemon = true } }

    // GhostBand video: geometry only, ~28 kbps each way at 12 fps.
    private val _remoteFace = MutableStateFlow<GhostBandLandmarks?>(null)
    val remoteFace: StateFlow<GhostBandLandmarks?> = _remoteFace.asStateFlow()
    private val _localFace = MutableStateFlow<GhostBandLandmarks?>(null)
    val localFace: StateFlow<GhostBandLandmarks?> = _localFace.asStateFlow()
    private var faceCapture: ChirpyFaceCapture? = null
    private var faceSeq = 0
    private val videoSent = java.util.concurrent.atomic.AtomicLong()
    private val videoReceived = java.util.concurrent.atomic.AtomicLong()

    fun callStats(): ChirpyAudioEngine.Stats? = audio?.stats()?.let {
        it.copy(sentBytes = it.sentBytes + videoSent.get(), receivedBytes = it.receivedBytes + videoReceived.get())
    }

    fun setVideo(on: Boolean) {
        _call.value = _call.value?.copy(videoOn = on)
        if (!on) {
            faceCapture?.stop()
            _localFace.value = null
        }
    }

    /** Binds the camera to the on-screen call; the UI owns the lifecycle so the camera stops with it. */
    fun startFaceCapture(owner: androidx.lifecycle.LifecycleOwner) {
        val c = _call.value ?: return
        if (c.phase != CallPhase.CONNECTED || !c.videoOn) return
        val capture = faceCapture ?: ChirpyFaceCapture(context) { local, packet ->
            _localFace.value = local
            sendFace(packet)
        }.also { faceCapture = it }
        capture.start(owner)
    }

    fun stopFaceCapture() {
        faceCapture?.stop()
        _localFace.value = null
    }

    private fun sendFace(packet: ByteArray) {
        val c = _call.value ?: return
        if (c.phase != CallPhase.CONNECTED || !c.videoOn) return
        val seq = faceSeq++
        val sealed = cipherFor(c.peerId).encryptAt(packet, faceCounter(c.callId, seq))
        videoSent.addAndGet(sealed.size.toLong())
        links[c.peerId]?.let { sendFrame(it, ChirpyWireFrame.Media(c.callId, seq, sealed, ChirpyWireFrame.Media.KIND_FACE)) }
    }

    /** Face frames use a separate key-counter space from voice packets. */
    private fun faceCounter(callId: Long, seq: Int) = callId xor ((1L shl 40) or seq.toLong())

    @Synchronized
    fun startCall(peerId: String, alias: String) {
        if (_call.value?.phase.let { it != null && it != CallPhase.ENDED }) return
        val link = links[peerId]
        val callId = random.nextLong()
        if (link == null) {
            _call.value = CallState(peerId, alias, callId, CallPhase.ENDED,
                endReason = "Not in radio range. Live calls need a direct link (the relay only carries chirps).")
            return
        }
        _call.value = CallState(peerId, alias, callId, CallPhase.OUTGOING_RINGING)
        sendFrame(link, ChirpyWireFrame.Call(callId, ChirpyCallAction.INVITE))
        callTimer.schedule({
            val c = _call.value
            if (c?.callId == callId && c.phase == CallPhase.OUTGOING_RINGING) endCall("No answer")
        }, 30, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Synchronized
    fun acceptCall() {
        val c = _call.value ?: return
        if (c.phase != CallPhase.INCOMING_RINGING) return
        val link = links[c.peerId] ?: return endCall("Link lost")
        sendFrame(link, ChirpyWireFrame.Call(c.callId, ChirpyCallAction.ACCEPT))
        connectMedia(c)
    }

    /** Hang up, decline, or cancel — whichever applies to the current phase. */
    @Synchronized
    fun endCall(reason: String = "Call ended") {
        val c = _call.value ?: return
        if (c.phase == CallPhase.ENDED) return
        val action = if (c.phase == CallPhase.INCOMING_RINGING) ChirpyCallAction.DECLINE else ChirpyCallAction.END
        links[c.peerId]?.let { sendFrame(it, ChirpyWireFrame.Call(c.callId, action)) }
        finishCall(c, reason)
    }

    fun dismissCall() {
        if (_call.value?.phase == CallPhase.ENDED) _call.value = null
    }

    fun setMuted(muted: Boolean) {
        audio?.muted = muted
        _call.value = _call.value?.copy(muted = muted)
    }

    fun setSpeaker(on: Boolean) {
        audio?.speakerOn = on
        _call.value = _call.value?.copy(speakerOn = on)
    }

    private fun connectMedia(c: CallState) {
        mediaSeq = 0
        faceSeq = 0
        val cipher = cipherFor(c.peerId)
        audio = ChirpyAudioEngine(context) { encoded ->
            val seq = mediaSeq++
            val sealed = cipher.encryptAt(encoded, c.callId xor seq.toLong())
            links[c.peerId]?.let { sendFrame(it, ChirpyWireFrame.Media(c.callId, seq, sealed)) }
        }.also {
            it.speakerOn = c.speakerOn
            it.muted = c.muted
            it.start()
        }
        _call.value = c.copy(phase = CallPhase.CONNECTED, connectedAtMs = System.currentTimeMillis())
    }

    private fun finishCall(c: CallState, reason: String) {
        audio?.stop()
        audio = null
        faceCapture?.stop()
        _localFace.value = null
        _remoteFace.value = null
        videoSent.set(0)
        videoReceived.set(0)
        _call.value = c.copy(phase = CallPhase.ENDED, endReason = reason, videoOn = false)
    }

    @Synchronized
    private fun onCallFrame(nodeId: String, frame: ChirpyWireFrame.Call) {
        val c = _call.value
        when (frame.action) {
            ChirpyCallAction.INVITE -> {
                if (c != null && c.phase != CallPhase.ENDED) {
                    links[nodeId]?.let { sendFrame(it, ChirpyWireFrame.Call(frame.callId, ChirpyCallAction.DECLINE)) }
                    return
                }
                val alias = contactAliases.getString(nodeId, null) ?: "Peer ${nodeId.takeLast(6)}"
                _call.value = CallState(nodeId, alias, frame.callId, CallPhase.INCOMING_RINGING)
            }
            ChirpyCallAction.ACCEPT ->
                if (c?.callId == frame.callId && c.phase == CallPhase.OUTGOING_RINGING) connectMedia(c)
            ChirpyCallAction.DECLINE ->
                if (c?.callId == frame.callId && c.phase != CallPhase.ENDED) finishCall(c, "Declined")
            ChirpyCallAction.END ->
                if (c?.callId == frame.callId && c.phase != CallPhase.ENDED) finishCall(c, "Call ended")
        }
    }

    private fun onMediaFrame(nodeId: String, frame: ChirpyWireFrame.Media) {
        val c = _call.value ?: return
        if (c.peerId != nodeId || c.callId != frame.callId || c.phase != CallPhase.CONNECTED) return
        try {
            if (frame.kind == ChirpyWireFrame.Media.KIND_FACE) {
                val packet = cipherFor(nodeId).decryptPayload(frame.payload, faceCounter(c.callId, frame.seq))
                videoReceived.addAndGet(frame.payload.size.toLong())
                GhostBandMeshCodec.decode(packet)?.let { _remoteFace.value = it }
                return
            }
            val encoded = cipherFor(nodeId).decryptPayload(frame.payload, c.callId xor frame.seq.toLong())
            audio?.onPacket(frame.seq, encoded)
        } catch (_: Exception) {
            // Tampered or corrupt packet: concealed by the jitter buffer.
        }
    }

    @Synchronized
    fun stop() {
        stopRelay()
        nearby?.stop()
        nearby = null
        socketBridge.stop()
        meshEngine.stopMesh()
    }

    fun isLinked(peerId: String): Boolean = links.containsKey(peerId)

    private val contactAliases = context.getSharedPreferences(CONTACTS, Context.MODE_PRIVATE)

    /** Everyone we have chatted with, newest conversation first, so queued chirps can be written offline. */
    fun recentContacts(timeline: List<ChirpyMessage>): List<ChirpyMeshPeer> =
        timeline.asReversed()
            .map { if (it.senderId == localNodeId) it.recipientId else it.senderId }
            .distinct()
            .map { id ->
                ChirpyMeshPeer(
                    peerIdHex = id,
                    alias = contactAliases.getString(id, null) ?: "Peer ${id.takeLast(6)}",
                    rssiDbm = 0,
                    radioKind = ChirpyRadioKind.INTERNET_RELAY
                )
            }

    fun unreadCount(timeline: List<ChirpyMessage>, peerId: String): Int =
        timeline.count { it.senderId == peerId && it.status != ChirpyDeliveryStatus.READ }

    fun conversationWith(peerId: String): List<ChirpyMessage> = _messages.value.filter {
        (it.senderId == peerId && it.recipientId == localNodeId) ||
            (it.senderId == localNodeId && it.recipientId == peerId)
    }

    fun send(peerId: String, kind: ChirpyPayloadKind, text: String) {
        val wire = seal(peerId, text.toByteArray())
        val msg = crdtLog.createOutboxMessage(
            recipientId = peerId,
            ciphertext = wire,
            signature = identity.signData(wire),
            payloadKind = kind
        )
        plaintextCache[msg.messageId] = text
        publish()
        routeTo(peerId)?.let { deliver(it, msg) }
    }

    /** Marks the open conversation as read and tells the peer, if it is in range. */
    fun openChat(peerId: String?) {
        openChatPeerId = peerId
        if (peerId == null) return
        val unread = _messages.value.filter {
            it.senderId == peerId && it.status.ordinal < ChirpyDeliveryStatus.READ.ordinal
        }
        if (unread.isEmpty()) return
        unread.forEach { crdtLog.updateStatus(it.messageId, ChirpyDeliveryStatus.READ) }
        publish()
        routeTo(peerId)?.let { sender ->
            unread.forEach { sendFrame(sender, ChirpyWireFrame.Ack(it.messageId, ChirpyDeliveryStatus.READ)) }
        }
    }

    fun displayText(msg: ChirpyMessage): String = plaintextCache.getOrPut(msg.messageId) {
        val peerId = if (msg.senderId == localNodeId) msg.recipientId else msg.senderId
        try {
            String(open(peerId, msg.ciphertext))
        } catch (_: Exception) {
            "[unable to decrypt]"
        }
    }

    // --- ChirpyTransportListener -------------------------------------------------------------

    override fun onLinkUp(nodeId: String, alias: String, radio: ChirpyRadioKind, rssiDbm: Int, sender: (ByteArray) -> Unit) {
        links[nodeId] = sender
        contactAliases.edit().putString(nodeId, alias).apply()
        meshEngine.onPeerDiscovered(nodeId, alias, rssiDbm, radio)
        // Store-and-forward: flush everything this peer has not confirmed yet.
        _messages.value
            .filter { it.senderId == localNodeId && it.recipientId == nodeId && it.status.ordinal < ChirpyDeliveryStatus.DELIVERED.ordinal }
            .forEach { deliver(sender, it) }
    }

    override fun onFrame(nodeId: String, frame: ChirpyWireFrame) {
        when (frame) {
            is ChirpyWireFrame.Msg -> receive(nodeId, frame.message)
            is ChirpyWireFrame.Ack -> {
                crdtLog.updateStatus(frame.messageId, frame.status)
                publish()
            }
            is ChirpyWireFrame.Call -> if (links.containsKey(nodeId)) onCallFrame(nodeId, frame)
            is ChirpyWireFrame.Media -> if (links.containsKey(nodeId)) onMediaFrame(nodeId, frame)
            is ChirpyWireFrame.Hello -> Unit
        }
    }

    override fun onLinkDown(nodeId: String) {
        links.remove(nodeId)
        meshEngine.onPeerLost(nodeId)
        _call.value?.let { c ->
            if (c.peerId == nodeId && c.phase != CallPhase.ENDED) synchronized(this) { finishCall(c, "Radio link lost") }
        }
    }

    // --- ChirpyNostrRelay.Listener -----------------------------------------------------------

    override fun onRelayStateChanged(online: Boolean) {
        _relayOnline.value = online
        if (!online) return
        // Long-range store-and-forward: hand every unconfirmed chirp without a radio link to the relays.
        val relayRoute = relay ?: return
        _messages.value
            .filter { it.senderId == localNodeId && !links.containsKey(it.recipientId) && it.status.ordinal < ChirpyDeliveryStatus.DELIVERED.ordinal }
            .forEach { msg -> deliver({ bytes -> relayRoute.publish(msg.recipientId, bytes) }, msg) }
    }

    override fun onRelayFrame(senderNodeId: String, senderAlias: String, frame: ChirpyWireFrame) {
        if (senderAlias.isNotBlank() && !contactAliases.contains(senderNodeId)) contactAliases.edit().putString(senderNodeId, senderAlias).apply()
        onFrame(senderNodeId, frame)
    }

    // --- internals ---------------------------------------------------------------------------

    private fun receive(linkNodeId: String, msg: ChirpyMessage) {
        if (msg.recipientId != localNodeId || msg.senderId != linkNodeId) return
        val authentic = try {
            identity.verifySignature(msg.ciphertext, msg.signature, ChirpyIdentity.publicKeyFromHex(msg.senderId))
        } catch (_: Exception) {
            false
        }
        if (!authentic) {
            Log.w(TAG, "Dropped message with bad signature from $linkNodeId")
            return
        }

        val read = openChatPeerId == msg.senderId
        val status = if (read) ChirpyDeliveryStatus.READ else ChirpyDeliveryStatus.DELIVERED
        crdtLog.mergeIncoming(listOf(msg.copy(status = status)))
        publish()
        routeTo(linkNodeId)?.let { sendFrame(it, ChirpyWireFrame.Ack(msg.messageId, status)) }
    }

    private fun deliver(sender: (ByteArray) -> Unit, msg: ChirpyMessage) {
        val outbound = msg.copy(status = ChirpyDeliveryStatus.IN_FLIGHT_MESH)
        if (sendFrame(sender, ChirpyWireFrame.Msg(outbound)) && msg.status == ChirpyDeliveryStatus.OUTBOX) {
            crdtLog.updateStatus(msg.messageId, ChirpyDeliveryStatus.IN_FLIGHT_MESH)
            publish()
        }
    }

    private fun sendFrame(sender: (ByteArray) -> Unit, frame: ChirpyWireFrame): Boolean = try {
        sender(ChirpyWireCodec.encode(frame))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Send failed", e)
        false
    }

    private fun cipherFor(peerId: String) =
        DoubleRatchetCipher(secrets.getOrPut(peerId) { identity.sharedSecretWith(peerId) })

    /** Wire layout: 8-byte key counter || 12-byte IV || AES-GCM ciphertext. */
    private fun seal(peerId: String, plaintext: ByteArray): ByteArray {
        val counter = random.nextLong()
        val sealed = cipherFor(peerId).encryptAt(plaintext, counter)
        return ByteBuffer.allocate(8 + sealed.size).putLong(counter).put(sealed).array()
    }

    private fun open(peerId: String, wire: ByteArray): ByteArray {
        val counter = ByteBuffer.wrap(wire, 0, 8).long
        return cipherFor(peerId).decryptPayload(wire.copyOfRange(8, wire.size), counter)
    }

    @Synchronized
    private fun publish() {
        val timeline = crdtLog.getTimeline()
        _messages.value = timeline
        saveTimeline(timeline)
    }

    private fun saveTimeline(timeline: List<ChirpyMessage>) {
        try {
            val tmp = File(context.filesDir, "$TIMELINE_FILE.tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(timeline.size)
                for (m in timeline) {
                    val bytes = ChirpyWireCodec.encode(ChirpyWireFrame.Msg(m))
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            }
            tmp.renameTo(File(context.filesDir, TIMELINE_FILE))
        } catch (e: Exception) {
            Log.w(TAG, "Could not save timeline", e)
        }
    }

    private fun loadTimeline() {
        val file = File(context.filesDir, TIMELINE_FILE)
        if (!file.exists()) return
        try {
            val loaded = DataInputStream(file.inputStream().buffered()).use { input ->
                List(input.readInt()) {
                    val bytes = ByteArray(input.readInt()).also { input.readFully(it) }
                    (ChirpyWireCodec.decode(bytes) as ChirpyWireFrame.Msg).message
                }
            }
            crdtLog.mergeIncoming(loaded)
            _messages.value = crdtLog.getTimeline()
        } catch (e: Exception) {
            Log.w(TAG, "Could not load timeline", e)
        }
    }

    private fun loadOrCreateIdentity(): ChirpyIdentity {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pub = prefs.getString("public", null)
        val priv = prefs.getString("private", null)
        val alias = prefs.getString("alias", null) ?: defaultAlias()
        if (pub != null && priv != null) {
            try {
                return ChirpyIdentity.restore(
                    Base64.decode(pub, Base64.NO_WRAP), Base64.decode(priv, Base64.NO_WRAP), alias
                )
            } catch (e: Exception) {
                Log.w(TAG, "Stored identity unreadable, generating a new one", e)
            }
        }
        val fresh = ChirpyIdentity.createNewIdentity(alias)
        prefs.edit()
            .putString("public", Base64.encodeToString(fresh.keyPair.public.encoded, Base64.NO_WRAP))
            .putString("private", Base64.encodeToString(fresh.keyPair.private.encoded, Base64.NO_WRAP))
            .putString("alias", alias)
            .apply()
        return fresh
    }

    private fun defaultAlias(): String {
        val model = Build.MODEL ?: "Phone"
        return if (model.startsWith(Build.MANUFACTURER ?: "", ignoreCase = true)) model
        else "${(Build.MANUFACTURER ?: "").replaceFirstChar { it.uppercase() }} $model".trim()
    }
}
