package com.nqobile.chirpynami.core.call

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Live voice for an offline call: mic -> IMA-ADPCM -> link, and link -> jitter buffer -> speaker.
 *
 * - 8 kHz mono, 40 ms packets (25 packets/s, ~33 kbps on the wire incl. headers).
 * - Uses the VOICE_COMMUNICATION source so the phone's own echo canceller is engaged.
 * - Jitter buffer pre-fills 120 ms, then plays strictly in sequence order. A missing packet is
 *   concealed by replaying the previous one at reduced volume (up to 80 ms), then silence.
 *   The buffer is capped so latency cannot creep up during a long call.
 */
class ChirpyAudioEngine(
    context: Context,
    private val onEncodedPacket: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "ChirpyAudio"
        const val SAMPLE_RATE = 8000
        const val FRAME_SAMPLES = 320 // 40 ms
        private const val PREBUFFER_FRAMES = 3
        private const val MAX_BUFFER_FRAMES = 12
        private const val MAX_CONCEAL_FRAMES = 2
    }

    data class Stats(
        val sentBytes: Long,
        val receivedBytes: Long,
        val packetsPlayed: Long,
        val packetsConcealed: Long,
        val micAvailable: Boolean
    )

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val previousMode = audioManager.mode

    @Volatile private var running = false
    @Volatile var muted = false
    @Volatile private var micAvailable = true

    private val jitter = TreeMap<Int, ShortArray>()
    private var nextPlaySeq = -1
    private var prefilled = false

    private val sentBytes = AtomicLong()
    private val receivedBytes = AtomicLong()
    private val played = AtomicLong()
    private val concealed = AtomicLong()

    var speakerOn: Boolean = true
        set(value) {
            field = value
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = value
        }

    fun start() {
        if (running) return
        running = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        speakerOn = speakerOn
        thread(name = "chirpy-mic", isDaemon = true) { captureLoop() }
        thread(name = "chirpy-playout", isDaemon = true) { playoutLoop() }
    }

    fun stop() {
        running = false
        audioManager.mode = previousMode
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
    }

    fun stats() = Stats(sentBytes.get(), receivedBytes.get(), played.get(), concealed.get(), micAvailable)

    /** Feed one decrypted packet from the peer. */
    fun onPacket(seq: Int, encoded: ByteArray) {
        receivedBytes.addAndGet(encoded.size.toLong())
        val pcm = ImaAdpcmCodec.decode(encoded, FRAME_SAMPLES)
        synchronized(jitter) {
            if (prefilled && seq < nextPlaySeq) return // arrived too late, already concealed
            jitter[seq] = pcm
            while (jitter.size > MAX_BUFFER_FRAMES) {
                jitter.pollFirstEntry()
                nextPlaySeq = jitter.firstKey()
            }
        }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested at launch; failure is handled below.
    private fun captureLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SAMPLES * 2 * 4)
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord unavailable", e)
            null
        }
        micAvailable = record != null

        record?.audioSessionId?.let { session ->
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(session)?.enabled = true
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(session)?.enabled = true
        }

        val encoder = ImaAdpcmCodec.Encoder()
        val frame = ShortArray(FRAME_SAMPLES)
        record?.startRecording()
        try {
            while (running) {
                if (record != null) {
                    var read = 0
                    while (read < FRAME_SAMPLES && running) {
                        val n = record.read(frame, read, FRAME_SAMPLES - read)
                        if (n <= 0) break
                        read += n
                    }
                } else {
                    Thread.sleep(40) // keep the stream (and the peer's jitter buffer) ticking
                }
                if (muted || record == null) frame.fill(0)
                val packet = encoder.encode(frame)
                sentBytes.addAndGet(packet.size.toLong())
                onEncodedPacket(packet)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Capture stopped", e)
        } finally {
            record?.stop()
            record?.release()
        }
    }

    private fun playoutLoop() {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, FRAME_SAMPLES * 2 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val silence = ShortArray(FRAME_SAMPLES)
        var last: ShortArray? = null
        var concealRun = 0
        track.play()
        try {
            while (running) {
                val next: ShortArray? = synchronized(jitter) {
                    if (!prefilled) {
                        if (jitter.size >= PREBUFFER_FRAMES) {
                            prefilled = true
                            nextPlaySeq = jitter.firstKey()
                        } else {
                            return@synchronized null
                        }
                    }
                    jitter.remove(nextPlaySeq).also { nextPlaySeq++ }
                }

                val out = when {
                    next != null -> { concealRun = 0; last = next; played.incrementAndGet(); next }
                    !prefilled -> silence
                    concealRun < MAX_CONCEAL_FRAMES && last != null -> {
                        concealRun++
                        concealed.incrementAndGet()
                        val gain = 0.5f / concealRun
                        ShortArray(FRAME_SAMPLES) { (last!![it] * gain).toInt().toShort() }
                    }
                    else -> {
                        concealed.incrementAndGet()
                        // A long gap (peer stalled, link hiccup): re-prefill so we resync to whatever arrives next.
                        if (++concealRun > 10) synchronized(jitter) { prefilled = false; jitter.clear() }
                        silence
                    }
                }
                track.write(out, 0, out.size) // blocking write paces the loop at real time
            }
        } catch (e: Exception) {
            Log.w(TAG, "Playout stopped", e)
        } finally {
            track.stop()
            track.release()
        }
    }
}
