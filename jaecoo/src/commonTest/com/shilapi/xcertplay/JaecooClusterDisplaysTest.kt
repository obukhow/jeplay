package com.shilapi.xcertplay

import android.hardware.display.DisplayManager
import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class JaecooClusterDisplaysTest {
    @Test
    fun `picks the largest presentation display`() {
        val small = display("small", "w960dp-h360dp-mdpi")
        val large = display("cluster", "w1920dp-h720dp-mdpi")
        try {
            assertEquals(large, JaecooClusterDisplays.find(RuntimeEnvironment.getApplication())?.displayId)
        } finally {
            ShadowDisplayManager.removeDisplay(small)
            ShadowDisplayManager.removeDisplay(large)
        }
    }

    private fun display(name: String, spec: String): Int {
        val id = ShadowDisplayManager.addDisplay(spec, 5)
        val manager = RuntimeEnvironment.getApplication().getSystemService(DisplayManager::class.java)
        shadowOf(manager.getDisplay(id)).apply {
            setName(name)
            setFlags(Display.FLAG_PRESENTATION)
        }
        return id
    }
}
