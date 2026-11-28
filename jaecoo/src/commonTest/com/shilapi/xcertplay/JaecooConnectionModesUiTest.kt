package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
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
class JaecooConnectionModesUiTest {
    @Test
    fun hidesManualHotspotAndWifiDirect() {
        val context = RuntimeEnvironment.getApplication()
        val offered = JaecooConnectionModesUi.offered(context)
        assertTrue(WirelessHotspotMode.JAECOO_BUILTIN in offered)
        assertTrue(WirelessHotspotMode.EXISTING_WIFI in offered)
        assertFalse(WirelessHotspotMode.MANUAL in offered)
        assertFalse(WirelessHotspotMode.WIFI_P2P in offered)
        assertEquals(
            WirelessHotspotMode.JAECOO_BUILTIN,
            JaecooConnectionModesUi.coerce(context, WirelessHotspotMode.WIFI_P2P),
        )
        assertEquals(
            WirelessHotspotMode.JAECOO_BUILTIN,
            JaecooConnectionModesUi.coerce(context, WirelessHotspotMode.MANUAL),
        )
    }
}
