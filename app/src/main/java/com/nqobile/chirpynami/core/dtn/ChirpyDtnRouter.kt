package com.nqobile.chirpynami.core.dtn

import com.nqobile.chirpynami.core.crdt.ChirpyCrdtLog
import com.nqobile.chirpynami.core.crdt.ChirpyDeliveryStatus
import com.nqobile.chirpynami.core.crdt.ChirpyMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Chirpy-NAMi Epidemic Gossip Router.
 *
 * Designed for SADC transit corridors (Johannesburg <-> Gaborone <-> Bulawayo).
 * Devices exchange 128-byte Bloom filters during momentary radio encounters (at taxi ranks or border gates),
 * syncing missing bundles in sub-second bursts with zero cellular data.
 */
class ChirpyDtnRouter(
    val localNodeId: String,
    val messageLog: ChirpyCrdtLog,
    private val maxCacheSize: Int = 500
) {
    private val bundleCache = ConcurrentHashMap<String, ChirpyDtnBundle>()

    fun getBundleCount(): Int = bundleCache.size

    fun storeOutboxBundle(msg: ChirpyMessage): ChirpyDtnBundle {
        val bundle = ChirpyDtnBundle(
            bundleId = msg.messageId,
            message = msg.copy(status = ChirpyDeliveryStatus.IN_FLIGHT_MESH),
            ttlHops = 12
        )
        bundleCache[bundle.bundleId] = bundle
        return bundle
    }

    fun makeBloomFilter(): ChirpyBloomFilter {
        val filter = ChirpyBloomFilter(bitSize = 1024, hashCount = 3)
        for (k in bundleCache.keys) {
            filter.add(k)
        }
        return filter
    }

    fun findMissingBundles(remoteFilter: ChirpyBloomFilter, remoteNodeId: String): List<ChirpyDtnBundle> {
        val toSend = mutableListOf<ChirpyDtnBundle>()
        for (b in bundleCache.values) {
            if (b.isExpired) continue
            if (b.carrierTrail.contains(remoteNodeId)) continue
            if (!remoteFilter.mightContain(b.bundleId)) {
                toSend.add(b)
            }
        }
        return toSend
    }

    fun ingestPeerBundles(bundles: List<ChirpyDtnBundle>, peerId: String): List<ChirpyMessage> {
        val deliveredToMe = mutableListOf<ChirpyMessage>()
        for (b in bundles) {
            if (b.isExpired) continue
            val updated = b.carryThroughNode(localNodeId)

            if (b.message.recipientId == localNodeId) {
                val delivered = b.message.copy(
                    status = ChirpyDeliveryStatus.DELIVERED,
                    meshHops = updated.message.meshHops
                )
                messageLog.mergeIncoming(listOf(delivered))
                deliveredToMe.add(delivered)
            } else {
                if (bundleCache.size < maxCacheSize) {
                    bundleCache[updated.bundleId] = updated
                }
            }
        }
        return deliveredToMe
    }
}
