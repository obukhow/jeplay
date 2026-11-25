package com.shilapi.xcertplay

import android.view.Gravity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class JaecooClusterMeterTest {
    @Test fun fullscreenIsMapStatusAndAreaBothOne() {
        assertEquals(JaecooClusterMeter.Layout.FULL, JaecooClusterMeter.layoutOf(1, 1, false))
    }

    @Test fun anyOtherSlotIsTheWell() {
        assertEquals(JaecooClusterMeter.Layout.WELL, JaecooClusterMeter.layoutOf(1, 0, false))
        assertEquals(JaecooClusterMeter.Layout.WELL, JaecooClusterMeter.layoutOf(0, 1, false))
        assertEquals(JaecooClusterMeter.Layout.WELL, JaecooClusterMeter.layoutOf(0, 0, false))
        assertEquals(JaecooClusterMeter.Layout.WELL, JaecooClusterMeter.layoutOf(2, 2, false))
    }

    @Test fun closeAllHidesTheMap() {
        assertEquals(JaecooClusterMeter.Layout.HIDDEN, JaecooClusterMeter.layoutOf(1, 1, true))
        assertEquals(JaecooClusterMeter.Layout.HIDDEN, JaecooClusterMeter.layoutOf(0, 0, true))
    }

    @Test fun scale100MatchesTheYaSwitcherWell() {
        val frame = JaecooClusterMeter.frame(JaecooClusterMeter.Layout.WELL, 1920, 720, 81, 43, 100)
        assertEquals(500, frame.width)
        assertEquals(620, frame.height)
        assertEquals(Gravity.TOP or Gravity.LEFT, frame.gravity)
        assertEquals(true, frame.visible)
        assertEquals(1555, frame.x + frame.width / 2)
        assertEquals(309, frame.y + frame.height / 2)
        assertEquals(1305, frame.x)
        assertEquals(-1, frame.y)
    }

    @Test fun scale85GrowsTheWindowSoThe500x620HoleShowsMoreMap() {
        val frame = JaecooClusterMeter.frame(JaecooClusterMeter.Layout.WELL, 1920, 720, 81, 43, 85)
        assertEquals(588, frame.width)
        assertEquals(729, frame.height)
        assertEquals(true, frame.width > JaecooClusterMeter.WELL_WIDTH)
        assertEquals(true, frame.height > JaecooClusterMeter.WELL_HEIGHT)
        assertEquals(1555, frame.x + frame.width / 2)
        assertEquals(309, frame.y + frame.height / 2)
    }

    @Test fun wellFrameKeepsTheSameFractionOnAnotherPanel() {
        val frame = JaecooClusterMeter.frame(JaecooClusterMeter.Layout.WELL, 960, 360, 81, 43, 100)
        assertEquals(250, frame.width)
        assertEquals(310, frame.height)
        assertEquals(652, frame.x)
        assertEquals(-1, frame.y)
    }

    @Test fun maxRightSliderPutsTheCarPastTheWellCentre() {
        val frame = JaecooClusterMeter.frame(JaecooClusterMeter.Layout.WELL, 1920, 720, 95, 43, 85)
        assertEquals(588, frame.width)
        assertEquals(1824, frame.x + frame.width / 2)
        assertEquals(true, frame.x + frame.width / 2 > JaecooClusterMeter.WELL_X + JaecooClusterMeter.WELL_WIDTH / 2)
    }

    @Test fun modeChoosesWhetherTheReportedSlotIsUsed() {
        assertEquals(JaecooClusterMeter.Layout.FULL, JaecooClusterMeter.shown(0, JaecooClusterMeter.Layout.WELL))
        assertEquals(JaecooClusterMeter.Layout.WELL, JaecooClusterMeter.shown(1, JaecooClusterMeter.Layout.FULL))
        assertEquals(JaecooClusterMeter.Layout.HIDDEN, JaecooClusterMeter.shown(1, JaecooClusterMeter.Layout.HIDDEN))
        assertEquals(JaecooClusterMeter.Layout.WELL, JaecooClusterMeter.shown(2, JaecooClusterMeter.Layout.WELL))
        assertEquals(JaecooClusterMeter.Layout.FULL, JaecooClusterMeter.shown(2, null))
    }

    @Test fun fullscreenFillsThePanel() {
        val frame = JaecooClusterMeter.frame(JaecooClusterMeter.Layout.FULL, 1920, 720)
        assertEquals(0, frame.x)
        assertEquals(0, frame.y)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, frame.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, frame.height)
        assertEquals(Gravity.CENTER, frame.gravity)
        assertEquals(true, frame.visible)
    }

    @Test fun hiddenFrameIsNotShown() {
        val frame = JaecooClusterMeter.frame(JaecooClusterMeter.Layout.HIDDEN, 1920, 720)
        assertEquals(false, frame.visible)
    }
}
