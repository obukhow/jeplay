package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class DesayAudioAttributesTest {
    @Test
    fun navigationIsUsageSpeechWithoutStreamOrSource() {
        val attrs = DesayAudioAttributes.navigation()
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, attrs.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attrs.contentType)
        assertEquals(-1, DesayAudioAttributes.readSourceId(attrs))
        val legacy = DesayAudioAttributes.readLegacyStreamType(attrs)
        assertTrue("legacy=$legacy", legacy <= 0)
    }

    @Test
    fun mediaTagsUsageMusic() {
        val attrs = DesayAudioAttributes.media()
        assertEquals(AudioAttributes.USAGE_MEDIA, attrs.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, attrs.contentType)
        assertEquals(-1, DesayAudioAttributes.readSourceId(attrs))
        val legacy = DesayAudioAttributes.readLegacyStreamType(attrs)
        assertTrue("legacy=$legacy", legacy == -1 || legacy == DesayAudioAttributes.STREAM_MEDIA)
    }

    @Test
    fun withNavigationSourceStripsVendorStream() {
        val media = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        assertSame(media, DesayAudioAttributes.withNavigationSource(media))

        val nav = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        assertSame(nav, DesayAudioAttributes.withNavigationSource(nav))

        val withStream = AudioAttributes.Builder()
            .setLegacyStreamType(DesayAudioAttributes.STREAM_NAVIGATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val cleaned = DesayAudioAttributes.withNavigationSource(withStream)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, cleaned.usage)
        assertEquals(-1, DesayAudioAttributes.readSourceId(cleaned))
        val legacy = DesayAudioAttributes.readLegacyStreamType(cleaned)
        assertTrue("legacy=$legacy", legacy <= 0)
    }

    @Test
    fun withMediaStreamKeepsMediaUsage() {
        val nav = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        assertSame(nav, DesayAudioAttributes.withMediaStream(nav))

        val media = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val tagged = DesayAudioAttributes.withMediaStream(media)
        assertEquals(AudioAttributes.USAGE_MEDIA, tagged.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_MUSIC, tagged.contentType)
        val legacy = DesayAudioAttributes.readLegacyStreamType(tagged)
        assertTrue("legacy=$legacy", legacy == -1 || legacy == DesayAudioAttributes.STREAM_MEDIA)
    }
}
