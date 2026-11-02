package com.nqobile.chirpynami.core.mesh

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Local TCP bridge transport, used to link a physical phone with an emulator over ADB.
 *
 * Every device listens on [LISTEN_PORT]. It also keeps dialling 127.0.0.1:[DIAL_PORT],
 * which only leads somewhere when ADB has bridged it:
 *
 *   adb -s emulator-5554 forward tcp:9876 tcp:9876   (PC 9876   -> emulator app)
 *   adb -s <phone>       reverse tcp:9877 tcp:9876   (phone 9877 -> PC 9876)
 *
 * Frames are length-prefixed [ChirpyWireCodec] payloads; the first frame is always HELLO.
 */
class ChirpySocketTransport(
    private val localNodeId: String,
    private val localAlias: String,
    private val listener: ChirpyTransportListener
) {
    companion object {
        const val LISTEN_PORT = 9876
        const val DIAL_PORT = 9877
        private const val MAX_FRAME_BYTES = 1 shl 20
    }

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var dialledSocket: Socket? = null

    fun start() {
        if (running) return
        running = true

        thread(name = "chirpy-socket-accept", isDaemon = true) {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(LISTEN_PORT))
                }
                serverSocket = server
                while (running) {
                    val socket = server.accept()
                    thread(name = "chirpy-socket-in", isDaemon = true) { serve(socket) }
                }
            } catch (_: Exception) {
                // Port busy or transport stopped: the bridge is optional.
            }
        }

        thread(name = "chirpy-socket-dial", isDaemon = true) {
            while (running) {
                if (dialledSocket == null) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress("127.0.0.1", DIAL_PORT), 1500)
                        dialledSocket = socket
                        thread(name = "chirpy-socket-out", isDaemon = true) {
                            serve(socket)
                            dialledSocket = null
                        }
                    } catch (_: Exception) {
                        // Nothing bridged yet.
                    }
                }
                Thread.sleep(3000)
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        sockets.forEach { try { it.close() } catch (_: Exception) {} }
        sockets.clear()
    }

    private fun serve(socket: Socket) {
        sockets.add(socket)
        var remoteNodeId: String? = null
        // Writes are queued off the caller's thread: Android forbids socket IO on the main thread.
        val writer = Executors.newSingleThreadExecutor { Thread(it, "chirpy-socket-write").apply { isDaemon = true } }
        try {
            socket.tcpNoDelay = true
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val send: (ByteArray) -> Unit = { bytes ->
                writer.execute {
                    try {
                        output.writeInt(bytes.size)
                        output.write(bytes)
                        output.flush()
                    } catch (_: Exception) {
                        try { socket.close() } catch (_: Exception) {}
                    }
                }
            }
            send(ChirpyWireCodec.encode(ChirpyWireFrame.Hello(localNodeId, localAlias)))

            while (running) {
                val length = input.readInt()
                if (length !in 1..MAX_FRAME_BYTES) break
                val bytes = ByteArray(length).also { input.readFully(it) }
                when (val frame = ChirpyWireCodec.decode(bytes)) {
                    is ChirpyWireFrame.Hello -> {
                        if (frame.nodeId == localNodeId) break // dialled ourselves
                        remoteNodeId = frame.nodeId
                        listener.onLinkUp(frame.nodeId, frame.alias, ChirpyRadioKind.LOCAL_BRIDGE, -50, send)
                    }
                    null -> Unit
                    else -> remoteNodeId?.let { listener.onFrame(it, frame) }
                }
            }
        } catch (_: Exception) {
            // Link dropped.
        } finally {
            writer.shutdown()
            sockets.remove(socket)
            try { socket.close() } catch (_: Exception) {}
            remoteNodeId?.let { listener.onLinkDown(it) }
        }
    }
}
