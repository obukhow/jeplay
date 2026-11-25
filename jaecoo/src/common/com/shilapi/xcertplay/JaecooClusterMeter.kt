package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import com.shilapi.xcertplay.hud.JaecooSettings

/**
 * Desay tells the cluster which map slot is showing. The instrument does not know about CarPlay.
 * YaSwitcher answers that for Yandex by moving the navi window into the well rect.
 *
 * Two fits:
 * - **Resize** — shrink the Presentation to the well (optionally grown by scale) and advertise that
 *   size as stream 111. Well ↔ full reconnects so `/info` picks up the new aspect.
 * - **Shift** — keep the full-panel stream and slide the **map content** so the car marker lands on
 *   the well centre. The Presentation window stays fullscreen: WindowManager will not place a
 *   full-size window at x>0 on the cluster display. No reconnect; full screen clears the offset.
 *
 * On a 1920×720 Jaecoo cluster the well is the rect YaSwitcher sends as
 * `show_cluster?x=1300&y=0&width=500&height=620`. Other panel sizes keep that same fraction.
 */
internal object JaecooClusterMeter {
    const val ACTION = "desay.broadcast.map.meter.interaction"
    const val EXTRA_MAP_AREA = "map_area"
    const val EXTRA_MAP_STATUS = "map_status"
    const val EXTRA_CLOSE_ALL = "close_all_variants"
    const val SHIFT_PROBE_MS = 20_000L

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
    private val probeHandler = Handler(Looper.getMainLooper())
    @Volatile private var probeWellUntilElapsed = 0L
    private val endProbe = Runnable {
        probeWellUntilElapsed = 0L
        presentations.toList().forEach { apply(it) }
        onChange?.invoke()
        Log.i(ClusterMapPresentation.TAG, "well shift probe ended")
    }

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
        probeHandler.removeCallbacks(endProbe)
        probeWellUntilElapsed = 0L
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

    fun probingWell(): Boolean = SystemClock.elapsedRealtime() < probeWellUntilElapsed

    /**
     * Settings probe: force the well + shift fit for [durationMs] while the instrument still shows
     * the full panel, so the driver can see whether the map slides without waiting for Desay.
     */
    fun probeShiftWell(context: Context, durationMs: Long = SHIFT_PROBE_MS): String {
        appContext = context.applicationContext
        probeHandler.removeCallbacks(endProbe)
        probeWellUntilElapsed = SystemClock.elapsedRealtime() + durationMs
        presentations.toList().forEach { apply(it) }
        onChange?.invoke()
        probeHandler.postDelayed(endProbe, durationMs)
        val size = presentations.firstOrNull()?.let { ClusterMapPresentation.sizeOf(it.display) }
        val panelW = size?.x?.takeIf { it > 0 } ?: REFERENCE_WIDTH
        val panelH = size?.y?.takeIf { it > 0 } ?: REFERENCE_HEIGHT
        val offset = shiftFrame(
            panelW,
            panelH,
            AirPlayPersistence.loadClusterMarkerXPercent(context),
            AirPlayPersistence.loadClusterMarkerYPercent(context),
            JaecooSettings.clusterWellShiftXPercent(context),
            JaecooSettings.clusterWellShiftYPercent(context),
        )
        Log.i(ClusterMapPresentation.TAG, "well shift probe ${durationMs}ms offset=${offset.x},${offset.y} panel=${panelW}x${panelH}")
        return "shift probe ${durationMs / 1000}s · offset ${offset.x},${offset.y}"
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
        effectiveLayout(context) == Layout.WELL

    private fun effectiveLayout(context: Context): Layout =
        if (probingWell()) Layout.WELL
        else shown(AirPlayPersistence.loadClusterSmallWindowMode(context), layout)

    /** True only for resize fit: stream 111 must advertise the well size. */
    fun resizesStream(context: Context): Boolean =
        showsWell(context) && !probingWell() &&
            JaecooSettings.clusterWellFit(context) == JaecooSettings.CLUSTER_WELL_FIT_RESIZE

    fun frame(
        layout: Layout,
        panelWidth: Int,
        panelHeight: Int,
        centerXPercent: Int = DEFAULT_CENTER_X_PERCENT,
        centerYPercent: Int = DEFAULT_CENTER_Y_PERCENT,
        scalePercent: Int = JaecooSettings.CLUSTER_WELL_SCALE_DEFAULT,
        fit: Int = JaecooSettings.CLUSTER_WELL_FIT_RESIZE,
        markerXPercent: Int = 50,
        markerYPercent: Int = 45,
        shiftXPercent: Int = JaecooSettings.CLUSTER_WELL_SHIFT_DEFAULT,
        shiftYPercent: Int = JaecooSettings.CLUSTER_WELL_SHIFT_DEFAULT,
    ): Frame {
        if (layout != Layout.WELL || panelWidth <= 0 || panelHeight <= 0) {
            return Frame(0, 0, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Gravity.CENTER, layout != Layout.HIDDEN && panelWidth > 0 && panelHeight > 0)
        }
        return if (fit == JaecooSettings.CLUSTER_WELL_FIT_SHIFT) {
            shiftFrame(panelWidth, panelHeight, markerXPercent, markerYPercent, shiftXPercent, shiftYPercent)
        } else {
            resizeFrame(panelWidth, panelHeight, centerXPercent, centerYPercent, scalePercent)
        }
    }

    /** Shrink/grow a well-sized window centred on the hole (resize fit). */
    private fun resizeFrame(
        panelWidth: Int,
        panelHeight: Int,
        centerXPercent: Int,
        centerYPercent: Int,
        scalePercent: Int,
    ): Frame {
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

    /**
     * Keep the full-panel window and slide it so the car marker (at [markerXPercent]/[markerYPercent]
     * of the panel) lands on the well centre. [shiftXPercent]/[shiftYPercent] at 50 add nothing;
     * each step away from 50 is 1 % of the panel as fine-tune.
     */
    fun shiftFrame(
        panelWidth: Int,
        panelHeight: Int,
        markerXPercent: Int,
        markerYPercent: Int,
        shiftXPercent: Int = JaecooSettings.CLUSTER_WELL_SHIFT_DEFAULT,
        shiftYPercent: Int = JaecooSettings.CLUSTER_WELL_SHIFT_DEFAULT,
    ): Frame {
        val wellCenterX = (WELL_X + WELL_WIDTH / 2) * panelWidth / REFERENCE_WIDTH
        val wellCenterY = (WELL_Y + WELL_HEIGHT / 2) * panelHeight / REFERENCE_HEIGHT
        val markerX = panelWidth * markerXPercent.coerceIn(5, 95) / 100
        val markerY = panelHeight * markerYPercent.coerceIn(5, 95) / 100
        val fineX = panelWidth * (shiftXPercent.coerceIn(5, 95) - 50) / 100
        val fineY = panelHeight * (shiftYPercent.coerceIn(5, 95) - 50) / 100
        return Frame(
            wellCenterX - markerX + fineX,
            wellCenterY - markerY + fineY,
            panelWidth,
            panelHeight,
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
        val current = effectiveLayout(context)
        val size = ClusterMapPresentation.sizeOf(presentation.display)
        // Probe always uses shift so the driver sees the slide without switching fit or Desay.
        val fit = if (probingWell()) JaecooSettings.CLUSTER_WELL_FIT_SHIFT
        else JaecooSettings.clusterWellFit(context)
        val target = frame(
            current,
            size.x,
            size.y,
            AirPlayPersistence.loadClusterSmallWindowMarkerXPercent(context),
            AirPlayPersistence.loadClusterSmallWindowMarkerYPercent(context),
            JaecooSettings.clusterWellScale(context),
            fit,
            AirPlayPersistence.loadClusterMarkerXPercent(context),
            AirPlayPersistence.loadClusterMarkerYPercent(context),
            JaecooSettings.clusterWellShiftXPercent(context),
            JaecooSettings.clusterWellShiftYPercent(context),
        )
        if (current == Layout.WELL && fit == JaecooSettings.CLUSTER_WELL_FIT_SHIFT) {
            // Keep the Presentation fullscreen; slide map pixels. WM clamps a full-size window to x=0.
            apply(
                presentation.window,
                Frame(
                    0,
                    0,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                    true,
                ),
            )
            presentation.setContentOffset(target.x, target.y)
            Log.i(
                ClusterMapPresentation.TAG,
                "well shift content offset=${target.x},${target.y} panel=${size.x}x${size.y} probe=${probingWell()}",
            )
        } else {
            presentation.setContentOffset(0, 0)
            apply(presentation.window, target)
        }
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
