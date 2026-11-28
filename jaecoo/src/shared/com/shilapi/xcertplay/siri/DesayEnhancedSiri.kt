package com.shilapi.xcertplay.siri

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.desaysv.enhancesiri.EnhanceSiriController
import com.desaysv.enhancesiri.VoiceDetectionCallback
import com.shilapi.xcertplay.hud.JaecooSettings
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Jaecoo continuous "Hey Siri" via the OEM Desay Enhanced Siri stack.
 *
 * While CarPlay is attached and the setting is on: capture PCM, feed
 * [EnhanceSiriController.inputData], and on wake-word fire
 * [onVoiceActivation] (CarPlay `siriAction = 4` + sample index).
 *
 * Models stay on the head unit ([DesayEnhancedSiriNative.MODEL_PATHS]);
 * natives come from the APK jniLibs folder or OEM system paths.
 */
object DesayEnhancedSiri {
    private const val TAG = "JePlay-EnhanceSiri"

    /** OEM [CommonType.VoiceActivationMode.KEYWORD]. */
    const val MODE_KEYWORD = 1

    /** iFlytek MVW expects 16 kHz mono PCM16 — same as Desay AUXIN wake path. */
    private const val SAMPLE_RATE = 16_000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val FRAME_MILLIS = 20
    private const val RESTART_AFTER_DETECT_MILLIS = 20_000L

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val capturing = AtomicBoolean(false)

    private var app: Context? = null
    private var onVoiceActivation: ((Long) -> Boolean)? = null
    private var controller: EnhanceSiriController? = null
    private var recordThread: Thread? = null
    private var engineRunning = false
    private var attached = false

    private val detectionCallback = VoiceDetectionCallback { sampleIndex ->
        Log.i(TAG, "wake word sampleIndex=$sampleIndex")
        val callback = synchronized(lock) { onVoiceActivation }
        val sent = runCatching { callback?.invoke(sampleIndex) == true }.getOrDefault(false)
        Log.i(TAG, "voice activation sent=$sent sampleIndex=$sampleIndex")
        // Free the mic so CarPlay speech-recognition uplink can open.
        handler.post {
            synchronized(lock) { stopEngineLocked(restartLater = true) }
        }
    }

    fun available(context: Context): Boolean {
        DesayEnhancedSiriNative.ensureLoaded(context.applicationContext)
        return DesayEnhancedSiriNative.isLoaded() && DesayEnhancedSiriNative.modelsAvailable()
    }

    fun statusLine(context: Context): String {
        DesayEnhancedSiriNative.ensureLoaded(context.applicationContext)
        val models = DesayEnhancedSiriNative.findModelPath() ?: "missing"
        return "natives=${DesayEnhancedSiriNative.loadDetail()}; models=$models"
    }

    /**
     * Starts listening when CarPlay is attached and the setting is on.
     * Safe to call repeatedly; [onVoiceActivation] may run on the capture thread.
     */
    fun start(context: Context, onVoiceActivation: (Long) -> Boolean) {
        synchronized(lock) {
            this.onVoiceActivation = onVoiceActivation
            app = context.applicationContext
            attached = true
            if (!JaecooSettings.enhancedSiri(context.applicationContext)) {
                Log.i(TAG, "setting off; not listening")
                stopEngineLocked(restartLater = false)
                return
            }
            startEngineLocked()
        }
    }

    /** Stops listening when CarPlay ends. */
    fun stop() {
        synchronized(lock) {
            attached = false
            onVoiceActivation = null
            handler.removeCallbacksAndMessages(null)
            stopEngineLocked(restartLater = false)
            app = null
        }
    }

    /** Re-reads the setting; call after the toggle changes while CarPlay is up. */
    fun applySetting(context: Context) {
        synchronized(lock) {
            app = context.applicationContext
            if (!attached) return
            if (JaecooSettings.enhancedSiri(context.applicationContext)) {
                startEngineLocked()
            } else {
                stopEngineLocked(restartLater = false)
            }
        }
    }

    private fun startEngineLocked() {
        val ctx = app ?: return
        if (engineRunning) return
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO missing")
            return
        }
        if (!DesayEnhancedSiriNative.ensureLoaded(ctx)) {
            Log.w(TAG, "natives unavailable: ${DesayEnhancedSiriNative.loadDetail()}")
            return
        }
        val modelPath = DesayEnhancedSiriNative.findModelPath()
        if (modelPath == null) {
            Log.w(TAG, "MVW models missing at ${DesayEnhancedSiriNative.MODEL_PATHS}")
            return
        }
        val next = controller ?: EnhanceSiriController(detectionCallback).also {
            it.markNativesReady()
            it.init()
            controller = it
        }
        val language = wakeLanguage(ctx)
        runCatching {
            next.startEnhancedSiri(language, MODE_KEYWORD, modelPath)
        }.onFailure {
            Log.w(TAG, "startEnhancedSiri failed", it)
            return
        }
        engineRunning = true
        startCaptureLocked(next)
        Log.i(TAG, "listening language=$language models=$modelPath")
    }

    private fun stopEngineLocked(restartLater: Boolean) {
        capturing.set(false)
        recordThread = null
        val c = controller
        if (engineRunning && c != null) {
            runCatching { c.stopEnhancedSiri() }
                .onFailure { Log.w(TAG, "stopEnhancedSiri failed", it) }
        }
        engineRunning = false
        if (restartLater && attached) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                synchronized(lock) {
                    if (attached && JaecooSettings.enhancedSiri(app ?: return@synchronized)) {
                        startEngineLocked()
                    }
                }
            }, RESTART_AFTER_DETECT_MILLIS)
        }
    }

    private fun startCaptureLocked(engine: EnhanceSiriController) {
        if (capturing.getAndSet(true)) return
        val frameBytes = SAMPLE_RATE * FRAME_MILLIS / 1000 * 2 // mono PCM16
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord unavailable minBuffer=$minBuffer")
            capturing.set(false)
            return
        }
        val bufferSize = maxOf(minBuffer * 2, frameBytes * 4)
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                ENCODING,
                bufferSize,
            )
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord init failed")
            recorder?.release()
            capturing.set(false)
            return
        }
        val thread = Thread({
            val buffer = ByteArray(frameBytes)
            try {
                recorder.startRecording()
                while (capturing.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        engine.inputData(buffer, read)
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error=$read")
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "capture ended: ${t.message}")
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                capturing.set(false)
            }
        }, "jeplay-enhanced-siri")
        recordThread = thread
        thread.start()
    }

    /** Prefer Russian models on ru locales; OEM also ships en-US / zh-CN. */
    internal fun wakeLanguage(context: Context): String {
        val lang = context.resources.configuration.locales[0]?.language
            ?: Locale.getDefault().language
        return when (lang) {
            "ru" -> "ru-RU"
            "zh" -> "zh-CN"
            else -> "en-US"
        }
    }
}
