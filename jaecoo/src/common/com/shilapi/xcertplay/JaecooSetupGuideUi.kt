package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.setup.SetupGuide

/**
 * Jaecoo / Chery head units are not BYD DiLink. The setup guide's first step ("Your car" /
 * DiLink 3/4/5) only confuses drivers. Skip it and start at connection. Keep the gate in
 * jaecoo/; DiPlayActivity only needs thin call sites.
 */
object JaecooSetupGuideUi {
    @Suppress("UNUSED_PARAMETER")
    fun skipCarStep(context: Context): Boolean = true

    fun firstStep(context: Context): Int =
        if (skipCarStep(context)) SetupGuide.STEP_CONNECTION else SetupGuide.STEP_CAR

    fun visibleStepCount(context: Context): Int =
        if (skipCarStep(context)) SetupGuide.STEP_COUNT - 1 else SetupGuide.STEP_COUNT

    /** Zero-based index among the steps that are still shown. */
    fun visibleStepIndex(context: Context, step: Int): Int =
        if (skipCarStep(context)) (step - SetupGuide.STEP_CONNECTION).coerceAtLeast(0) else step
}
