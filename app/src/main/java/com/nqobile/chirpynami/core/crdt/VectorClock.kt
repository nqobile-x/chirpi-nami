package com.nqobile.chirpynami.core.crdt

import java.io.Serializable

data class VectorClock(
    val clockMap: Map<String, Long> = emptyMap()
) : Serializable, Comparable<VectorClock> {

    fun increment(peerId: String): VectorClock {
        val current = clockMap[peerId] ?: 0L
        return VectorClock(clockMap + (peerId to current + 1L))
    }

    fun merge(other: VectorClock): VectorClock {
        val allKeys = clockMap.keys + other.clockMap.keys
        val merged = allKeys.associateWith { key ->
            maxOf(clockMap[key] ?: 0L, other.clockMap[key] ?: 0L)
        }
        return VectorClock(merged)
    }

    override fun compareTo(other: VectorClock): Int {
        var lessThan = false
        var greaterThan = false

        val allKeys = clockMap.keys + other.clockMap.keys
        for (key in allKeys) {
            val v1 = clockMap[key] ?: 0L
            val v2 = other.clockMap[key] ?: 0L
            if (v1 < v2) lessThan = true
            if (v1 > v2) greaterThan = true
        }

        return when {
            lessThan && !greaterThan -> -1
            greaterThan && !lessThan -> 1
            else -> 0
        }
    }
}
