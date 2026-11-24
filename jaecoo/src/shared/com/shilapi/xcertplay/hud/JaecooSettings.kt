package com.shilapi.xcertplay.hud

import android.content.Context

/** Jaecoo J8 switches. Defaults on: this overlay exists for that car. */
object JaecooSettings {
    private const val PREFS = "diplay_jaecoo"
    private const val KEY_HUD = "hud_enabled"
    private const val KEY_COMPATIBLE_VIDEO_DECODE = "compatible_video_decode"
    private const val KEY_CLUSTER_WELL_SCALE = "cluster_well_scale"
    const val CLUSTER_WELL_SCALE_DEFAULT = 85

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

    /** How large the cluster frame is drawn while it sits in the well, in percent of the panel. */
    fun clusterWellScale(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_WELL_SCALE, CLUSTER_WELL_SCALE_DEFAULT).coerceIn(60, 100)

    fun setClusterWellScale(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_CLUSTER_WELL_SCALE, percent.coerceIn(60, 100)).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
