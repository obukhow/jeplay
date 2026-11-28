package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.DesaySmsKeys

/**
 * Jaecoo / Desay head units deliver steering-wheel keys on VDB SMS, not as Android [android.view.KeyEvent]s.
 * The assignable "Wheel keys" settings (Siri assign, media remap, joystick, external controller) are
 * no-ops there — JePlay already listens via [DesaySmsKeys]. Keep this gate in the jaecoo tree so
 * upstream DiPlay settings UI can stay untouched aside from a one-line call site.
 */
object JaecooWheelKeysUi {
    /** Hide the whole Wheel keys settings section when Desay SMS is present. */
    fun hideSection(context: Context): Boolean = DesaySmsKeys.available(context)
}
