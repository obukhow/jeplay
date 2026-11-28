package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JaecooWheelKeysUiTest {
    @Test
    fun hideSectionFalseWithoutDesaySmsPackage() {
        // Robolectric has no com.desaysv.ivi.vds.sms — keep upstream wheel-key UI in unit tests.
        assertFalse(JaecooWheelKeysUi.hideSection(RuntimeEnvironment.getApplication()))
    }
}
