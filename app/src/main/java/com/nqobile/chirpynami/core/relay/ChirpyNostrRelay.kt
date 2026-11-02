package com.nqobile.chirpynami.core.relay

import android.util.Base64
import android.util.Log
import com.nqobile.chirpynami.core.mesh.ChirpyWireCodec
import com.nqobile.chirpynami.core.mesh.ChirpyWireFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Long-range transport over public Nostr relays, so chirps reach contacts in other cities or countries.
 *
 * Relays only ever see: a random per-device relay key, a hash of the recipient's node id, timing,
 * and an opaque blob. The blob is the same signed, AES-GCM encrypted wire frame used over radio,
 * so relays can neither read nor forge chirps. Relays store events, so a recipient who is offline
 * picks the chirp up the next time they come online.
 */
class ChirpyNostrRelay(
    private val localNodeId: String,
    /** Seals our display name for one recipient, so relays never see it in the clear. */
    private val sealAlias: (recipientNodeId: String) -> ByteArray,
    private val openAlias: (senderNodeId: String, sealed: ByteArray) -> String?,
    private val secretKey: ByteArray,
    initialSince: Long,
    private val onSinceAdvanced: (Long) -> Unit,
    private val listener: Listener
) {
    interface Listener {
        fun onRelayStateChanged(online: Boolean)
        fun onRelayFrame(senderNodeId: String, senderAlias: String, frame: ChirpyWireFrame)
    }

    companion object {
        private const val TAG = "ChirpyRelay"
        /** Regular (stored) application kind for Chirpi Nami envelopes. */
        const val KIND = 4242
        private const val ENVELOPE_VERSION = 2

        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://nostr.mom",
            "wss://relay.primal.net"
        )

        /** Public routing tag for a node: relays filter on it without learning the identity key. */
        fun routingTag(nodeId: String): String = NostrEvents.hex(Secp256k1Schnorr.sha256(nodeId.toByteArray()))
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "chirpy-relay").apply { isDaemon = true } }
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val seenEvents = Collections.synchronizedSet(LinkedHashSet<String>())
    private val myTag = routingTag(localNodeId)

    @Volatile private var running = false
    @Volatile private var since = initialSince

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    fun start() {
        if (running) return
        running = true
        DEFAULT_RELAYS.forEach { connect(it) }
    }

    fun stop() {
        running = false
        sockets.values.forEach { it.close(1000, "bye") }
        sockets.clear()
        _online.value = false
    }

    /** Publishes a wire frame for [recipientNodeId]. Throws if no relay is connected. */
    fun publish(recipientNodeId: String, frameBytes: ByteArray) {
        check(sockets.isNotEmpty()) { "No relay connected" }
        val createdAt = System.currentTimeMillis() / 1000
        // Schnorr signing is pure BigInteger maths: keep it off the caller's (often UI) thread.
        scheduler.execute {
            val event = NostrEvents.signedEvent(
                secretKey = secretKey,
                createdAt = createdAt,
                kind = KIND,
                tags = listOf(listOf("p", routingTag(recipientNodeId))),
                content = Base64.encodeToString(envelope(recipientNodeId, frameBytes), Base64.NO_WRAP)
            )
            val message = "[\"EVENT\",$event]"
            sockets.values.forEach { it.send(message) }
        }
    }

    private fun connect(url: String) {
        if (!running) return
        client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sockets[url] = webSocket
                val filter = JSONObject()
                    .put("kinds", JSONArray().put(KIND))
                    .put("#p", JSONArray().put(myTag))
                    .put("since", since - 120) // tolerate clock skew between handsets
                webSocket.send(JSONArray().put("REQ").put("chirpy").put(filter).toString())
                val wasOnline = _online.value
                _online.value = true
                if (!wasOnline) listener.onRelayStateChanged(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(url, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "$url failed: ${t.message}")
                dropped(url)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                dropped(url)
            }
        })
    }

    private fun dropped(url: String) {
        sockets.remove(url)
        val nowOnline = sockets.isNotEmpty()
        if (_online.value != nowOnline) {
            _online.value = nowOnline
            listener.onRelayStateChanged(nowOnline)
        }
        if (running) scheduler.schedule({ connect(url) }, 15, TimeUnit.SECONDS)
    }

    private fun handle(url: String, text: String) {
        try {
            val msg = JSONArray(text)
            when (msg.getString(0)) {
                "EVENT" -> {
                    val event = msg.getJSONObject(2)
                    if (event.getInt("kind") != KIND || !seenEvents.add(event.getString("id"))) return
                    if (seenEvents.size > 2000) seenEvents.remove(seenEvents.first())
                    val createdAt = event.getLong("created_at")
                    val (sender, alias, frame) = openEnvelope(Base64.decode(event.getString("content"), Base64.NO_WRAP)) ?: return
                    if (sender == localNodeId) return
                    listener.onRelayFrame(sender, alias, frame)
                    if (createdAt > since) {
                        since = createdAt
                        onSinceAdvanced(createdAt)
                    }
                }
                "OK" -> if (!msg.optBoolean(2, true)) Log.w(TAG, "$url rejected event: ${msg.optString(3)}")
                "NOTICE", "CLOSED" -> Log.i(TAG, "$url: $text")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bad relay message from $url", e)
        }
    }

    private fun envelope(recipientNodeId: String, frameBytes: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(ENVELOPE_VERSION)
            out.writeUTF(localNodeId)
            val alias = sealAlias(recipientNodeId)
            out.writeShort(alias.size)
            out.write(alias)
            out.writeInt(frameBytes.size)
            out.write(frameBytes)
        }
        return bytes.toByteArray()
    }

    private fun openEnvelope(bytes: ByteArray): Triple<String, String, ChirpyWireFrame>? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readUnsignedByte() != ENVELOPE_VERSION) return null
            val sender = input.readUTF()
            val sealedAlias = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
            val alias = openAlias(sender, sealedAlias) ?: ""
            val frame = ByteArray(input.readInt()).also { input.readFully(it) }
            ChirpyWireCodec.decode(frame)?.let { Triple(sender, alias, it) }
        }
    } catch (_: Exception) {
        null
    }
}
