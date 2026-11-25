package com.shilapi.xcertplay.hud

import android.content.Context

/** Jaecoo J8 switches. Defaults on: this overlay exists for that car. */
object JaecooSettings {
    private const val PREFS = "diplay_jaecoo"
    private const val KEY_HUD = "hud_enabled"
    private const val KEY_COMPATIBLE_VIDEO_DECODE = "compatible_video_decode"
    private const val KEY_CLUSTER_WELL_SCALE = "cluster_well_scale"
    private const val KEY_CLUSTER_WELL_FIT = "cluster_well_fit"
    private const val KEY_CLUSTER_WELL_SHIFT_X = "cluster_well_shift_x"
    private const val KEY_CLUSTER_WELL_SHIFT_Y = "cluster_well_shift_y"
    /**
     * Map scale inside the well. 100 → window equals the 500×620 well; 85 → window =
     * well / 0.85, so the fixed 500×620 hole shows more map without revealing the frame edges.
     */
    const val CLUSTER_WELL_SCALE_DEFAULT = 85
    const val CLUSTER_WELL_SCALE_MIN = 60
    const val CLUSTER_WELL_SCALE_MAX = 100

    /**
     * How the cluster Presentation enters the well.
     * [CLUSTER_WELL_FIT_RESIZE] shrinks the window and advertises that size (reconnects).
     * [CLUSTER_WELL_FIT_SHIFT] keeps the full-panel stream and slides it so the car marker
     * lands on the well centre (no reconnect).
     */
    const val CLUSTER_WELL_FIT_RESIZE = 0
    const val CLUSTER_WELL_FIT_SHIFT = 1
    const val CLUSTER_WELL_FIT_DEFAULT = CLUSTER_WELL_FIT_SHIFT

    /** Fine-tune around the computed shift; 50 = exact marker→well alignment. */
    const val CLUSTER_WELL_SHIFT_DEFAULT = 50

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

    /** How large the cluster frame is drawn while it sits in the well, in percent of the well. */
    fun clusterWellScale(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_WELL_SCALE, CLUSTER_WELL_SCALE_DEFAULT)
            .coerceIn(CLUSTER_WELL_SCALE_MIN, CLUSTER_WELL_SCALE_MAX)

    fun setClusterWellScale(context: Context, percent: Int) {
        prefs(context).edit()
            .putInt(KEY_CLUSTER_WELL_SCALE, percent.coerceIn(CLUSTER_WELL_SCALE_MIN, CLUSTER_WELL_SCALE_MAX))
            .commit()
    }

    fun clusterWellFit(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_WELL_FIT, CLUSTER_WELL_FIT_DEFAULT).let {
            if (it == CLUSTER_WELL_FIT_RESIZE) CLUSTER_WELL_FIT_RESIZE else CLUSTER_WELL_FIT_SHIFT
        }

    fun setClusterWellFit(context: Context, fit: Int) {
        val value = if (fit == CLUSTER_WELL_FIT_RESIZE) CLUSTER_WELL_FIT_RESIZE else CLUSTER_WELL_FIT_SHIFT
        prefs(context).edit().putInt(KEY_CLUSTER_WELL_FIT, value).commit()
    }

    fun clusterWellShiftXPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_WELL_SHIFT_X, CLUSTER_WELL_SHIFT_DEFAULT).coerceIn(5, 95)

    fun setClusterWellShiftXPercent(context: Context, percent: Int) {
        prefs(context).edit()
            .putInt(KEY_CLUSTER_WELL_SHIFT_X, percent.coerceIn(5, 95))
            .commit()
    }

    fun clusterWellShiftYPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_WELL_SHIFT_Y, CLUSTER_WELL_SHIFT_DEFAULT).coerceIn(5, 95)

    fun setClusterWellShiftYPercent(context: Context, percent: Int) {
        prefs(context).edit()
            .putInt(KEY_CLUSTER_WELL_SHIFT_Y, percent.coerceIn(5, 95))
            .commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
