package com.shilapi.xcertplay

import com.shilapi.xcertplay.setup.SetupGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JaecooSetupGuideUiTest {
    @Test
    fun skipsDiLinkCarStep() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(JaecooSetupGuideUi.skipCarStep(context))
        assertEquals(SetupGuide.STEP_CONNECTION, JaecooSetupGuideUi.firstStep(context))
        assertEquals(SetupGuide.STEP_COUNT - 1, JaecooSetupGuideUi.visibleStepCount(context))
        assertEquals(0, JaecooSetupGuideUi.visibleStepIndex(context, SetupGuide.STEP_CONNECTION))
        assertEquals(3, JaecooSetupGuideUi.visibleStepIndex(context, SetupGuide.STEP_DONE))
    }
}
