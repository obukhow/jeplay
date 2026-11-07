package com.shilapi.xcertplay.hud

import android.content.Context

/** Jaecoo J8 switches. Defaults on: this overlay exists for that car. */
object JaecooSettings {
    private const val PREFS = "diplay_jaecoo"
    private const val KEY_HUD = "hud_enabled"
    private const val KEY_COMPATIBLE_VIDEO_DECODE = "compatible_video_decode"

    /** Whether Desay CarLan VDB (`com.desaysv.ivi.vds.carlan`) is installed. */
    fun hudAvailable(context: Context): Boolean = DesayVdbClient().available(context)

    /** Send CarPlay turn arrows/distance to the car HUD/cluster through Desay CarLan VDB. */
    fun hud(context: Context): Boolean = prefs(context).getBoolean(KEY_HUD, true)

    fun setHud(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HUD, enabled).commit()
    }

    /**
     * Decode like OEM Desay CarPlay on Jaecoo: SurfaceView, dedicated input/output threads,
     * immediate frame release, and no mid-GOP backlog discard/recover. Default on for this car.
     */
    fun compatibleVideoDecode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPATIBLE_VIDEO_DECODE, true)

    fun setCompatibleVideoDecode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COMPATIBLE_VIDEO_DECODE, enabled).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
