package com.nqobile.chirpynami.core.dtn

import com.nqobile.chirpynami.core.crdt.ChirpyMessage
import java.io.Serializable

data class ChirpyDtnBundle(
    val bundleId: String,
    val message: ChirpyMessage,
    val ttlHops: Int = 12,
    val expiresAtEpochMs: Long = System.currentTimeMillis() + (7L * 24 * 3600 * 1000),
    val carrierTrail: List<String> = emptyList()
) : Serializable {

    val isExpired: Boolean
        get() = ttlHops <= 0 || System.currentTimeMillis() > expiresAtEpochMs

    fun carryThroughNode(nodeId: String): ChirpyDtnBundle {
        return copy(
            ttlHops = ttlHops - 1,
            carrierTrail = carrierTrail + nodeId,
            message = message.copy(meshHops = message.meshHops + 1)
        )
    }
}
