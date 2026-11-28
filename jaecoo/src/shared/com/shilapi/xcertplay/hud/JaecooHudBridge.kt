package com.shilapi.xcertplay.hud

import android.content.Context
import android.util.Log
import com.desaysv.ivi.vdb.event.VDEvent
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CarPlay [CPManeuver] / iAP2 route guidance → Jaecoo J8 HUD/cluster via Desay CarLan VDB
 * (events 721701 / 721698 / 721708 / 721697) — not FCE.
 */
internal object JaecooHudBridge {
    private const val TAG = "DiPlay-Jaecoo"
    private val lock = Any()
    private val route = BydHudRouteState()
    private val lanes = BydLaneGuidanceState()
    private val navigation = FceNavigationState()
    private var output = DesayHudOutput()
    private var context: Context? = null
    private var tickStarted = false
    private var logged = false
    private var loggedLanes = false
    private var loggedRoad = false

    fun available(): Boolean = synchronized(lock) {
        output.available(context)
    }

    fun initialize(appContext: Context) = synchronized(lock) {
        val app = appContext.applicationContext
        if (context == null) {
            Log.i(TAG, "Jaecoo Desay VDB HUD ready (carlan=${DesayVdbClient().available(app)})")
        }
        context = app
        output.initialize(app)
        JaecooClusterNaviPresence.start(app)
        if (!tickStarted) {
            tickStarted = true
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "diplay-jaecoo-hud").apply { isDaemon = true }
            }.scheduleAtFixedRate(::tick, 1, 1, TimeUnit.SECONDS)
        }
        refreshLocked()
    }

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        if (context == null) return@synchronized
        route.accept(frame.messageId, frame.payload)
        navigation.accept(frame.messageId, frame.payload)
        val laneChanged = lanes.accept(frame.messageId, frame.payload)
        if (laneChanged) {
            val strip = lanes.current()
            Log.i(
                TAG,
                "Lane state msg=0x${frame.messageId.toString(16)} " +
                    "showingStrip=${strip != null} laneCount=${strip?.lanes?.size ?: -1}",
            )
            if (!loggedLanes && strip != null && strip.lanes.isNotEmpty()) {
                loggedLanes = true
                Log.i(TAG, "Lane guidance active lanes=${strip.lanes.size}")
            }
        }
        refreshLocked()
    }

    fun clear() = synchronized(lock) {
        route.clear()
        lanes.clear()
        navigation.clear()
        output.stop(context)
        JaecooClusterNaviPresence.stop()
    }

    /**
     * Jaecoo no longer writes FCE properties; stock FCE bridge may run if present.
     * Kept for call-site compatibility with [FceClusterBridge].
     */
    fun ownsFce(): Boolean = false

    private fun tick() = synchronized(lock) { if (context != null) refreshLocked() }

    private fun refreshLocked() {
        val app = context ?: return
        if (!JaecooSettings.hud(app) || !output.available(app)) {
            if (output.active) output.stop(app)
            return
        }
        val apple = route.currentApple()
        when {
            apple != null -> {
                // RoadIcon = next-turn arrow (maneuver). Lane strip is separate (0x5204).
                // If the strip is empty, the HUD only shows that turn arrow — that is not
                // "lanes mapped from maneuvers", it means CarPlay lane events are missing/hidden.
                val icon = DesayManeuverCodes.icon(apple.type, apple.drivingSide)
                val strip = lanes.current()
                val laneCount = strip?.lanes?.size ?: 0
                // BydLaneGuidanceState.current() returns empty list when iOS hides the strip.
                val laneShowing = strip != null && strip.lanes.isNotEmpty()
                output.update(app, icon, apple.distanceMeters, apple.road)
                output.updateLanes(strip)
                val laneIcons = strip?.lanes?.joinToString(",") { lane ->
                    val dirs = lane.anglesDegrees.map(DesayLaneIcons::direction)
                    val hi = when {
                        lane.status == 0 -> null
                        lane.highlightedDegrees != null -> DesayLaneIcons.direction(lane.highlightedDegrees)
                        else -> dirs.firstOrNull { it != DesayLaneIcons.STRAIGHT } ?: dirs.firstOrNull()
                    }
                    "${DesayLaneIcons.iconId(hi, dirs)}@${lane.status}"
                }.orEmpty()
                JaecooCarPlayHudDiag.onHudPublish(
                    apple.type, icon, apple.distanceMeters, apple.road, laneCount, laneShowing, laneIcons,
                )
                if (!logged && output.active) {
                    logged = true
                    Log.i(
                        TAG,
                        "HUD guidance sent type=${apple.type} icon=$icon " +
                            "distance=${apple.distanceMeters}m road='${apple.road}' " +
                            "lanes=$laneCount",
                    )
                }
                if (!loggedRoad && apple.road.isNotEmpty()) {
                    loggedRoad = true
                    Log.i(TAG, "HUD street name road='${apple.road}'")
                }
            }
            navigation.active -> {
                output.update(app, DesayManeuverCodes.NONE, 0, "")
                output.updateLanes(lanes.current())
            }
            else -> output.stop(app)
        }
    }

    /** Test hook: swap publisher and optional app context. */
    internal fun resetForTest(publisher: (VDEvent) -> Boolean, appContext: Context?) = synchronized(lock) {
        route.clear()
        lanes.clear()
        navigation.clear()
        output = DesayHudOutput(DesayVdbClient(publisher))
        context = appContext
        if (appContext != null) output.initialize(appContext)
        JaecooClusterNaviPresence.resetForTest(publisher, appContext)
        logged = false
        loggedLanes = false
        loggedRoad = false
        JaecooCarPlayHudDiag.reset()
    }
}
