package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** Live cluster layout. Small-window navi keeps a permanent card from staying on the map. */
internal object ClusterMusicWindow {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var smallWindow = false

    fun smallWindow(): Boolean = smallWindow

    fun setSmallWindow(value: Boolean) {
        if (value == smallWindow) return
        smallWindow = value
        val notify = { listeners.toList().forEach { it() } }
        if (Looper.myLooper() == Looper.getMainLooper()) notify() else main.post(notify)
    }

    fun observe(listener: () -> Unit): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        listeners.add(listener)
        return { listeners.remove(listener) }
    }
}

/** Main-thread snapshots reuse the bounded media artwork cache; they never own/recycle its bitmap. */
internal object ClusterMusicState {
    data class Snapshot(val info: CarPlayNowPlaying = CarPlayNowPlaying(), val artwork: Bitmap? = null,
        val changedAt: Long = 0L, val revision: Long = 0L)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(Snapshot) -> Unit>()
    private var state = Snapshot()

    fun publish(info: CarPlayNowPlaying, artwork: Bitmap?) {
        main.post {
            val changed = trackChanged(state.info, info)
            state = Snapshot(info, artwork, if (changed) SystemClock.elapsedRealtime() else state.changedAt, state.revision + if (changed) 1 else 0)
            listeners.toList().forEach { it(state) }
        }
    }

    fun observe(listener: (Snapshot) -> Unit): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        listeners.add(listener)
        listener(state)
        return { listeners.remove(listener) }
    }

    fun trackChanged(old: CarPlayNowPlaying, next: CarPlayNowPlaying): Boolean =
        old.title != next.title || old.artist != next.artist || old.album != next.album ||
            old.sourceApp != next.sourceApp ||
            (old.durationMillis != null && next.durationMillis != null && old.durationMillis != next.durationMillis)
}
