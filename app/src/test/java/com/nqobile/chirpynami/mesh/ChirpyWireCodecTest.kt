package com.nqobile.chirpynami.mesh

import com.nqobile.chirpynami.core.crdt.ChirpyCrdtLog
import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.crdt.ChirpyPayloadKind
import com.nqobile.chirpynami.core.crypto.ChirpyIdentity
import com.nqobile.chirpynami.core.crypto.DoubleRatchetCipher
import com.nqobile.chirpynami.core.mesh.ChirpyWireCodec
import com.nqobile.chirpynami.core.mesh.ChirpyWireFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChirpyWireCodecTest {

    @Test
    fun messageFrameRoundTripsEveryField() {
        val log = ChirpyCrdtLog("node-a")
        val original = log.createOutboxMessage(
            recipientId = "node-b",
            ciphertext = byteArrayOf(1, 2, 3, 4),
            signature = byteArrayOf(9, 8, 7),
            payloadKind = ChirpyPayloadKind.STICKER
        )

        val decoded = ChirpyWireCodec.decode(ChirpyWireCodec.encode(ChirpyWireFrame.Msg(original)))
            as ChirpyWireFrame.Msg

        val m = decoded.message
        assertEquals(original.messageId, m.messageId)
        assertEquals("node-a", m.senderId)
        assertEquals("node-b", m.recipientId)
        assertArrayEquals(original.ciphertext, m.ciphertext)
        assertArrayEquals(original.signature, m.signature)
        assertEquals(original.lamportTimestamp, m.lamportTimestamp)
        assertEquals(original.vectorClock, m.vectorClock)
        assertEquals(ChirpyPayloadKind.STICKER, m.payloadKind)
        assertEquals(ChirpyDeliveryStatus.OUTBOX, m.status)
        assertEquals(original.epochMs, m.epochMs)
    }

    @Test
    fun helloAndAckRoundTrip() {
        val hello = ChirpyWireFrame.Hello("abc123", "Nqobza")
        assertEquals(hello, ChirpyWireCodec.decode(ChirpyWireCodec.encode(hello)))

        val ack = ChirpyWireFrame.Ack("msg-1", ChirpyDeliveryStatus.READ)
        assertEquals(ack, ChirpyWireCodec.decode(ChirpyWireCodec.encode(ack)))
    }

    @Test
    fun garbageDecodesToNull() {
        assertNull(ChirpyWireCodec.decode(byteArrayOf(42, 1, 2)))
        assertNull(ChirpyWireCodec.decode(ByteArray(0)))
    }

    @Test
    fun ecdhSecretsMatchAndSealedChirpsDecrypt() {
        val alice = ChirpyIdentity.createNewIdentity("Alice")
        val bob = ChirpyIdentity.createNewIdentity("Bob")

        val aliceSide = alice.sharedSecretWith(bob.publicKeyHex)
        val bobSide = bob.sharedSecretWith(alice.publicKeyHex)
        assertArrayEquals(aliceSide, bobSide)

        val sealed = DoubleRatchetCipher(aliceSide).encryptAt("Sho my chom".toByteArray(), 77L)
        val opened = DoubleRatchetCipher(bobSide).decryptPayload(sealed, 77L)
        assertEquals("Sho my chom", String(opened))

        val signature = alice.signData(sealed)
        assertTrue(bob.verifySignature(sealed, signature, ChirpyIdentity.publicKeyFromHex(alice.publicKeyHex)))
    }
}
