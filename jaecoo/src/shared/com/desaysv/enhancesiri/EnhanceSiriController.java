package com.desaysv.enhancesiri;

import android.os.Build;
import android.util.Log;

/**
 * Thin Java mirror of the OEM Desay Enhanced Siri JNI class.
 *
 * <p>Native symbols are {@code Java_com_desaysv_enhancesiri_EnhanceSiriController_*},
 * so this class name and package must not change. Load {@code enhancesiri_jni}
 * (and its iFlytek dependencies) before constructing an instance.
 */
public class EnhanceSiriController {
    private static final String TAG = "JePlay-EnhanceSiri";

    private final VoiceDetectionCallback voiceDetectionCallback;
    private boolean nativesReady;

    public EnhanceSiriController(VoiceDetectionCallback voiceDetectionCallback) {
        this.voiceDetectionCallback = voiceDetectionCallback;
    }

    /** Marks the JNI library as already loaded by [DesayEnhancedSiriNative]. */
    public void markNativesReady() {
        nativesReady = true;
    }

    public boolean nativesReady() {
        return nativesReady;
    }

    private native void nativeInit(VoiceDetectionCallback voiceDetectionCallback);

    private native void nativeInputData(byte[] data, int length);

    private native void nativeStartEnhancedSiri(String language, int mode);

    private native void nativeStartEnhancedSiriWithResPath(String language, int mode, String resPath);

    private native void nativeStopEnhancedSiri();

    public void init() {
        if (!nativesReady) {
            Log.w(TAG, "init skipped: natives not loaded");
            return;
        }
        nativeInit(voiceDetectionCallback);
    }

    public void inputData(byte[] data, int length) {
        if (!nativesReady || data == null || length <= 0) return;
        nativeInputData(data, length);
    }

    public void startEnhancedSiri(String language, int mode, String resPath) {
        if (!nativesReady) {
            Log.w(TAG, "start skipped: natives not loaded");
            return;
        }
        nativeStartEnhancedSiriWithResPath(language, mode, resPath);
    }

    public void startEnhancedSiri(String language, int mode) {
        if (!nativesReady) {
            Log.w(TAG, "start skipped: natives not loaded");
            return;
        }
        nativeStartEnhancedSiri(language, mode);
    }

    public void stopEnhancedSiri() {
        if (!nativesReady) return;
        nativeStopEnhancedSiri();
    }

    /** Robolectric / host unit tests never load the car-only .so files. */
    public static boolean shouldLoadNatives() {
        return !"robolectric".equals(Build.FINGERPRINT);
    }
}
