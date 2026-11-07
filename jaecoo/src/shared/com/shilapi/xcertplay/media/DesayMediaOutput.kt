package com.shilapi.xcertplay.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.media.VDEventMedia
import com.desaysv.ivi.vdb.event.id.media.VDValueMedia
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaExtInfo
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaInfo
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaItem
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlay
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaPlayTime
import com.desaysv.ivi.vdb.event.id.media.bean.VDMediaType
import com.shilapi.xcertplay.hud.DesayVdbClient
import java.io.ByteArrayOutputStream

/**
 * Publishes CarPlay now-playing to Desay Media VDB so the home media card can show it.
 *
 * OEM path: PhoneLink → [VDMediaItem] wrapping [VDMediaInfo] → MediaService **publish**.
 * Third-party clients cannot `set(MEDIA_SOURCE_INFO)` (rejected as "can not be setting");
 * [DesayVdbClient] injects via `id=MIN` + `InjectEventID`.
 *
 * Yandex Music uses [MediaBrowserService] for UMA — a different card. The Desay launcher
 * [BigMediaCard] reads VDB type 18 (CARPLAY) only.
 */
class DesayMediaOutput {
    private val client: DesayVdbClient

    constructor() {
        client = DesayVdbClient.media()
    }

    /** Test / injected publisher — keeps [DesayVdbClient] out of the public API. */
    internal constructor(publishHook: (VDEvent) -> Boolean) {
        client = DesayVdbClient.media(publishHook)
    }

    private var claimedSource = false
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastAlbum: String? = null
    private var lastArtworkKey: Int? = null
    private var lastPlayStatus: Int? = null
    private var lastPosition: Int? = null
    private var lastDuration: Int? = null
    private var lastArtworkJpeg: ByteArray? = null
    private var active = false

    fun initialize(context: Context) = client.initialize(context)

    fun available(context: Context? = null): Boolean = client.available(context)

    fun publish(
        info: CarPlayNowPlaying,
        artwork: Bitmap?,
        artworkKey: Int?,
        mediaAudioActive: Boolean,
    ) {
        val hasTrack = !info.title.isNullOrBlank() || !info.artist.isNullOrBlank() ||
            !info.album.isNullOrBlank() || mediaAudioActive
        if (!hasTrack) {
            clear()
            return
        }
        active = true
        claimSource()
        val keyChanged = artworkKey != lastArtworkKey
        val artArrived = artwork != null && !artwork.isRecycled && lastArtworkJpeg == null
        if (keyChanged || artArrived) {
            lastArtworkKey = artworkKey
            lastArtworkJpeg = encodeArtwork(artwork)
        }
        val metaChanged = info.title != lastTitle || info.artist != lastArtist ||
            info.album != lastAlbum || keyChanged || artArrived
        if (metaChanged) {
            lastTitle = info.title
            lastArtist = info.artist
            lastAlbum = info.album
            publishInfo(info, lastArtworkJpeg)
        }
        val resolved = when {
            info.playing -> VDValueMedia.MediaPlayStatus.PLAYING
            info.title != null || info.artist != null || info.album != null ->
                VDValueMedia.MediaPlayStatus.PAUSED
            mediaAudioActive -> VDValueMedia.MediaPlayStatus.PLAYING
            else -> VDValueMedia.MediaPlayStatus.STOPPED
        }
        if (resolved != lastPlayStatus) {
            lastPlayStatus = resolved
            publishStatus(resolved)
        }
        val position = (info.elapsedMillis ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val duration = (info.durationMillis ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        if (position != lastPosition || duration != lastDuration || metaChanged) {
            lastPosition = position
            lastDuration = duration
            publishTime(position, duration)
        }
    }

    fun clear() {
        if (!active && !claimedSource && lastPlayStatus == null) return
        active = false
        lastTitle = null
        lastArtist = null
        lastAlbum = null
        lastArtworkKey = null
        lastArtworkJpeg = null
        lastPosition = null
        lastDuration = null
        publishInfo(CarPlayNowPlaying(), null)
        lastPlayStatus = VDValueMedia.MediaPlayStatus.STOPPED
        publishStatus(VDValueMedia.MediaPlayStatus.STOPPED)
        publishTime(0, 0)
        publishExtInfo(connected = false)
        claimedSource = false
    }

    private fun claimSource() {
        if (claimedSource) return
        claimedSource = true
        val type = VDMediaType()
        type.putMediaType(VDValueMedia.MediaType.CARPLAY)
        publish(VDMediaType.createEvent(VDEventMedia.CURRENT_SOURCE_TYPE, type))
        publishExtInfo(connected = true)
        Log.i(TAG, "claimed CURRENT_SOURCE_TYPE=CARPLAY(18) + EXT_INFO connected")
    }

    private fun publishExtInfo(connected: Boolean) {
        val ext = VDMediaExtInfo()
        ext.putMediaType(VDValueMedia.MediaType.CARPLAY)
        ext.putConnectState(
            if (connected) {
                VDValueMedia.MediaConnectState.CONNECTED_SEARCHED
            } else {
                VDValueMedia.MediaConnectState.DISCONNECTED
            },
        )
        publish(VDMediaExtInfo.createEvent(VDEventMedia.MEDIA_SOURCE_EXT_INFO, ext))
    }

    private fun publishInfo(info: CarPlayNowPlaying, artworkJpeg: ByteArray?) {
        val mediaInfo = VDMediaInfo()
        mediaInfo.putMediaType(VDValueMedia.MediaType.CARPLAY)
        mediaInfo.putCollectStatus(VDValueMedia.MediaCollectStatus.WITHOUT_COLLECT)
        mediaInfo.putTitle(info.title.orEmpty())
        mediaInfo.putArtist(info.artist.orEmpty())
        mediaInfo.putAlbum(info.album.orEmpty())
        if (artworkJpeg != null && artworkJpeg.isNotEmpty()) {
            mediaInfo.setAlbumBuffer(artworkJpeg)
        }
        val item = VDMediaItem()
        item.putMediaType(VDValueMedia.MediaType.CARPLAY)
        item.putMediaInfo(mediaInfo)
        publish(VDMediaItem.createEvent(VDEventMedia.MEDIA_SOURCE_INFO, item))
        Log.i(
            TAG,
            "MEDIA_SOURCE_INFO VDMediaItem type=18 title=${info.title} artist=${info.artist} " +
                "artBytes=${artworkJpeg?.size ?: 0}",
        )
    }

    private fun publishStatus(status: Int) {
        val play = VDMediaPlay()
        play.putMediaType(VDValueMedia.MediaType.CARPLAY)
        play.putPlayStatus(status)
        publish(VDMediaPlay.createEvent(VDEventMedia.MEDIA_SOURCE_PLAY_STATUS, play))
        Log.i(TAG, "MEDIA_SOURCE_PLAY_STATUS type=18 status=$status")
    }

    private fun publishTime(position: Int, duration: Int) {
        val time = VDMediaPlayTime()
        time.putMediaType(VDValueMedia.MediaType.CARPLAY)
        time.putPosition(position)
        time.putDuration(duration)
        publish(VDMediaPlayTime.createEvent(VDEventMedia.MEDIA_SOURCE_PLAY_TIME, time))
    }

    private fun publish(event: VDEvent) {
        if (!client.publish(event)) {
            Log.i(TAG, "VDB media not ready for id=${event.id}")
        }
    }

    companion object {
        private const val TAG = "DiPlay-DesayMedia"
        private const val MAX_ART_SIDE = 320
        private const val MAX_ART_BYTES = 120_000
        /** OEM PhoneLinkAdapter recompresses CarPlay covers at JPEG quality 30. */
        private const val JPEG_QUALITY = 30

        internal fun encodeArtwork(bitmap: Bitmap?): ByteArray? {
            if (bitmap == null || bitmap.isRecycled) return null
            val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_ART_SIDE) {
                val scale = MAX_ART_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
                val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }
            return try {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                val bytes = out.toByteArray()
                if (bytes.size > MAX_ART_BYTES) null else bytes
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        }
    }
}
