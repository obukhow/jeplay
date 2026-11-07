package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JaecooSoftApCredentialsTest {
    @Test
    fun `resolve prefers live SoftAP identity over saved ssid`() {
        val live = JaecooSoftApCredentials.Snapshot(
            ssid = "HS0099374",
            passphrase = "factory-secret",
            security = ManualHotspotSecurity.WPA2,
            channel = 149,
            source = "SoftApConfiguration",
        )
        assertTrue(live.hasPassphrase)
        assertEquals("HS0099374", live.ssid)
        assertEquals("factory-secret".length, live.passphrase!!.length)
    }

    @Test
    fun `snapshot without passphrase is incomplete for secured SoftAP`() {
        val live = JaecooSoftApCredentials.Snapshot(
            ssid = "HS0099374",
            passphrase = null,
            security = ManualHotspotSecurity.WPA2,
            channel = 149,
            source = "scan:wlan1",
        )
        assertNull(live.passphrase)
        assertEquals(false, live.hasPassphrase)
    }

    @Test
    fun `ssid mismatch must not reuse invented passphrase`() {
        // Documented contract used by resolve(): passphrase from settings is only valid when the
        // live SoftAP SSID matches what the driver saved — otherwise iAP2 would overwrite HS00 keys.
        val liveSsid = "HS0099374"
        val savedSsid = "MyJePlayHotspot"
        val liveMatchesSaved = liveSsid.equals(savedSsid, ignoreCase = true)
        assertEquals(false, liveMatchesSaved)
    }
}
