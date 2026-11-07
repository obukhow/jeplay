package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle

/**
 * Desay / Jaecoo AudioAttributes for CarPlay on Jaecoo.
 *
 * **Media** needs OEM legacy stream **60** on the attributes: plain [USAGE_MEDIA] is granted
 * then immediately lost and the iPhone pauses within a second.
 *
 * **Navigation** must NOT use OEM stream 62 / `key_source_id` from a third-party UID. adb probes
 * showed headrest only for Builder+[USAGE_ASSISTANCE_NAVIGATION_GUIDANCE] + prefer
 * `BUS02_NAV_GUIDANCE` + nav focus. Stream 62 (attrs or AudioTrack ctor) and `key_source_id=38`
 * made guidance audible on cabin speakers instead. Pin the track with
 * [android.media.AudioTrack.setPreferredDevice] BUS02; keep content as CarPlay sends it (SPEECH).
 *
 * [AudioAttributes.Builder.addBundle] / [AudioAttributes.getBundle] exist from API 29 but are
 * missing from some compile stubs, so they are reached reflectively when reading.
 */
object DesayAudioAttributes {
    const val KEY_SOURCE_ID = "key_source_id"

    /**
     * Source id reported on focus-grant callbacks for CarPlay alert/nav. Never written into our
     * attributes — it forced cabin routing for JePlay.
     */
    const val SOURCE_NAVIGATION = 38

    /**
     * OEM legacy stream for CarPlay media (accessory source type 0). Not a public Android
     * STREAM_* constant; Desay's audio policy recognizes it on [AudioAttributes].
     */
    const val STREAM_MEDIA = 60

    /** OEM privileged nav stream — do not apply from JePlay; see class KDoc. */
    const val STREAM_NAVIGATION = 62

    /** Headrest path verified by probe T3: usage nav, no legacy stream, no source id. */
    fun navigation(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    fun media(): AudioAttributes =
        AudioAttributes.Builder()
            .setLegacyStreamType(STREAM_MEDIA)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    /**
     * Ensures nav usage without vendor stream 62 / source id. Leaves an already-clean nav
     * attribute alone (including CarPlay's SPEECH content type).
     */
    fun withNavigationSource(base: AudioAttributes): AudioAttributes {
        if (base.usage != AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) return base
        if (readLegacyStreamType(base) <= 0 && readSourceId(base) < 0) return base
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(
                if (base.contentType != AudioAttributes.CONTENT_TYPE_UNKNOWN) base.contentType
                else AudioAttributes.CONTENT_TYPE_SPEECH,
            )
            .build()
    }

    /** Tags media attributes with OEM legacy stream 60; leaves non-media usages alone. */
    fun withMediaStream(base: AudioAttributes): AudioAttributes {
        if (base.usage != AudioAttributes.USAGE_MEDIA) return base
        if (readLegacyStreamType(base) == STREAM_MEDIA) return base
        return AudioAttributes.Builder(base)
            .setLegacyStreamType(STREAM_MEDIA)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    fun readSourceId(attributes: AudioAttributes): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        val bundle = runCatching {
            AudioAttributes::class.java.getMethod("getBundle").invoke(attributes) as? Bundle
        }.getOrNull() ?: return -1
        return bundle.getInt(KEY_SOURCE_ID, -1)
    }

    fun readLegacyStreamType(attributes: AudioAttributes): Int =
        runCatching {
            // Hidden on some stubs; OEM and framework both keep it on the attributes.
            AudioAttributes::class.java.getMethod("getLegacyStreamType").invoke(attributes) as Int
        }.getOrDefault(-1)
}
