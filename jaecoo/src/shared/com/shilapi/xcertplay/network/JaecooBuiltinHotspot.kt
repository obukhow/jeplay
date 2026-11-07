package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Jaecoo SoftAP presets for [WirelessHotspotMode.JAECOO_BUILTIN].
 *
 * Matches Desay OEM SoftAP defaults (`wlan1`, 5 GHz / channel 149) used by
 * `com.desaysv.vehicle.carplayapp` and DriveLabs wifihelper.
 */
object JaecooBuiltinHotspot {
    const val INTERFACE = "wlan1"
    const val CHANNEL = 149
    val BAND: ManualHotspotBand = ManualHotspotBand.GHZ_5

    /** Modes that attach to the car SoftAP with saved SSID/passphrase (not P2P / same-LAN). */
    fun usesManualBackend(mode: WirelessHotspotMode): Boolean =
        mode == WirelessHotspotMode.MANUAL || mode == WirelessHotspotMode.JAECOO_BUILTIN

    fun preferredInterface(mode: WirelessHotspotMode, stored: String?): String? {
        val trimmed = stored?.trim().orEmpty()
        if (mode == WirelessHotspotMode.JAECOO_BUILTIN) {
            return trimmed.ifEmpty { INTERFACE }
        }
        return trimmed.ifEmpty { null }
    }

    fun preferredBand(mode: WirelessHotspotMode, stored: ManualHotspotBand): ManualHotspotBand =
        if (mode == WirelessHotspotMode.JAECOO_BUILTIN && stored == ManualHotspotBand.AUTO) BAND else stored

    fun preferredChannel(mode: WirelessHotspotMode, stored: Int): Int =
        if (mode == WirelessHotspotMode.JAECOO_BUILTIN && stored == 0) CHANNEL else stored
}
