package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JaecooLaneProbeTest {
    @Test
    fun `yaswitcher scheme uses highlight icon on left and backgrounds on others`() {
        // iconId(LEFT90, LEFT90|STRAIGHT) = 31; ||STRAIGHT = 0; ||RIGHT90 = 3
        assertEquals(31, DesayLaneIcons.iconId(DesayLaneIcons.LEFT90, listOf(DesayLaneIcons.LEFT90, DesayLaneIcons.STRAIGHT)))
        assertEquals(0, DesayLaneIcons.iconId(null, listOf(DesayLaneIcons.STRAIGHT)))
        assertEquals(3, DesayLaneIcons.iconId(null, listOf(DesayLaneIcons.RIGHT90)))
    }

    @Test
    fun `nine lane and eight road probe schemes are listed`() {
        assertEquals(9, JaecooLaneProbe.laneSchemes.size)
        assertEquals(8, JaecooLaneProbe.roadSchemes.size)
        assertTrue(JaecooLaneProbe.laneSchemes.any { it.id == "apple_pipeline" })
        assertTrue(JaecooLaneProbe.roadSchemes.any { it.id == "road_next_only" })
    }
}
