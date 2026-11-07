package com.shilapi.xcertplay.hud

import android.content.Context
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDDistNaviContent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviLaneInfo
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviRoadInfo
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class JaecooHudBridgeTest {
    private val events = mutableListOf<VDEvent>()
    private lateinit var app: Context

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        JaecooSettings.setHud(app, true)
        JaecooHudBridge.resetForTest({ event -> events += event; true }, app)
        events.clear() // drop idle ticks from reset / previous tests
    }

    @After
    fun tearDown() = JaecooHudBridge.resetForTest({ true }, null)

    @Test
    fun `carplay left turn reaches Desay VDB events`() {
        maneuver(index = 2, type = 1)
        route(state = 1, distance = 178, current = 2)

        assertTrue(events.map { it.id }.containsAll(
            listOf(
                VDNaviDigitalInfo.EVENT_ID,
                VDDistNaviContent.EVENT_ID,
                VDNaviRoadInfo.EVENT_ID,
            ),
        ))

        @Suppress("DEPRECATION")
        val status = events.last { it.id == VDNaviDigitalInfo.EVENT_ID }
            .payload!!.getParcelable<VDNaviDigitalInfo>(VDNaviDigitalInfo.PAYLOAD_KEY)!!
        assertEquals(VDNaviDigitalInfo.STATUS_GUIDING, status.naviStatus)

        @Suppress("DEPRECATION")
        val dist = events.last { it.id == VDDistNaviContent.EVENT_ID }
            .payload!!.getParcelable<VDDistNaviContent>(VDDistNaviContent.PAYLOAD_KEY)!!
        assertEquals(178, dist.distNaviContent)

        @Suppress("DEPRECATION")
        val road = events.last { it.id == VDNaviRoadInfo.EVENT_ID }
            .payload!!.getParcelable<VDNaviRoadInfo>(VDNaviRoadInfo.PAYLOAD_KEY)!!
        assertEquals(DesayManeuverCodes.LEFT, road.roadIcon)
        assertEquals(178, road.segRemainDis)
        // Empty until 0x5202 carries AfterManeuverRoadName (TLV 0x04) or 0x5201 current road.
        assertFalse(JaecooHudBridge.ownsFce())
    }

    @Test
    fun `carplay right turn uses Desay right icon not BYD gaode`() {
        maneuver(index = 1, type = 2)
        route(state = 1, distance = 90, current = 1)

        @Suppress("DEPRECATION")
        val road = events.last { it.id == VDNaviRoadInfo.EVENT_ID }
            .payload!!.getParcelable<VDNaviRoadInfo>(VDNaviRoadInfo.PAYLOAD_KEY)!!
        // BYD Gaode RIGHT is 2 (= Desay LEFT); Desay RIGHT must be 3.
        assertEquals(DesayManeuverCodes.RIGHT, road.roadIcon)
        assertEquals(3, road.roadIcon)
    }

    @Test
    fun `desay maneuver table matches YaSwitcher vocabulary`() {
        assertEquals(2, DesayManeuverCodes.icon(1, 0)) // left
        assertEquals(3, DesayManeuverCodes.icon(2, 0)) // right
        assertEquals(9, DesayManeuverCodes.icon(3, 0)) // straight
        assertEquals(4, DesayManeuverCodes.icon(49, 0)) // slight left
        assertEquals(5, DesayManeuverCodes.icon(50, 0)) // slight right
        assertEquals(6, DesayManeuverCodes.icon(47, 0)) // hard left
        assertEquals(7, DesayManeuverCodes.icon(48, 0)) // hard right
        assertEquals(8, DesayManeuverCodes.icon(4, 0)) // uturn left (RHD)
        assertEquals(19, DesayManeuverCodes.icon(4, 1)) // uturn right (LHD)
        assertEquals(11, DesayManeuverCodes.icon(6, 0)) // enter roundabout
        assertEquals(12, DesayManeuverCodes.icon(7, 0)) // leave roundabout
        assertEquals(12, DesayManeuverCodes.icon(28, 0)) // numbered exit → leave
        assertEquals(15, DesayManeuverCodes.icon(10, 0)) // finish / destination
        assertEquals(0, DesayManeuverCodes.icon(99, 0))
    }

    @Test
    fun `carplay lane guidance reaches Desay lane event`() {
        maneuver(index = 1, type = 2)
        route(state = 1, distance = 90, current = 1)
        events.clear()

        // Two lanes: Apple left (-90°) preferred, straight+right not recommended.
        // Desay keys match Apple sense (no L↔R negate).
        val lane0 = tlvs(
            tlv(0x00, 0, 0),
            tlv(0x01, 2),
            tlv16Angles(0x02, -90),
            tlv16Angles(0x03, -90),
        )
        val lane1 = tlvs(
            tlv(0x00, 0, 1),
            tlv(0x01, 0),
            tlv16Angles(0x02, 0, 90),
        )
        JaecooHudBridge.onFrame(
            Iap2Frame(
                BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
                // 0x01 = lane-event index (not maneuver index); iOS precaches a window of these.
                tlvs(tlv(0x01, 0, 0), tlvBytes(0x02, tlvs(lane0, lane1))),
            ),
        )
        // 0x10 current lane-event index, 0x12 LaneGuidanceShowing=1
        route(state = 1, distance = 90, current = 1, laneEvent = 0, laneShowing = true)

        val laneEvents = events.filter { it.id == VDNaviLaneInfo.EVENT_ID }
        assertTrue(laneEvents.isNotEmpty())
        @Suppress("DEPRECATION")
        val info = laneEvents.last().payload!!.getParcelable<VDNaviLaneInfo>(VDNaviLaneInfo.PAYLOAD_KEY)!!
        assertEquals(2, info.roadInfo.size)
        // YaSwitcher: LaneInfo always 1 when showing; white/gray is in LaneIconId.
        assertEquals(1, info.roadInfo[0].laneInfo)
        assertEquals(64, info.roadInfo[0].laneIconId) // Apple left → LEFT90||LEFT90 (white)
        assertEquals(0, info.roadInfo[0].laneId)
        assertEquals(1, info.roadInfo[1].laneInfo)
        assertEquals(4, info.roadInfo[1].laneIconId) // ||STRAIGHT_AHEAD|RIGHT90 (gray)
        assertEquals(1, info.roadInfo[1].laneId)
    }

    @Test
    fun `disabled overlay writes nothing`() {
        JaecooSettings.setHud(app, false)
        events.clear()
        maneuver(index = 1, type = 2)
        route(state = 1, distance = 50, current = 1)

        assertEquals(emptyList<VDEvent>(), events)
    }

    @Test
    fun `lane icon map matches YaSwitcher patterns`() {
        assertEquals(64, DesayLaneIcons.iconId(DesayLaneIcons.LEFT90, listOf(DesayLaneIcons.LEFT90)))
        assertEquals(2, DesayLaneIcons.iconId(null, listOf(DesayLaneIcons.LEFT90, DesayLaneIcons.STRAIGHT)))
        assertEquals(0, DesayLaneIcons.iconId(null, listOf(DesayLaneIcons.STRAIGHT)))
        assertEquals(0, DesayLaneIcons.backgroundIconId(listOf(DesayLaneIcons.STRAIGHT)))
        assertEquals(3, DesayLaneIcons.backgroundIconId(listOf(DesayLaneIcons.RIGHT90)))
        assertEquals(DesayLaneIcons.STRAIGHT, DesayLaneIcons.simplify(DesayLaneIcons.LEFT45))
        assertEquals(DesayLaneIcons.LEFT180, DesayLaneIcons.simplify(DesayLaneIcons.LEFT135))
        // Apple sense preserved: -90 → LEFT90, +90 → RIGHT90.
        assertEquals(DesayLaneIcons.LEFT90, DesayLaneIcons.direction(-90))
        assertEquals(DesayLaneIcons.RIGHT90, DesayLaneIcons.direction(90))
    }

    @Test
    fun `three lanes publish background types with white on the recommended one`() {
        maneuver(index = 1, type = 1)
        route(state = 1, distance = 40, current = 1)
        events.clear()
        // left preferred, middle straight gray, right gray
        val lane0 = tlvs(tlv(0x00, 0, 0), tlv(0x01, 2), tlv16Angles(0x02, -90), tlv16Angles(0x03, -90))
        val lane1 = tlvs(tlv(0x00, 0, 1), tlv(0x01, 0), tlv16Angles(0x02, 0))
        val lane2 = tlvs(tlv(0x00, 0, 2), tlv(0x01, 0), tlv16Angles(0x02, 90))
        JaecooHudBridge.onFrame(
            Iap2Frame(
                BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
                tlvs(tlv(0x01, 0, 0), tlvBytes(0x02, tlvs(lane0, lane1, lane2))),
            ),
        )
        route(state = 1, distance = 40, current = 1, laneEvent = 0, laneShowing = true)
        @Suppress("DEPRECATION")
        val info = events.last { it.id == VDNaviLaneInfo.EVENT_ID }
            .payload!!.getParcelable<VDNaviLaneInfo>(VDNaviLaneInfo.PAYLOAD_KEY)!!
        assertEquals(3, info.roadInfo.size)
        assertEquals(listOf(1, 1, 1), info.roadInfo.map { it.laneInfo })
        // Apple left→LEFT90 white (64); straight gray (0); Apple right→RIGHT90 gray (3)
        assertEquals(listOf(64, 0, 3), info.roadInfo.map { it.laneIconId })
        assertEquals(listOf(0, 1, 2), info.roadInfo.map { it.laneId })
    }

    private fun maneuver(index: Int, type: Int, side: Int = 0) = JaecooHudBridge.onFrame(
        Iap2Frame(
            BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, index), tlv(0x03, type), tlv(0x08, side)),
        ),
    )

    private fun route(
        state: Int,
        distance: Int,
        current: Int? = null,
        laneEvent: Int? = null,
        laneShowing: Boolean? = null,
    ) = JaecooHudBridge.onFrame(
        Iap2Frame(
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(
                tlv(0x01, state),
                tlv(
                    0x0a,
                    distance ushr 24 and 0xff,
                    distance ushr 16 and 0xff,
                    distance ushr 8 and 0xff,
                    distance and 0xff,
                ),
                *listOfNotNull(
                    current?.let { tlv(0x0d, 0, it) },
                    laneEvent?.let { tlv(0x10, 0, it) },
                    laneShowing?.let { tlv(0x12, if (it) 1 else 0) },
                ).toTypedArray(),
            ),
        ),
    )

    private fun tlv(type: Int, vararg value: Int): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8)
        write(length)
        write(type ushr 8)
        write(type)
        value.forEach(::write)
    }.toByteArray()

    private fun tlv16Angles(type: Int, vararg angles: Int): ByteArray {
        val bytes = angles.flatMap { a ->
            val v = a and 0xffff
            listOf(v ushr 8, v and 0xff)
        }
        return tlv(type, *bytes.toIntArray())
    }

    private fun tlvBytes(type: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8)
        write(length)
        write(type ushr 8)
        write(type)
        write(value)
    }.toByteArray()

    private fun tlvs(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        values.forEach(::write)
    }.toByteArray()
}
