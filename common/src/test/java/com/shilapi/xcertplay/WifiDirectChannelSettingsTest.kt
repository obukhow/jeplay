package com.shilapi.xcertplay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.WifiP2pChannels
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en", manifest = Config.NONE)
class WifiDirectChannelSettingsTest {
    private lateinit var activity: DiPlayActivity
    private val prefs get() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun setup() {
        prefs.edit().clear().commit()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.WIFI_P2P)
    }

    @Test fun jaecooHidesWifiDirectControlsEvenWhenModeWasWifiP2p() {
        // JAECOO: Wi‑Fi Direct is not offered; picker coerces to Built-in Jaecoo SoftAP.
        val parent = controls()
        assertNull(channelControl(parent))
        assertNull(findSwitch(parent, activity.getString(R.string.settings_wifi_direct_scan_pause)))
        assertTrue(
            descendants(parent).filterIsInstance<Button>().any {
                it.text.contains(activity.getString(R.string.settings_built_in_jaecoo))
            },
        )
        assertFalse(
            descendants(parent).filterIsInstance<Button>().any {
                it.text.contains(activity.getString(R.string.wifi_direct))
            },
        )
    }

    @Test fun invalidOrCorruptSavedChannelsUseAuto() {
        for (channel in listOf(-1, 12, 14, 52, 100, 196)) {
            prefs.edit().putInt("wifi_p2p_preferred_channel", channel).commit()
            assertEquals(WifiP2pChannels.AUTO, AirPlayPersistence.loadWifiP2pPreferredChannel(activity))
        }
        prefs.edit().putString("wifi_p2p_preferred_channel", "149").commit()
        assertEquals(WifiP2pChannels.AUTO, AirPlayPersistence.loadWifiP2pPreferredChannel(activity))
    }

    @Test fun hostRuntimeConfigReceivesSavedChannel() {
        AirPlayPersistence.saveWifiP2pPreferredChannel(activity, 149)
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        CarPlayHostActivity::class.java.getDeclaredField("airPlayIdentity").apply { isAccessible = true }
            .set(host, AirPlayPersistence.loadIdentity(host))
        val config = CarPlayHostActivity::class.java.getDeclaredMethod("createRuntimeConfig")
            .apply { isAccessible = true }.invoke(host) as CarPlayRuntimeConfig
        assertEquals(149, config.wifiP2pPreferredChannel)
    }

    private fun findSwitch(view: View, title: String): android.widget.Switch? {
        if (view is android.widget.Switch && view.contentDescription == title) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findSwitch(view.getChildAt(index), title)?.let { return it }
        }
        return null
    }

    private fun controls() = LinearLayout(activity).also {
        DiPlayActivity::class.java.getDeclaredMethod("wirelessLinkControls", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, it)
    }

    private fun channelControl(view: View): Button? {
        if (view is Button && view.text.startsWith(activity.getString(R.string.wifi_direct_channel_summary, ""))) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            channelControl(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
