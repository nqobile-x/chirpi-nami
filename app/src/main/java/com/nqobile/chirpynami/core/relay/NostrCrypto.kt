package com.nqobile.chirpynami.core.relay

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Minimal secp256k1 + BIP-340 Schnorr, enough to sign Nostr events.
 *
 * Pure BigInteger maths (no native libs) so it runs on Android 7.0 and in JVM unit tests.
 * Signing is only done once per outgoing relay event, so affine arithmetic is fast enough.
 */
object Secp256k1Schnorr {
    private val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val G = Point(
        BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    )

    private data class Point(val x: BigInteger, val y: BigInteger)

    private fun add(a: Point?, b: Point?): Point? {
        if (a == null) return b
        if (b == null) return a
        if (a.x == b.x && a.y != b.y) return null
        val lambda = if (a == b) {
            BigInteger.valueOf(3).multiply(a.x.pow(2)).multiply(a.y.shiftLeft(1).modInverse(P)).mod(P)
        } else {
            b.y.subtract(a.y).multiply(b.x.subtract(a.x).modInverse(P)).mod(P)
        }
        val x = lambda.pow(2).subtract(a.x).subtract(b.x).mod(P)
        return Point(x, lambda.multiply(a.x.subtract(x)).subtract(a.y).mod(P))
    }

    private fun multiply(point: Point, scalar: BigInteger): Point? {
        var result: Point? = null
        var addend: Point? = point
        for (i in 0 until scalar.bitLength()) {
            if (scalar.testBit(i)) result = add(result, addend)
            addend = add(addend, addend)
        }
        return result
    }

    private fun liftX(x: BigInteger): Point? {
        if (x >= P) return null
        val ySq = x.modPow(BigInteger.valueOf(3), P).add(BigInteger.valueOf(7)).mod(P)
        val y = ySq.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
        if (y.modPow(BigInteger.valueOf(2), P) != ySq) return null
        return Point(x, if (y.testBit(0)) P.subtract(y) else y)
    }

    fun taggedHash(tag: String, vararg parts: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray())
        val md = MessageDigest.getInstance("SHA-256")
        md.update(tagHash)
        md.update(tagHash)
        parts.forEach { md.update(it) }
        return md.digest()
    }

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun bytes32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    private fun int(bytes: ByteArray) = BigInteger(1, bytes)

    /** x-only public key (32 bytes) for a 32-byte secret key. */
    fun publicKey(secretKey: ByteArray): ByteArray {
        val d = int(secretKey)
        require(d > BigInteger.ZERO && d < N) { "Invalid secret key" }
        return bytes32(multiply(G, d)!!.x)
    }

    fun newSecretKey(random: SecureRandom = SecureRandom()): ByteArray {
        while (true) {
            val candidate = ByteArray(32).also { random.nextBytes(it) }
            val d = int(candidate)
            if (d > BigInteger.ZERO && d < N) return candidate
        }
    }

    fun sign(message: ByteArray, secretKey: ByteArray, auxRand: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }): ByteArray {
        val d0 = int(secretKey)
        require(d0 > BigInteger.ZERO && d0 < N) { "Invalid secret key" }
        val publicPoint = multiply(G, d0)!!
        val d = if (publicPoint.y.testBit(0)) N.subtract(d0) else d0
        val t = bytes32(d).zip(taggedHash("BIP0340/aux", auxRand)) { a, b -> (a.toInt() xor b.toInt()).toByte() }.toByteArray()
        val px = bytes32(publicPoint.x)
        val k0 = int(taggedHash("BIP0340/nonce", t, px, message)).mod(N)
        require(k0 != BigInteger.ZERO) { "Nonce failure" }
        val r = multiply(G, k0)!!
        val k = if (r.y.testBit(0)) N.subtract(k0) else k0
        val rx = bytes32(r.x)
        val e = int(taggedHash("BIP0340/challenge", rx, px, message)).mod(N)
        return rx + bytes32(k.add(e.multiply(d)).mod(N))
    }

    fun verify(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        val pk = liftX(int(publicKey)) ?: return false
        val r = int(signature.copyOfRange(0, 32))
        val s = int(signature.copyOfRange(32, 64))
        if (r >= P || s >= N) return false
        val e = int(taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32), publicKey, message)).mod(N)
        val point = add(multiply(G, s), multiply(pk, N.subtract(e))) ?: return false
        return !point.y.testBit(0) && point.x == r
    }
}

/** NIP-01 event construction (id = sha256 of the canonical serialisation, Schnorr-signed). */
object NostrEvents {
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun jsonString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun tagsJson(tags: List<List<String>>) =
        tags.joinToString(",", "[", "]") { tag -> tag.joinToString(",", "[", "]") { jsonString(it) } }

    fun eventId(pubkeyHex: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
        val canonical = "[0,${jsonString(pubkeyHex)},$createdAt,$kind,${tagsJson(tags)},${jsonString(content)}]"
        return hex(Secp256k1Schnorr.sha256(canonical.toByteArray(Charsets.UTF_8)))
    }

    /** Returns the complete signed event as a JSON object string. */
    fun signedEvent(secretKey: ByteArray, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
        val pubkey = hex(Secp256k1Schnorr.publicKey(secretKey))
        val id = eventId(pubkey, createdAt, kind, tags, content)
        val idBytes = ByteArray(32) { id.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val sig = hex(Secp256k1Schnorr.sign(idBytes, secretKey))
        return "{\"id\":\"$id\",\"pubkey\":\"$pubkey\",\"created_at\":$createdAt,\"kind\":$kind," +
            "\"tags\":${tagsJson(tags)},\"content\":${jsonString(content)},\"sig\":\"$sig\"}"
    }
}
