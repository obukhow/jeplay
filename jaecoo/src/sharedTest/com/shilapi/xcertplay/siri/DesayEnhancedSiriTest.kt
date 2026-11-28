package com.shilapi.xcertplay.siri

import android.content.Context
import com.shilapi.xcertplay.hud.JaecooSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DesayEnhancedSiriTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()

    @Test fun enhancedSiriDefaultsOff() {
        assertFalse(JaecooSettings.enhancedSiri(app))
    }

    @Test fun enhancedSiriPersists() {
        JaecooSettings.setEnhancedSiri(app, true)
        assertTrue(JaecooSettings.enhancedSiri(app))
        JaecooSettings.setEnhancedSiri(app, false)
        assertFalse(JaecooSettings.enhancedSiri(app))
    }

    @Test fun wakeLanguageUsesRussianWhenLocaleIsRu() {
        val config = app.resources.configuration
        config.setLocale(Locale("ru", "RU"))
        app.resources.updateConfiguration(config, app.resources.displayMetrics)
        assertEquals("ru-RU", DesayEnhancedSiri.wakeLanguage(app))
    }

    @Test fun availableIsFalseWithoutNativesOnRobolectric() {
        assertFalse(DesayEnhancedSiri.available(app))
    }
}
