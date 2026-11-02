package com.nqobile.chirpynami.relay

import com.nqobile.chirpynami.core.relay.NostrEvents
import com.nqobile.chirpynami.core.relay.Secp256k1Schnorr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrCryptoTest {

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // Official BIP-340 test vectors 0 and 1.
    @Test
    fun bip340Vector0() {
        val sk = bytes("0000000000000000000000000000000000000000000000000000000000000003")
        assertEquals("f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9", NostrEvents.hex(Secp256k1Schnorr.publicKey(sk)))
        val sig = Secp256k1Schnorr.sign(ByteArray(32), sk, ByteArray(32))
        assertEquals(
            "e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca821525f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0",
            NostrEvents.hex(sig)
        )
    }

    @Test
    fun bip340Vector1() {
        val sk = bytes("b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef")
        val msg = bytes("243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c89")
        val aux = bytes("0000000000000000000000000000000000000000000000000000000000000001")
        assertEquals("dff1d77f2a671c5f36183726db2341be58feae1da2deced843240f7b502ba659", NostrEvents.hex(Secp256k1Schnorr.publicKey(sk)))
        assertEquals(
            "6896bd60eeae296db48a229ff71dfe071bde413e6d43f917dc8dcf8c78de33418906d11ac976abccb20b091292bff4ea897efcb639ea871cfa95f6de339e4b0a",
            NostrEvents.hex(Secp256k1Schnorr.sign(msg, sk, aux))
        )
    }

    @Test
    fun randomKeySignsAndVerifies() {
        val sk = Secp256k1Schnorr.newSecretKey()
        val pk = Secp256k1Schnorr.publicKey(sk)
        val msg = Secp256k1Schnorr.sha256("chirp".toByteArray())
        val sig = Secp256k1Schnorr.sign(msg, sk)
        assertTrue(Secp256k1Schnorr.verify(msg, pk, sig))
        sig[10] = (sig[10].toInt() xor 1).toByte()
        assertFalse(Secp256k1Schnorr.verify(msg, pk, sig))
    }

    @Test
    fun eventIdUsesCanonicalNip01Serialisation() {
        val pubkey = "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
        val tags = listOf(listOf("p", "ab"))
        val content = "line\n\"quoted\""
        val canonical = "[0,\"$pubkey\",1700000000,4242,[[\"p\",\"ab\"]],\"line\\n\\\"quoted\\\"\"]"
        assertEquals(
            NostrEvents.hex(Secp256k1Schnorr.sha256(canonical.toByteArray())),
            NostrEvents.eventId(pubkey, 1700000000, 4242, tags, content)
        )
    }
}
