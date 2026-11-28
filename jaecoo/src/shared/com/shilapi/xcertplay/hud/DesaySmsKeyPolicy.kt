package com.shilapi.xcertplay.hud

import com.desaysv.ivi.vdb.event.id.sms.VDValueAction
import com.desaysv.ivi.vdb.event.id.sms.VDValueKey

/** Pure policy for Desay SMS steering-wheel keys → CarPlay actions. */
object DesaySmsKeyPolicy {
    /** Short click on the VR / voice key (same edge YaSwitcher uses for its assistant). */
    fun opensSiri(type: Int, action: Int): Boolean =
        type == VDValueKey.KEYCODE_VR && action == VDValueAction.ACTION_RELEASE
}
