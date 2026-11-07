package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class JaecooBuiltinHotspotTest {
    @Test
    fun `manual backends include Jaecoo builtin`() {
        assertTrue(JaecooBuiltinHotspot.usesManualBackend(WirelessHotspotMode.MANUAL))
        assertTrue(JaecooBuiltinHotspot.usesManualBackend(WirelessHotspotMode.JAECOO_BUILTIN))
        assertFalse(JaecooBuiltinHotspot.usesManualBackend(WirelessHotspotMode.WIFI_P2P))
        assertFalse(JaecooBuiltinHotspot.usesManualBackend(WirelessHotspotMode.EXISTING_WIFI))
    }

    @Test
    fun `Jaecoo defaults fill empty interface band and channel`() {
        assertEquals(
            JaecooBuiltinHotspot.INTERFACE,
            JaecooBuiltinHotspot.preferredInterface(WirelessHotspotMode.JAECOO_BUILTIN, ""),
        )
        assertEquals(
            JaecooBuiltinHotspot.BAND,
            JaecooBuiltinHotspot.preferredBand(WirelessHotspotMode.JAECOO_BUILTIN, ManualHotspotBand.AUTO),
        )
        assertEquals(
            JaecooBuiltinHotspot.CHANNEL,
            JaecooBuiltinHotspot.preferredChannel(WirelessHotspotMode.JAECOO_BUILTIN, 0),
        )
    }

    @Test
    fun `stored Jaecoo values are kept`() {
        assertEquals(
            "ap0",
            JaecooBuiltinHotspot.preferredInterface(WirelessHotspotMode.JAECOO_BUILTIN, " ap0 "),
        )
        assertEquals(
            ManualHotspotBand.GHZ_2_4,
            JaecooBuiltinHotspot.preferredBand(WirelessHotspotMode.JAECOO_BUILTIN, ManualHotspotBand.GHZ_2_4),
        )
        assertEquals(
            36,
            JaecooBuiltinHotspot.preferredChannel(WirelessHotspotMode.JAECOO_BUILTIN, 36),
        )
    }

    @Test
    fun `enable intent targets Desay SystemUI`() {
        val on = JaecooSoftApStarter.enableIntent(true)
        assertEquals(JaecooSoftApStarter.ACTION_SET_AP_STATUS, on.action)
        assertTrue(on.getBooleanExtra(JaecooSoftApStarter.EXTRA_AP_STATUS, false))
        val off = JaecooSoftApStarter.enableIntent(false)
        assertFalse(off.getBooleanExtra(JaecooSoftApStarter.EXTRA_AP_STATUS, true))
    }
}
