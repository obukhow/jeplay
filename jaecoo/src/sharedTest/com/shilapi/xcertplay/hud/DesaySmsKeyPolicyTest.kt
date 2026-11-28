package com.shilapi.xcertplay.hud

import com.desaysv.ivi.vdb.event.id.sms.VDValueAction
import com.desaysv.ivi.vdb.event.id.sms.VDValueKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesaySmsKeyPolicyTest {
    @Test
    fun opensSiriOnVrReleaseOnly() {
        assertTrue(
            DesaySmsKeyPolicy.opensSiri(VDValueKey.KEYCODE_VR, VDValueAction.ACTION_RELEASE),
        )
        assertFalse(
            DesaySmsKeyPolicy.opensSiri(VDValueKey.KEYCODE_VR, VDValueAction.ACTION_PRESS),
        )
        assertFalse(
            DesaySmsKeyPolicy.opensSiri(VDValueKey.KEYCODE_VR, VDValueAction.ACTION_LONG_PRESS),
        )
        assertFalse(
            DesaySmsKeyPolicy.opensSiri(VDValueKey.KEYCODE_MEDIA_NEXT, VDValueAction.ACTION_RELEASE),
        )
        assertFalse(
            DesaySmsKeyPolicy.opensSiri(VDValueKey.KEYCODE_PLAY_OR_PAUSE, VDValueAction.ACTION_RELEASE),
        )
    }
}
