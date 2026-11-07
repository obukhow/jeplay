package com.shilapi.xcertplay.media

import android.graphics.Bitmap
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.media.VDEventMedia
import com.desaysv.ivi.vdb.event.id.media.VDValueMedia
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaExtInfo
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaItem
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlay
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlayTime
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class DesayMediaOutputTest {
    @Test
    fun `publishes carplay source info status and time`() {
        val events = mutableListOf<VDEvent>()
        val output = DesayMediaOutput { events += it; true }

        output.publish(
            info = CarPlayNowPlaying(
                title = "Racer Material",
                artist = "SKY RAE",
                album = "Singles",
                durationMillis = 180_000,
                elapsedMillis = 12_000,
                playing = true,
            ),
            artwork = null,
            artworkKey = null,
            mediaAudioActive = true,
        )

        assertTrue(events.any { it.id == VDEventMedia.CURRENT_SOURCE_TYPE })
        assertTrue(events.any { it.id == VDEventMedia.MEDIA_SOURCE_EXT_INFO })
        assertTrue(events.any { it.id == VDEventMedia.MEDIA_SOURCE_INFO })
        assertTrue(events.any { it.id == VDEventMedia.MEDIA_SOURCE_PLAY_STATUS })
        assertTrue(events.any { it.id == VDEventMedia.MEDIA_SOURCE_PLAY_TIME })

        val source = events.first { it.id == VDEventMedia.CURRENT_SOURCE_TYPE }
        val type = source.payload!!.getParcelable<VDMediaType>("type")!!
        assertEquals(VDValueMedia.MediaType.CARPLAY, type.mediaType)

        val ext = events.first { it.id == VDEventMedia.MEDIA_SOURCE_EXT_INFO }
            .payload!!.getParcelable<VDMediaExtInfo>("info")!!
        assertEquals(VDValueMedia.MediaConnectState.CONNECTED_SEARCHED, ext.connectState)

        val infoEvent = events.first { it.id == VDEventMedia.MEDIA_SOURCE_INFO }
        val item = infoEvent.payload!!.getParcelable<VDMediaItem>("info")!!
        assertEquals(VDValueMedia.MediaType.CARPLAY, item.mediaType)
        val info = item.mediaInfo
        assertNotNull(info)
        assertEquals("Racer Material", info!!.title)
        assertEquals("SKY RAE", info.artist)
        assertEquals("Singles", info.album)

        val statusEvent = events.first { it.id == VDEventMedia.MEDIA_SOURCE_PLAY_STATUS }
        val play = statusEvent.payload!!.getParcelable<VDMediaPlay>("status")!!
        assertEquals(VDValueMedia.MediaPlayStatus.PLAYING, play.playStatus)

        val timeEvent = events.first { it.id == VDEventMedia.MEDIA_SOURCE_PLAY_TIME }
        val time = timeEvent.payload!!.getParcelable<VDMediaPlayTime>("value")!!
        assertEquals(12_000, time.position)
        assertEquals(180_000, time.duration)
    }

    @Test
    fun `artwork arrives later republishes info with buffer`() {
        val events = mutableListOf<VDEvent>()
        val output = DesayMediaOutput { events += it; true }
        val track = CarPlayNowPlaying(title = "A", artist = "B", artworkTransferId = 7, playing = true)

        output.publish(track, artwork = null, artworkKey = 7, mediaAudioActive = true)
        events.clear()

        val art = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        output.publish(track, artwork = art, artworkKey = 7, mediaAudioActive = true)

        val infoEvent = events.first { it.id == VDEventMedia.MEDIA_SOURCE_INFO }
        val item = infoEvent.payload!!.getParcelable<VDMediaItem>("info")!!
        assertTrue((item.mediaInfo?.albumBuffer?.size ?: 0) > 0)
        art.recycle()
    }

    @Test
    fun `position-only update skips info republish`() {
        val events = mutableListOf<VDEvent>()
        val output = DesayMediaOutput { events += it; true }
        val base = CarPlayNowPlaying(title = "A", artist = "B", elapsedMillis = 1_000, playing = true)
        output.publish(base, null, null, true)
        events.clear()

        output.publish(base.copy(elapsedMillis = 2_000), null, null, true)
        assertTrue(events.none { it.id == VDEventMedia.MEDIA_SOURCE_INFO })
        assertTrue(events.any { it.id == VDEventMedia.MEDIA_SOURCE_PLAY_TIME })
    }
}
