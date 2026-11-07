package com.shilapi.xcertplay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.compat.AudioFocusRequestCompat
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin

/**
 * Plays a short, easy-to-localize cue through the same route CarPlay navigation/media use.
 * Prefers the spoken [NAV_PCM] asset; falls back to three sharp beeps if it is missing.
 *
 * JAECOO: nav = USAGE_NAV + prefer BUS02 + focus (probe T3 → headrest). Media = stream 60.
 */
internal class AudioChannelPreview(
    context: Context,
    private val onUnavailable: (Int) -> Unit,
) : Closeable {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "diplay-channel-preview").apply { isDaemon = true }
    }
    private val generation = AtomicInteger()
    private val activeTrack = AtomicReference<AudioTrack?>()
    private var pending: Future<*>? = null
    @Volatile private var closed = false

    fun play(channel: Int, navigation: Boolean) {
        if (closed) return
        require(channel in AirPlayPersistence.AUDIO_CHANNELS)
        val request = generation.incrementAndGet()
        pending?.cancel(true)
        activeTrack.get()?.let { runCatching { it.stop() } }
        pending = worker.submit {
            var track: AudioTrack? = null
            var focus: AudioFocusRequestCompat? = null
            try {
                if (closed || generation.get() != request) return@submit
                val cue = loadCue()
                val minimum = AudioTrack.getMinBufferSize(
                    cue.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                )
                check(minimum > 0) { "No PCM output buffer is available" }
                val bufferBytes = maxOf(minimum, cue.sampleRate / 10 * 2)
                val attributes = if (navigation) {
                    com.shilapi.xcertplay.media.DesayAudioAttributes.navigation()
                } else {
                    com.shilapi.xcertplay.media.DesayAudioAttributes.media()
                }
                if (navigation && audioManager != null) {
                    focus = AudioFocusRequestCompat(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                        attributes,
                        AudioManager.OnAudioFocusChangeListener { },
                        mainHandler,
                    )
                    focus.request(audioManager)
                }
                val built = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(cue.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
                track = built
                check(built.state == AudioTrack.STATE_INITIALIZED) { "Audio output did not initialize" }
                if (navigation) preferNavGuidanceBus(built)
                if (closed || generation.get() != request) return@submit
                activeTrack.set(built)
                built.setVolume(1.0f)
                built.play()
                var written = 0
                while (written < cue.pcm.size && !closed && generation.get() == request) {
                    val count = built.write(
                        cue.pcm, written, minOf(4096, cue.pcm.size - written), AudioTrack.WRITE_BLOCKING,
                    )
                    check(count > 0) { "Could not write preview tone" }
                    written += count
                }
                if (!closed && generation.get() == request && written == cue.pcm.size) {
                    val routed = runCatching { built.routedDevice?.address }.getOrNull() ?: "none"
                    Log.i(
                        TAG,
                        "Preview started channel=$channel(ignored) navigation=$navigation " +
                            "cue=${cue.label} routed=$routed usage=${attributes.usage} " +
                            "content=${attributes.contentType}",
                    )
                }
                Thread.sleep(200L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                Log.w(TAG, "Channel preview unavailable channel=$channel", error)
                mainHandler.post {
                    if (!closed && generation.get() == request) onUnavailable(channel)
                }
            } finally {
                activeTrack.compareAndSet(track, null)
                track?.let { runCatching { it.stop() }; it.release() }
                focus?.let { req -> audioManager?.let { mgr -> runCatching { req.abandon(mgr) } } }
            }
        }
    }

    override fun close() {
        closed = true
        generation.incrementAndGet()
        pending?.cancel(true)
        activeTrack.get()?.let { runCatching { it.stop() } }
        worker.shutdownNow()
    }

    private fun preferNavGuidanceBus(track: AudioTrack) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || audioManager == null) return
        val bus = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { device ->
                device.isSink &&
                    device.type == AudioDeviceInfo.TYPE_BUS &&
                    device.address == NAV_GUIDANCE_BUS_ADDRESS
            }
        if (bus == null) {
            Log.w(TAG, "preview preferredDevice missing $NAV_GUIDANCE_BUS_ADDRESS")
            return
        }
        val ok = runCatching { track.setPreferredDevice(bus) }.getOrDefault(false)
        Log.i(TAG, "preview preferredDevice address=${bus.address} ok=$ok")
    }

    private fun loadCue(): Cue {
        runCatching {
            app.assets.open(NAV_PCM).use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { pcm ->
            return Cue(pcm, NAV_PCM_RATE, NAV_PCM)
        }
        return Cue(beeps(), BEEP_RATE, "beeps")
    }

    /** Three short rising beeps with gaps — easy to spot in a single headrest. */
    private fun beeps(): ByteArray {
        val rates = doubleArrayOf(1200.0, 1600.0, 2000.0)
        val beepSamples = BEEP_RATE * BEEP_MILLIS / 1000
        val gapSamples = BEEP_RATE * GAP_MILLIS / 1000
        val fadeSamples = BEEP_RATE / 200
        val total = rates.size * beepSamples + (rates.size - 1) * gapSamples
        return ByteArray(total * 2).also { pcm ->
            var offset = 0
            for ((beepIndex, hz) in rates.withIndex()) {
                for (i in 0 until beepSamples) {
                    val fade = minOf(1.0, i.toDouble() / fadeSamples,
                        (beepSamples - i - 1).toDouble() / fadeSamples).coerceAtLeast(0.0)
                    val sample = (sin(2.0 * Math.PI * hz * i / BEEP_RATE) *
                        fade * Short.MAX_VALUE * 0.85).toInt()
                    pcm[offset++] = sample.toByte()
                    pcm[offset++] = (sample ushr 8).toByte()
                }
                if (beepIndex < rates.lastIndex) {
                    offset += gapSamples * 2
                }
            }
        }
    }

    private data class Cue(val pcm: ByteArray, val sampleRate: Int, val label: String)

    private companion object {
        private const val TAG = "DiPlay-AudioChannelPreview"
        private const val NAV_GUIDANCE_BUS_ADDRESS = "BUS02_NAV_GUIDANCE"
        private const val NAV_PCM = "nav_preview.pcm"
        private const val NAV_PCM_RATE = 16_000
        private const val BEEP_RATE = 16_000
        private const val BEEP_MILLIS = 120
        private const val GAP_MILLIS = 80
    }
}
