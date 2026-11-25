package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import com.shilapi.xcertplay.hud.JaecooSettings

/**
 * Desay tells the cluster which map slot is showing. The instrument does not know about CarPlay.
 * YaSwitcher answers that for Yandex by moving the navi window into the well rect. CarPlay's
 * cluster stream keeps the car at the centre of its frame, so this receiver places that frame on
 * the YaSwitcher well. Scale 100% makes the window exactly that well; below 100% grows it
 * (`well / scale`) so the on-screen hole stays 500×620 while more map fits inside.
 *
 * The advertised stream 111 size follows the well frame (not the full 1920×720 panel), otherwise
 * the iPhone keeps encoding an ultrawide map that Android then squashes into ~500×620. Switching
 * well ↔ full reconnects so `/info` picks up the new aspect.
 *
 * On a 1920×720 Jaecoo cluster the well is the rect YaSwitcher sends as
 * `show_cluster?x=1300&y=0&width=500&height=620`. Other panel sizes keep that same fraction.
 */
internal object JaecooClusterMeter {
    const val ACTION = "desay.broadcast.map.meter.interaction"
    const val EXTRA_MAP_AREA = "map_area"
    const val EXTRA_MAP_STATUS = "map_status"
    const val EXTRA_CLOSE_ALL = "close_all_variants"

    /** Reference panel the well rect was measured on. */
    const val REFERENCE_WIDTH = 1920
    const val REFERENCE_HEIGHT = 720
    const val WELL_X = 1300
    const val WELL_Y = 0
    const val WELL_WIDTH = 500
    const val WELL_HEIGHT = 620

    /** Well centre as percent of the reference panel — the small-window slider defaults. */
    const val DEFAULT_CENTER_X_PERCENT = 81
    const val DEFAULT_CENTER_Y_PERCENT = 43

    enum class Layout { FULL, WELL, HIDDEN }

    data class Frame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val gravity: Int,
        val visible: Boolean,
    )

    var layout: Layout? = null
        private set

    private var appContext: Context? = null
    private var onChange: (() -> Unit)? = null
    private var registered = false
    private val presentations = ArrayList<ClusterMapPresentation>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val area = intent.getIntExtra(EXTRA_MAP_AREA, 1)
            val status = intent.getIntExtra(EXTRA_MAP_STATUS, 0)
            val closeAll = intent.getBooleanExtra(EXTRA_CLOSE_ALL, false)
            val next = layoutOf(area, status, closeAll)
            Log.i(ClusterMapPresentation.TAG, "meter map_area=$area map_status=$status close_all=$closeAll -> $next")
            if (next == layout) return
            layout = next
            presentations.toList().forEach { apply(it) }
            onChange?.invoke()
        }
    }

    fun start(context: Context, onChange: () -> Unit) {
        this.onChange = onChange
        val app = context.applicationContext
        appContext = app
        if (registered) return
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun stop() {
        val app = appContext
        if (registered && app != null) {
            runCatching { app.unregisterReceiver(receiver) }
        }
        registered = false
        appContext = null
        onChange = null
        presentations.clear()
        layout = null
    }

    fun attach(presentation: ClusterMapPresentation) {
        if (presentation !in presentations) presentations.add(presentation)
        apply(presentation)
    }

    fun detach(presentation: ClusterMapPresentation) {
        presentations.remove(presentation)
    }

    /** Full cluster map is `map_status == 1` and `map_area == 1`. Anything else still showing is the well. */
    fun layoutOf(mapArea: Int, mapStatus: Int, closeAll: Boolean): Layout = when {
        closeAll -> Layout.HIDDEN
        mapStatus == 1 && mapArea == 1 -> Layout.FULL
        else -> Layout.WELL
    }

    /**
     * Off keeps the frame full-screen. On holds it in the well. Auto follows the last cluster event,
     * and stays full-screen until the first one arrives.
     */
    fun shown(mode: Int, reported: Layout?): Layout = when (mode) {
        1 -> if (reported == Layout.HIDDEN) Layout.HIDDEN else Layout.WELL
        2 -> reported ?: Layout.FULL
        else -> Layout.FULL
    }

    fun showsWell(context: Context): Boolean =
        shown(AirPlayPersistence.loadClusterSmallWindowMode(context), layout) == Layout.WELL

    fun frame(
        layout: Layout,
        panelWidth: Int,
        panelHeight: Int,
        centerXPercent: Int = DEFAULT_CENTER_X_PERCENT,
        centerYPercent: Int = DEFAULT_CENTER_Y_PERCENT,
        scalePercent: Int = JaecooSettings.CLUSTER_WELL_SCALE_DEFAULT,
    ): Frame {
        if (layout != Layout.WELL || panelWidth <= 0 || panelHeight <= 0) {
            return Frame(0, 0, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Gravity.CENTER, layout != Layout.HIDDEN && panelWidth > 0 && panelHeight > 0)
        }
        // On-screen well hole is always ~500×620. Scale 100 → window == well.
        // Scale 85 → window = well/0.85, centred on the well; the hole crops more map.
        val scale = scalePercent.coerceIn(JaecooSettings.CLUSTER_WELL_SCALE_MIN, JaecooSettings.CLUSTER_WELL_SCALE_MAX)
        val wellWidth = (WELL_WIDTH * panelWidth / REFERENCE_WIDTH).coerceAtLeast(1)
        val wellHeight = (WELL_HEIGHT * panelHeight / REFERENCE_HEIGHT).coerceAtLeast(1)
        val width = (wellWidth * 100 / scale).coerceAtLeast(wellWidth)
        val height = (wellHeight * 100 / scale).coerceAtLeast(wellHeight)
        val centerX = panelWidth * centerXPercent.coerceIn(5, 95) / 100
        val centerY = panelHeight * centerYPercent.coerceIn(5, 95) / 100
        return Frame(
            centerX - width / 2,
            centerY - height / 2,
            width,
            height,
            Gravity.TOP or Gravity.LEFT,
            true,
        )
    }

    /** Settings changed. The open cluster window picks up the new centre and size immediately. */
    fun relayout(context: Context) {
        appContext = context.applicationContext
        presentations.toList().forEach { apply(it) }
    }

    fun apply(presentation: ClusterMapPresentation) {
        val context = appContext ?: presentation.context
        val current = shown(AirPlayPersistence.loadClusterSmallWindowMode(context), layout)
        val size = ClusterMapPresentation.sizeOf(presentation.display)
        apply(presentation.window, frame(
            current,
            size.x,
            size.y,
            AirPlayPersistence.loadClusterSmallWindowMarkerXPercent(context),
            AirPlayPersistence.loadClusterSmallWindowMarkerYPercent(context),
            JaecooSettings.clusterWellScale(context),
        ))
        presentation.setMapVisible(current != Layout.HIDDEN)
    }

    fun apply(window: Window?, frame: Frame) {
        if (window == null) return
        val params = window.attributes ?: return
        params.x = frame.x
        params.y = frame.y
        params.gravity = frame.gravity
        window.attributes = params
        window.setLayout(frame.width, frame.height)
        val updated = window.attributes ?: return
        updated.x = frame.x
        updated.y = frame.y
        updated.gravity = frame.gravity
        window.attributes = updated
    }
}
