package com.nqobile.chirpynami.core.crypto

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature

/**
 * Chirpy-NAMi Self-Sovereign Identity (SSI) Engine.
 *
 * Replaces expensive phone number SMS verification with asymmetric cryptography.
 * Compatible with Android 7.0 Nougat (API 24+) hardware keystore.
 *
 * "NAMi" (Zulu/Xhosa: "With Me") represents mutual trust:
 * Identity is rooted in the device, requiring no central telecom registration.
 */
class ChirpyIdentity(
    val keyPair: KeyPair,
    val alias: String
) {
    val publicKeyHex: String = keyPair.public.encoded.joinToString("") { "%02x".format(it) }

    fun signData(data: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }

    fun verifySignature(data: ByteArray, signature: ByteArray, foreignPublicKey: java.security.PublicKey): Boolean {
        return try {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(foreignPublicKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    /** ECDH shared secret with a peer, keyed by that peer's public-key hex (its node id). */
    fun sharedSecretWith(peerPublicKeyHex: String): ByteArray {
        val agreement = javax.crypto.KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(publicKeyFromHex(peerPublicKeyHex), true)
        return java.security.MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
    }

    companion object {
        fun publicKeyFromHex(hex: String): java.security.PublicKey {
            val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            return java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(bytes))
        }

        /** Rebuilds a persisted identity from its X.509 public and PKCS#8 private encodings. */
        fun restore(publicEncoded: ByteArray, privateEncoded: ByteArray, alias: String): ChirpyIdentity {
            val factory = java.security.KeyFactory.getInstance("EC")
            val publicKey = factory.generatePublic(java.security.spec.X509EncodedKeySpec(publicEncoded))
            val privateKey = factory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privateEncoded))
            return ChirpyIdentity(KeyPair(publicKey, privateKey), alias)
        }

        fun createNewIdentity(alias: String): ChirpyIdentity {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256, SecureRandom())
            val kp = kpg.generateKeyPair()
            return ChirpyIdentity(kp, alias)
        }
    }
}
