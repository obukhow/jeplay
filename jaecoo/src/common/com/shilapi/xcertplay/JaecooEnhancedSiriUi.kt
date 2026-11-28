package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.JaecooSettings
import com.shilapi.xcertplay.siri.DesayEnhancedSiri

/**
 * Settings helpers for OEM Enhanced Siri on Jaecoo. Keeps the thick copy and
 * apply path in the jaecoo tree; [DiPlayActivity] owns the Switch row.
 */
object JaecooEnhancedSiriUi {
    fun title(context: Context): String =
        context.getString(R.string.settings_jaecoo_enhanced_siri)

    fun description(context: Context): String = buildString {
        append(context.getString(R.string.settings_jaecoo_enhanced_siri_description))
        append('\n')
        append(DesayEnhancedSiri.statusLine(context))
    }

    fun enabled(context: Context): Boolean = JaecooSettings.enhancedSiri(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        JaecooSettings.setEnhancedSiri(context, enabled)
        DesayEnhancedSiri.applySetting(context)
    }
}
