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

    @Test fun clusterWellScaleDefaultsToASlightlySmallerFrame() {
        assertEquals(JaecooSettings.CLUSTER_WELL_SCALE_DEFAULT, JaecooSettings.clusterWellScale(app))
    }

    @Test fun clusterWellScalePersists() {
        JaecooSettings.setClusterWellScale(app, 70)
        assertEquals(70, JaecooSettings.clusterWellScale(app))
    }

    @Test fun compatibleVideoDecodePersists() {
        JaecooSettings.setCompatibleVideoDecode(app, false)
        assertFalse(JaecooSettings.compatibleVideoDecode(app))
        JaecooSettings.setCompatibleVideoDecode(app, true)
        assertTrue(JaecooSettings.compatibleVideoDecode(app))
    }
}
