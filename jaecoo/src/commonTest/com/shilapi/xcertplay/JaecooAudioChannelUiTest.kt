package com.shilapi.xcertplay

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JaecooAudioChannelUiTest {
    @Test
    fun hideChannelPickersOnJaecooBuild() {
        assertTrue(JaecooAudioChannelUi.hideChannelPickers(RuntimeEnvironment.getApplication()))
    }
}
