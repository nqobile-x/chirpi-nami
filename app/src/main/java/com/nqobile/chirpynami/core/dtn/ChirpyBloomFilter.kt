package com.nqobile.chirpynami.core.dtn

import java.io.Serializable
import java.util.BitSet

class ChirpyBloomFilter(
    val bitSize: Int = 1024,
    val hashCount: Int = 3
) : Serializable {

    private val bitSet = BitSet(bitSize)

    fun add(key: String) {
        val hashes = computeHashes(key)
        for (h in hashes) {
            bitSet.set(Math.floorMod(h, bitSize))
        }
    }

    fun mightContain(key: String): Boolean {
        val hashes = computeHashes(key)
        for (h in hashes) {
            if (!bitSet.get(Math.floorMod(h, bitSize))) {
                return false
            }
        }
        return true
    }

    fun toByteArray(): ByteArray = bitSet.toByteArray()

    private fun computeHashes(key: String): IntArray {
        val result = IntArray(hashCount)
        val h1 = key.hashCode()
        val h2 = (key.reversed() + "chirpy").hashCode()
        for (i in 0 until hashCount) {
            result[i] = h1 + (i * h2)
        }
        return result
    }
}
