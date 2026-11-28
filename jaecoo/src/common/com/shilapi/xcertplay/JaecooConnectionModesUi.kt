package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Jaecoo connection picker: keep Built-in Jaecoo SoftAP and same-LAN Wi‑Fi.
 * Hide BYD-style "built-in car hotspot" (duplicates Jaecoo SoftAP) and Wi‑Fi Direct (does not work).
 */
object JaecooConnectionModesUi {
    fun offered(@Suppress("UNUSED_PARAMETER") context: Context): List<WirelessHotspotMode> = listOf(
        WirelessHotspotMode.JAECOO_BUILTIN,
        WirelessHotspotMode.EXISTING_WIFI,
    )

    fun isOffered(context: Context, mode: WirelessHotspotMode): Boolean =
        offered(context).contains(mode)

    /** Map a stored/hidden mode onto something the picker still shows. */
    fun coerce(context: Context, mode: WirelessHotspotMode): WirelessHotspotMode =
        if (isOffered(context, mode)) mode else WirelessHotspotMode.JAECOO_BUILTIN
}
