package com.nqobile.chirpynami.core.call

/**
 * IMA-ADPCM voice codec: 16-bit PCM in, 4 bits per sample out (4:1).
 *
 * At 8 kHz narrowband that is 32 kbps, small enough for a Bluetooth-only Nearby link and
 * trivial for Wi-Fi. Pure Kotlin, no native code, works on Android 7.0.
 *
 * Every packet starts with a 3-byte header (predictor + step index) so each packet decodes
 * on its own: a dropped packet costs 40 ms of audio, never the packets after it.
 */
object ImaAdpcmCodec {
    private val INDEX_TABLE = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)
    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    /** Encoder state kept between packets so quality stays continuous on a clean link. */
    class Encoder {
        private var predictor = 0
        private var index = 0

        fun encode(pcm: ShortArray): ByteArray {
            val out = ByteArray(3 + (pcm.size + 1) / 2)
            out[0] = (predictor shr 8).toByte()
            out[1] = predictor.toByte()
            out[2] = index.toByte()
            for (i in pcm.indices) {
                val nibble = encodeSample(pcm[i].toInt())
                val pos = 3 + i / 2
                out[pos] = if (i % 2 == 0) nibble.toByte() else (out[pos].toInt() or (nibble shl 4)).toByte()
            }
            return out
        }

        private fun encodeSample(sample: Int): Int {
            val step = STEP_TABLE[index]
            var diff = sample - predictor
            var nibble = 0
            if (diff < 0) {
                nibble = 8
                diff = -diff
            }
            var delta = step shr 3
            if (diff >= step) { nibble = nibble or 4; diff -= step; delta += step }
            if (diff >= step shr 1) { nibble = nibble or 2; diff -= step shr 1; delta += step shr 1 }
            if (diff >= step shr 2) { nibble = nibble or 1; delta += step shr 2 }
            predictor = (if (nibble and 8 != 0) predictor - delta else predictor + delta).coerceIn(-32768, 32767)
            index = (index + INDEX_TABLE[nibble]).coerceIn(0, 88)
            return nibble
        }
    }

    fun decode(packet: ByteArray, sampleCount: Int): ShortArray {
        var predictor = ((packet[0].toInt() shl 8) or (packet[1].toInt() and 0xFF)).toShort().toInt()
        var index = (packet[2].toInt() and 0xFF).coerceIn(0, 88)
        val out = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val byte = packet.getOrElse(3 + i / 2) { 0 }.toInt()
            val nibble = if (i % 2 == 0) byte and 0x0F else (byte shr 4) and 0x0F
            val step = STEP_TABLE[index]
            var delta = step shr 3
            if (nibble and 4 != 0) delta += step
            if (nibble and 2 != 0) delta += step shr 1
            if (nibble and 1 != 0) delta += step shr 2
            predictor = (if (nibble and 8 != 0) predictor - delta else predictor + delta).coerceIn(-32768, 32767)
            index = (index + INDEX_TABLE[nibble]).coerceIn(0, 88)
            out[i] = predictor.toShort()
        }
        return out
    }
}
