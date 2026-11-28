package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.Intent
import android.util.Log
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDDistNaviContent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviLaneInfo
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviRoadInfo

/**
 * Publishes CarPlay turn guidance to Jaecoo/Desay HUD over CarLan VDB
 * (events 721701 / 721708 / 721698), plus AutoNavi standard broadcast as a fallback
 * many Desay clusters also listen to.
 */
internal class DesayHudOutput(
    private val client: DesayVdbClient = DesayVdbClient(),
) {
    private var lastStatus: Int? = null
    private var lastIcon: Int? = null
    private var lastDistance: Int? = null
    private var lastRoadName: String? = null
    private var lastNextRoadName: String? = null
    private var lastLaneKey: String? = null
    var active: Boolean = false
        private set

    fun initialize(context: Context) = client.initialize(context)

    fun available(context: Context? = null): Boolean = client.available(context)

    fun update(context: Context?, icon: Int, distanceMeters: Int, road: String) =
        update(context, Guidance(icon, distanceMeters, road, road))

    fun update(context: Context?, guidance: Guidance) {
        val distance = guidance.distanceMeters.coerceAtLeast(0)
        if (active &&
            lastIcon == guidance.icon &&
            lastDistance == distance &&
            lastRoadName == guidance.roadName &&
            lastNextRoadName == guidance.nextRoadName
        ) {
            return
        }
        active = true
        lastIcon = guidance.icon
        lastDistance = distance
        lastRoadName = guidance.roadName
        lastNextRoadName = guidance.nextRoadName
        setStatus(VDNaviDigitalInfo.STATUS_GUIDING)
        publishDistance(distance)
        publishRoad(guidance.icon, distance, guidance.roadName, guidance.nextRoadName)
        broadcastAutonavi(context, guidance)
    }

    fun updateLanes(guidance: BydLaneGuidance?) {
        val key = guidance?.lanes?.joinToString(";") { lane ->
            "${lane.index}:${lane.status}:${lane.highlightedDegrees}:${lane.anglesDegrees.joinToString(",")}"
        } ?: ""
        if (lastLaneKey == key) return
        lastLaneKey = key
        publishLanes(guidance)
    }

    data class Guidance(
        val icon: Int,
        val distanceMeters: Int,
        val roadName: String,
        val nextRoadName: String,
    )

    fun stop(context: Context?) {
        if (!active && lastStatus == VDNaviDigitalInfo.STATUS_IDLE && lastLaneKey == "") return
        active = false
        lastIcon = null
        lastDistance = null
        lastRoadName = null
        lastNextRoadName = null
        lastLaneKey = null
        setStatus(VDNaviDigitalInfo.STATUS_IDLE)
        publishDistance(0)
        publishRoad(0, 0, "", "")
        publishLanes(null)
        lastLaneKey = ""
        broadcastAutonavi(context, Guidance(0, 0, "", ""))
    }

    private fun setStatus(status: Int) {
        if (lastStatus == status) return
        lastStatus = status
        val info = VDNaviDigitalInfo()
        info.setNaviStatus(status)
        publish(VDNaviDigitalInfo.createEvent(info))
    }

    private fun publishDistance(meters: Int) {
        val info = VDDistNaviContent()
        info.setDistNaviContent(meters)
        publish(VDDistNaviContent.createEvent(info))
    }

    private fun publishRoad(icon: Int, meters: Int, roadName: String, nextRoadName: String) {
        val info = VDNaviRoadInfo()
        info.setSegRemainDis(meters)
        info.setRoadIcon(icon)
        info.setRoadType(-1) // OEM path always sends -1
        info.setRoadName(roadName)
        info.setNextRoadName(nextRoadName)
        publish(VDNaviRoadInfo.createEvent(info))
    }

    private fun publishLanes(guidance: BydLaneGuidance?) {
        val info = VDNaviLaneInfo()
        val lanes = ArrayList<VDNaviLaneInfo.LaneInfo>()
        if (guidance == null || guidance.lanes.isEmpty()) {
            val clear = VDNaviLaneInfo.LaneInfo()
            clear.setLaneInfo(0)
            clear.setLaneId(0)
            clear.setLaneIconId(0)
            lanes.add(clear)
        } else {
            // Desay lane-strip encoding: every visible slot gets LaneInfo=1;
            // white vs gray is baked into LaneIconId via highlight||dirs (table a3.y).
            guidance.lanes.forEachIndexed { i, lane ->
                val available = lane.anglesDegrees.map(DesayLaneIcons::direction)
                val highlight = when {
                    lane.status == 0 -> null
                    lane.highlightedDegrees != null -> DesayLaneIcons.direction(lane.highlightedDegrees)
                    available.isNotEmpty() ->
                        available.firstOrNull { it != DesayLaneIcons.STRAIGHT } ?: available.first()
                    else -> null
                }
                val row = VDNaviLaneInfo.LaneInfo()
                row.setLaneInfo(1)
                row.setLaneId(i)
                row.setLaneIconId(DesayLaneIcons.iconId(highlight, available))
                lanes.add(row)
            }
        }
        info.setRoadInfo(lanes)
        publish(VDNaviLaneInfo.createEvent(info))
    }

    private fun publish(event: VDEvent) {
        if (!client.publish(event)) {
            Log.d(TAG, "VDB not ready for id=${event.id}")
        }
    }

    /**
     * AutoNavi / Gaode cluster broadcast (also used on some Desay "Lion" builds).
     * Harmless if nothing listens; helps when CarLan bind is delayed.
     */
    private fun broadcastAutonavi(context: Context?, guidance: Guidance) {
        val ctx = context ?: return
        try {
            val intent = Intent(AUTONAVI_ACTION)
                .putExtra("KEY_TYPE", 10001)
                .putExtra("ICON", guidance.icon)
                .putExtra("SEG_REMAIN_DIS", guidance.distanceMeters)
                .putExtra("NEXT_ROAD_NAME", guidance.nextRoadName)
                .putExtra("ROUTE_REMAIN_DIS", guidance.distanceMeters)
            if (guidance.roadName.isNotEmpty()) intent.putExtra("ROAD_NAME", guidance.roadName)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.d(TAG, "autonavi broadcast: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DiPlay-DesayHud"
        private const val AUTONAVI_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND"
    }
}
