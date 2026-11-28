package com.shilapi.xcertplay.hud

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JaecooSettingsTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Test fun compatibleVideoDecodeDefaultsOn() {
        assertTrue(JaecooSettings.compatibleVideoDecode(app))
    }

    @Test fun clusterWellScaleDefaultsToEightyFive() {
        assertEquals(85, JaecooSettings.clusterWellScale(app))
    }

    @Test fun clusterWellScalePersists() {
        JaecooSettings.setClusterWellScale(app, 70)
        assertEquals(70, JaecooSettings.clusterWellScale(app))
    }

    @Test fun clusterWellFitDefaultsToShift() {
        assertEquals(JaecooSettings.CLUSTER_WELL_FIT_SHIFT, JaecooSettings.clusterWellFit(app))
    }

    @Test fun clusterWellFitPersists() {
        JaecooSettings.setClusterWellFit(app, JaecooSettings.CLUSTER_WELL_FIT_RESIZE)
        assertEquals(JaecooSettings.CLUSTER_WELL_FIT_RESIZE, JaecooSettings.clusterWellFit(app))
        JaecooSettings.setClusterWellFit(app, JaecooSettings.CLUSTER_WELL_FIT_SHIFT)
        assertEquals(JaecooSettings.CLUSTER_WELL_FIT_SHIFT, JaecooSettings.clusterWellFit(app))
    }

    @Test fun clusterWellShiftDefaultsToFifteenRightAndSevenDown() {
        assertEquals(65, JaecooSettings.clusterWellShiftXPercent(app))
        assertEquals(57, JaecooSettings.clusterWellShiftYPercent(app))
    }

    @Test fun clusterWellShiftScaleDefaultsToSeventy() {
        assertEquals(70, JaecooSettings.clusterWellShiftScale(app))
    }

    @Test fun clusterWellShiftScalePersists() {
        JaecooSettings.setClusterWellShiftScale(app, 80)
        assertEquals(80, JaecooSettings.clusterWellShiftScale(app))
    }

    @Test fun clusterMeterLayoutPersists() {
        assertEquals(null, JaecooSettings.clusterMeterLayout(app))
        JaecooSettings.setClusterMeterLayout(app, "WELL")
        assertEquals("WELL", JaecooSettings.clusterMeterLayout(app))
    }

    @Test fun compatibleVideoDecodePersists() {
        JaecooSettings.setCompatibleVideoDecode(app, false)
        assertFalse(JaecooSettings.compatibleVideoDecode(app))
        JaecooSettings.setCompatibleVideoDecode(app, true)
        assertTrue(JaecooSettings.compatibleVideoDecode(app))
    }
}
