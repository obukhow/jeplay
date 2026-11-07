package com.shilapi.xcertplay.hud

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BydLaneGuidanceStateTest {
    private val state = BydLaneGuidanceState()

    @Test
    fun `parses repeated 0x02 lane groups and repeated angle TLVs`() {
        // Real iAP2 wire: each LaneGuidanceInfo is its own 0x02 sibling; each LaneAngle
        // is its own 0x02 sibling inside the group (see DiPlay-20261104-222230-457.txt).
        val leftOnly = tlvs(
            tlv(0x00, 0, 0),
            tlv(0x01, 2),
            tlv16(0x02, -90),
            tlv16(0x03, -90),
        )
        val leftAndRight = tlvs(
            tlv(0x00, 0, 1),
            tlv(0x01, 2),
            tlv16(0x02, -90),
            tlv16(0x02, 90),
            tlv16(0x03, -90),
        )
        assertTrue(
            state.accept(
                BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
                tlvs(
                    tlv(0x01, 0, 3),
                    tlvBytes(0x02, leftOnly),
                    tlvBytes(0x02, leftAndRight),
                ),
            ),
        )
        // First non-empty 0x5204 is shown immediately (Yandex omits 0x12); Apple still
        // overrides via 0x10/0x12 when it sends a different current index.
        assertEquals(2, state.current()!!.lanes.size)

        state.accept(
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x10, 0, 3), tlv(0x12, 1)),
        )
        val lanes = state.current()!!.lanes
        assertEquals(2, lanes.size)
        assertEquals(0, lanes[0].index)
        assertEquals(2, lanes[0].status)
        assertEquals(listOf(-90), lanes[0].anglesDegrees)
        assertEquals(-90, lanes[0].highlightedDegrees)
        assertEquals(1, lanes[1].index)
        assertEquals(listOf(-90, 90), lanes[1].anglesDegrees)
        assertEquals(-90, lanes[1].highlightedDegrees)
    }

    @Test
    fun `keeps left lane when later 0x02 would previously overwrite`() {
        // Regression: last-wins on outer 0x02 dropped lane 0; last-wins on angle
        // dropped every angle except the final one.
        val lane0 = tlvs(tlv(0x00, 0, 0), tlv(0x01, 2), tlv16(0x02, 0), tlv16(0x02, -90))
        val lane1 = tlvs(tlv(0x00, 0, 1), tlv(0x01, 1), tlv16(0x02, 0), tlv16(0x02, 90))
        state.accept(
            BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 0, 0), tlvBytes(0x02, lane0), tlvBytes(0x02, lane1)),
        )
        val lanes = state.current()!!.lanes
        assertEquals(2, lanes.size)
        assertEquals(listOf(0, -90), lanes[0].anglesDegrees)
        assertEquals(listOf(0, 90), lanes[1].anglesDegrees)
    }

    @Test
    fun `selects current event from the precache window`() {
        state.accept(
            BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 0, 1), tlvBytes(0x02, tlvs(tlv(0x00, 0, 0), tlv(0x01, 2), tlv16(0x02, -90)))),
        )
        state.accept(
            BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 0, 2), tlvBytes(0x02, tlvs(tlv(0x00, 0, 0), tlv(0x01, 2), tlv16(0x02, 90)))),
        )
        state.accept(
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x10, 0, 1), tlv(0x12, 1)),
        )
        assertEquals(listOf(-90), state.current()!!.lanes.single().anglesDegrees)

        state.accept(
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x10, 0, 2), tlv(0x12, 1)),
        )
        assertEquals(listOf(90), state.current()!!.lanes.single().anglesDegrees)
    }

    @Test
    fun `empty lane blob clears strip when selected`() {
        state.accept(
            BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 0, 0), tlv(0x02)),
        )
        state.accept(
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x10, 0, 0), tlv(0x12, 1)),
        )
        assertEquals(emptyList<BydLaneGuidance.BydLane>(), state.current()!!.lanes)
    }

    @Test
    fun `first non-empty 0x5204 shows without waiting for 0x12`() {
        // Yandex sends lane geometry but often omits LaneGuidanceShowing.
        assertTrue(
            state.accept(
                BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
                tlvs(tlv(0x01, 0, 4), tlvBytes(0x02, tlvs(tlv(0x00, 0, 0), tlv(0x01, 2), tlv16(0x02, -90)))),
            ),
        )
        assertEquals(listOf(-90), state.current()!!.lanes.single().anglesDegrees)
    }

    @Test
    fun `legacy single 0x02 nesting with packed angles still parses`() {
        // Older fixtures packed every lane into one 0x02 and every angle into one multi-
        // value 0x02; keep that path working so older caches / probes stay valid.
        val lane0 = tlvs(
            tlv(0x00, 0, 0),
            tlv(0x01, 2),
            tlv16(0x02, 0, -90),
            tlv16(0x03, -90),
        )
        val lane1 = tlvs(
            tlv(0x00, 0, 1),
            tlv(0x01, 1),
            tlv16(0x02, 0, 90),
        )
        assertTrue(
            state.accept(
                BydLaneGuidanceState.LANE_GUIDANCE_UPDATE,
                tlvs(tlv(0x01, 0, 5), tlvBytes(0x02, tlvs(lane0, lane1))),
            ),
        )
        val lanes = state.current()!!.lanes
        assertEquals(2, lanes.size)
        assertEquals(listOf(0, -90), lanes[0].anglesDegrees)
        assertNull(lanes[1].highlightedDegrees)
    }

    private fun tlv(type: Int, vararg value: Int): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8)
        write(length)
        write(type ushr 8)
        write(type)
        value.forEach(::write)
    }.toByteArray()

    private fun tlv16(type: Int, vararg angles: Int): ByteArray {
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
