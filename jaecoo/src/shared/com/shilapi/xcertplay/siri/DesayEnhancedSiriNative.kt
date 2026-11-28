package com.shilapi.xcertplay.siri

import android.content.Context
import android.os.Build
import android.util.Log
import com.desaysv.enhancesiri.EnhanceSiriController
import java.io.File

/**
 * Loads Desay Enhanced Siri native libraries for Jaecoo.
 *
 * Order:
 * 1. APK `jniLibs` via [System.loadLibrary] (when packaged under
 *    `jaecoo/jni/enhanced-siri/`).
 * 2. Absolute paths on the head unit / leftover OEM CarPlay install.
 */
object DesayEnhancedSiriNative {
    private const val TAG = "JePlay-EnhanceSiri"

    /** Primary JNI entry; depends on the wake-word / iFlytek stack below. */
    private const val JNI_LIB = "enhancesiri_jni"

    /**
     * Load order matters: dependencies before [JNI_LIB]. Names match OEM
     * libNAME.so from Desay CarPlay (without the lib prefix or .so suffix).
     */
    private val DEPENDENCY_LIBS = listOf(
        "issauth",
        "issmvw",
        "SpWord",
        "w_ivw",
        "w_ivw_proton",
        "wake_word_evaluate",
    )

    /** Where OEM models live on Jaecoo / Desay firmware. */
    val MODEL_PATHS = listOf(
        "/product/etc/Res/MVWRes/FirstRes",
        "/data_extra/carplay/Res/MVWRes/FirstRes",
        "/system/etc/Res/MVWRes/FirstRes",
    )

    /** Places to look for unpackaged native libraries on the head unit. */
    private val LIB_SEARCH_DIRS = listOf(
        "/product/lib64",
        "/system/lib64",
        "/vendor/lib64",
        "/system/priv-app/DesaySVCarPlay/lib/arm64",
        "/system/app/DesaySVCarPlay/lib/arm64",
        "/data_extra/carplay/lib",
    )

    @Volatile
    private var loaded: Boolean? = null

    @Volatile
    private var loadDetail: String = "not attempted"

    fun loadDetail(): String = loadDetail

    fun isLoaded(): Boolean = loaded == true

    /** First existing OEM model directory, or null. */
    fun findModelPath(): String? = MODEL_PATHS.firstOrNull { File(it).isDirectory }

    fun modelsAvailable(): Boolean = findModelPath() != null

    /**
     * Loads natives once. Safe to call from any thread; returns whether
     * [EnhanceSiriController] JNI methods may be used.
     */
    @Synchronized
    fun ensureLoaded(context: Context? = null): Boolean {
        loaded?.let { return it }
        if (!EnhanceSiriController.shouldLoadNatives()) {
            loaded = false
            loadDetail = "skipped on ${Build.FINGERPRINT}"
            return false
        }
        val nativeDir = context?.applicationInfo?.nativeLibraryDir
        val ok = runCatching {
            for (name in DEPENDENCY_LIBS) {
                loadOne(name, nativeDir)
            }
            loadOne(JNI_LIB, nativeDir)
            true
        }.onFailure {
            Log.w(TAG, "native load failed: ${it.message}")
            loadDetail = "failed: ${it.message}"
        }.getOrDefault(false)
        loaded = ok
        if (ok) {
            loadDetail = "loaded nativeDir=${nativeDir ?: "none"} models=${findModelPath() ?: "missing"}"
            Log.i(TAG, loadDetail)
        }
        return ok
    }

    private fun loadOne(name: String, nativeDir: String?) {
        // Prefer the APK copy when present.
        if (nativeDir != null) {
            val packaged = File(nativeDir, "lib$name.so")
            if (packaged.isFile) {
                System.load(packaged.absolutePath)
                Log.i(TAG, "loaded ${packaged.absolutePath}")
                return
            }
        }
        try {
            System.loadLibrary(name)
            Log.i(TAG, "loadLibrary($name)")
            return
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to absolute paths on the head unit.
        }
        for (dir in LIB_SEARCH_DIRS) {
            val file = File(dir, "lib$name.so")
            if (file.isFile) {
                System.load(file.absolutePath)
                Log.i(TAG, "loaded ${file.absolutePath}")
                return
            }
        }
        // Soft deps (w_ivw*) may be absent on some builds; only the JNI entry is fatal.
        if (name == JNI_LIB) {
            throw UnsatisfiedLinkError("lib$name.so not found in APK or $LIB_SEARCH_DIRS")
        }
        Log.i(TAG, "optional lib$name.so missing; continuing")
    }
}
