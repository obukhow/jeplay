package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.compat.AudioFocusRequestCompat
import kotlin.math.sin

/**
 * adb-driven tone probe for Jaecoo speaker routing.
 *
 * ```
 * adb shell am broadcast -a ru.jeplay.app.PROBE_AUDIO -n ru.jeplay.app/com.shilapi.xcertplay.AudioRouteProbeReceiver \
 *   --es label T1 --ei stream 12 --ei usage 12 --ei content 1 --ei prefer_bus 0 --ei focus 1 --ei source_id -1 --ei ms 2500
 * ```
 *
 * [usage] 1=MEDIA 12=NAV_GUIDANCE. [content] 1=SPEECH 2=MUSIC.
 * [stream] &lt;0 → AudioTrack.Builder only; ≥0 → deprecated AudioTrack(streamType, …) ctor.
 */
class AudioRouteProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "probe"
        val stream = intent.getIntExtra(EXTRA_STREAM, -1)
        val usage = intent.getIntExtra(EXTRA_USAGE, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        val content = intent.getIntExtra(EXTRA_CONTENT, AudioAttributes.CONTENT_TYPE_SPEECH)
        val preferBus = intent.getIntExtra(EXTRA_PREFER_BUS, 0) == 1
        val focus = intent.getIntExtra(EXTRA_FOCUS, 1) == 1
        val sourceId = intent.getIntExtra(EXTRA_SOURCE_ID, -1)
        val dsp = intent.getIntExtra(EXTRA_DSP, 0) == 1
        val ms = intent.getIntExtra(EXTRA_MS, 2500).coerceIn(500, 8_000)
        val pending = goAsync()
        Thread(
            {
                try {
                    play(app, label, stream, usage, content, preferBus, focus, sourceId, dsp, ms)
                } finally {
                    pending.finish()
                }
            },
            "jeplay-audio-probe",
        ).start()
    }

    private fun play(
        context: Context,
        label: String,
        stream: Int,
        usage: Int,
        content: Int,
        preferBus: Boolean,
        focus: Boolean,
        sourceId: Int,
        dsp: Boolean,
        ms: Int,
    ) {
        val am = context.getSystemService(AudioManager::class.java)
        val rate = 16_000
        val attrs = buildAttrs(usage, content, stream, sourceId)
        if (dsp) {
            runCatching {
                com.shilapi.xcertplay.media.DesayDspNavChannel.setNavigationActive(true)
            }.onFailure { Log.w(TAG, "$label DSP arm failed: ${it.message}") }
        }
        var focusReq: AudioFocusRequestCompat? = null
        if (focus && am != null) {
            focusReq = AudioFocusRequestCompat(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                attrs,
                AudioManager.OnAudioFocusChangeListener { },
                Handler(Looper.getMainLooper()),
            )
            val granted = focusReq.request(am)
            Log.i(TAG, "$label focus granted=$granted")
        }
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val buffer = maxOf(min, rate / 5 * 2)
        val track = if (stream >= 0) {
            @Suppress("DEPRECATION")
            AudioTrack(stream, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer, AudioTrack.MODE_STREAM)
        } else {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(buffer)
                .build()
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "$label track NOT initialized stream=$stream")
            track.release()
            focusReq?.let { req -> am?.let { req.abandon(it) } }
            return
        }
        if (preferBus && am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bus = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.isSink && it.type == AudioDeviceInfo.TYPE_BUS && it.address == "BUS02_NAV_GUIDANCE"
            }
            val ok = bus?.let { track.setPreferredDevice(it) } ?: false
            Log.i(TAG, "$label prefer BUS02 ok=$ok found=${bus != null}")
        }
        val routed = runCatching { track.routedDevice?.address }.getOrNull() ?: "none"
        val legacy = runCatching {
            AudioAttributes::class.java.getMethod("getLegacyStreamType").invoke(attrs) as Int
        }.getOrDefault(-1)
        Log.i(
            TAG,
            "$label PLAY stream=$stream usage=$usage content=$content sourceId=$sourceId " +
                "preferBus=$preferBus focus=$focus dsp=$dsp legacyAttr=$legacy " +
                "routed=$routed state=${track.state}",
        )
        try {
            track.play()
            val pcm = tone(rate, ms)
            var off = 0
            while (off < pcm.size) {
                val n = track.write(pcm, off, minOf(4096, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            Thread.sleep(200)
            val routedAfter = runCatching { track.routedDevice?.address }.getOrNull() ?: "none"
            Log.i(TAG, "$label DONE routed=$routedAfter")
        } finally {
            runCatching { track.stop() }
            track.release()
            focusReq?.let { req -> am?.let { req.abandon(it) } }
            if (dsp) {
                runCatching {
                    com.shilapi.xcertplay.media.DesayDspNavChannel.setNavigationActive(false)
                }
            }
        }
    }

    private fun buildAttrs(usage: Int, content: Int, stream: Int, sourceId: Int): AudioAttributes {
        val builder = AudioAttributes.Builder()
        if (stream >= 0) {
            try {
                builder.setLegacyStreamType(stream)
            } catch (_: Exception) {
            }
        }
        builder.setUsage(usage).setContentType(content)
        val attrs = builder.build()
        if (sourceId < 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return attrs
        return runCatching {
            val withBundle = AudioAttributes.Builder(attrs)
            val bundle = Bundle().apply { putInt("key_source_id", sourceId) }
            val method = AudioAttributes.Builder::class.java.methods.firstOrNull {
                it.name == "addBundle" && it.parameterTypes.size == 1
            } ?: return attrs
            method.invoke(withBundle, bundle)
            withBundle.setUsage(usage).setContentType(content).build()
        }.getOrDefault(attrs)
    }

    private fun tone(rate: Int, ms: Int): ByteArray {
        val samples = rate * ms / 1000
        val hz = 880.0
        return ByteArray(samples * 2).also { pcm ->
            var o = 0
            for (i in 0 until samples) {
                val fade = minOf(1.0, i / 200.0, (samples - i) / 200.0)
                val s = (sin(2.0 * Math.PI * hz * i / rate) * fade * Short.MAX_VALUE * 0.9).toInt()
                pcm[o++] = s.toByte()
                pcm[o++] = (s ushr 8).toByte()
            }
        }
    }

    companion object {
        private const val TAG = "JePlay-AudioProbe"
        const val ACTION = "ru.jeplay.app.PROBE_AUDIO"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_STREAM = "stream"
        private const val EXTRA_USAGE = "usage"
        private const val EXTRA_CONTENT = "content"
        private const val EXTRA_PREFER_BUS = "prefer_bus"
        private const val EXTRA_FOCUS = "focus"
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val EXTRA_DSP = "dsp"
        private const val EXTRA_MS = "ms"
    }
}
