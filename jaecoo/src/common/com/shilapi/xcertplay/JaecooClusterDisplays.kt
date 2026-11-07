package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Presentation display for the Jaecoo instrument cluster when BYD/DiLink display names are absent.
 * Used so CarPlay's instrument-cluster scene (stream 111) can land on the dashboard.
 */
object JaecooClusterDisplays {
    fun find(context: Context, presentations: Array<Display>? = null): Display? {
        val displays = presentations
            ?: context.getSystemService(DisplayManager::class.java)
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            ?: emptyArray()
        return displays
            .filter { it.isValid && it.displayId != Display.DEFAULT_DISPLAY }
            .maxByOrNull {
                val size = ClusterMapPresentation.sizeOf(it)
                size.x.toLong() * size.y
            }
    }
}
