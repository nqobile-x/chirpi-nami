package com.nqobile.chirpynami.core.mesh

import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.crdt.ChirpyMessage
import com.nqobile.chirpynami.core.crdt.ChirpyPayloadKind
import com.nqobile.chirpynami.core.crdt.VectorClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Transport-agnostic wire frames exchanged between Chirpi Nami peers.
 *
 * The same frames travel over Nearby Connections (BLE / Wi-Fi) and the local TCP bridge,
 * and the MSG frame doubles as the on-disk timeline record format.
 */
sealed interface ChirpyWireFrame {
    /** First frame on every link: binds the transport endpoint to a public-key identity. */
    data class Hello(val nodeId: String, val alias: String) : ChirpyWireFrame

    data class Msg(val message: ChirpyMessage) : ChirpyWireFrame

    /** Delivery / read receipt for a message previously sent by the receiver of this frame. */
    data class Ack(val messageId: String, val status: ChirpyDeliveryStatus) : ChirpyWireFrame

    /** Live call signalling. Only ever sent over a direct link (radio or bridge), never the relay. */
    data class Call(val callId: Long, val action: ChirpyCallAction) : ChirpyWireFrame

    /** One encrypted media packet of a live call: 40 ms of voice, or one GhostBand face frame. */
    class Media(val callId: Long, val seq: Int, val payload: ByteArray, val kind: Int = KIND_AUDIO) : ChirpyWireFrame {
        companion object {
            const val KIND_AUDIO = 0
            const val KIND_FACE = 1
        }
    }
}

enum class ChirpyCallAction { INVITE, ACCEPT, DECLINE, END }

object ChirpyWireCodec {
    private const val VERSION: Byte = 1
    private const val TYPE_HELLO: Byte = 1
    private const val TYPE_MSG: Byte = 2
    private const val TYPE_ACK: Byte = 3
    private const val TYPE_CALL: Byte = 4
    private const val TYPE_MEDIA: Byte = 5

    fun encode(frame: ChirpyWireFrame): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION.toInt())
            when (frame) {
                is ChirpyWireFrame.Hello -> {
                    out.writeByte(TYPE_HELLO.toInt())
                    out.writeUTF(frame.nodeId)
                    out.writeUTF(frame.alias)
                }
                is ChirpyWireFrame.Msg -> {
                    out.writeByte(TYPE_MSG.toInt())
                    writeMessage(out, frame.message)
                }
                is ChirpyWireFrame.Ack -> {
                    out.writeByte(TYPE_ACK.toInt())
                    out.writeUTF(frame.messageId)
                    out.writeByte(frame.status.ordinal)
                }
                is ChirpyWireFrame.Call -> {
                    out.writeByte(TYPE_CALL.toInt())
                    out.writeLong(frame.callId)
                    out.writeByte(frame.action.ordinal)
                }
                is ChirpyWireFrame.Media -> {
                    out.writeByte(TYPE_MEDIA.toInt())
                    out.writeLong(frame.callId)
                    out.writeByte(frame.kind)
                    out.writeInt(frame.seq)
                    out.writeShort(frame.payload.size)
                    out.write(frame.payload)
                }
            }
        }
        return bytes.toByteArray()
    }

    /** Returns null for frames from an unknown protocol version or of an unknown type. */
    fun decode(bytes: ByteArray): ChirpyWireFrame? {
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readByte() != VERSION) return null
                when (input.readByte()) {
                    TYPE_HELLO -> ChirpyWireFrame.Hello(input.readUTF(), input.readUTF())
                    TYPE_MSG -> ChirpyWireFrame.Msg(readMessage(input))
                    TYPE_ACK -> ChirpyWireFrame.Ack(
                        input.readUTF(),
                        ChirpyDeliveryStatus.entries[input.readUnsignedByte()]
                    )
                    TYPE_CALL -> ChirpyWireFrame.Call(input.readLong(), ChirpyCallAction.entries[input.readUnsignedByte()])
                    TYPE_MEDIA -> {
                        val callId = input.readLong()
                        val kind = input.readUnsignedByte()
                        val seq = input.readInt()
                        ChirpyWireFrame.Media(callId, seq, ByteArray(input.readUnsignedShort()).also { input.readFully(it) }, kind)
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeMessage(out: DataOutputStream, m: ChirpyMessage) {
        out.writeUTF(m.messageId)
        out.writeUTF(m.senderId)
        out.writeUTF(m.recipientId)
        out.writeInt(m.ciphertext.size)
        out.write(m.ciphertext)
        out.writeInt(m.signature.size)
        out.write(m.signature)
        out.writeLong(m.lamportTimestamp)
        out.writeInt(m.vectorClock.clockMap.size)
        for ((node, tick) in m.vectorClock.clockMap) {
            out.writeUTF(node)
            out.writeLong(tick)
        }
        out.writeByte(m.payloadKind.ordinal)
        out.writeByte(m.status.ordinal)
        out.writeInt(m.meshHops)
        out.writeLong(m.epochMs)
    }

    private fun readMessage(input: DataInputStream): ChirpyMessage {
        val messageId = input.readUTF()
        val senderId = input.readUTF()
        val recipientId = input.readUTF()
        val ciphertext = ByteArray(input.readInt()).also { input.readFully(it) }
        val signature = ByteArray(input.readInt()).also { input.readFully(it) }
        val lamport = input.readLong()
        val clock = (0 until input.readInt()).associate { input.readUTF() to input.readLong() }
        return ChirpyMessage(
            messageId = messageId,
            senderId = senderId,
            recipientId = recipientId,
            ciphertext = ciphertext,
            signature = signature,
            lamportTimestamp = lamport,
            vectorClock = VectorClock(clock),
            payloadKind = ChirpyPayloadKind.entries[input.readUnsignedByte()],
            status = ChirpyDeliveryStatus.entries[input.readUnsignedByte()],
            meshHops = input.readInt(),
            epochMs = input.readLong()
        )
    }
}

/** Callbacks a radio transport raises towards the mesh controller. Called from transport threads. */
interface ChirpyTransportListener {
    fun onLinkUp(nodeId: String, alias: String, radio: ChirpyRadioKind, rssiDbm: Int, sender: (ByteArray) -> Unit)
    fun onFrame(nodeId: String, frame: ChirpyWireFrame)
    fun onLinkDown(nodeId: String)
}
