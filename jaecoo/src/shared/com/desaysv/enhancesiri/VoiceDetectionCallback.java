package com.desaysv.enhancesiri;

/**
 * JNI callback from Desay {@code libenhancesiri_jni.so} when the OEM wake-word
 * engine hears "Hey Siri" / the configured keyword.
 *
 * <p>Package and method name must stay exactly as the OEM library expects.
 */
public interface VoiceDetectionCallback {
    /**
     * @param sampleIndex PCM sample index of the detection, passed to CarPlay
     *                    {@code requestSiri} with {@code siriAction = 4}.
     */
    void onVoiceDetectionMode(long sampleIndex);
}
