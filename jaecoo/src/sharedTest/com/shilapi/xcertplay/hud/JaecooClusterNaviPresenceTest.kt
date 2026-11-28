package com.shilapi.xcertplay.hud

import android.content.Context
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviAreaDisplay
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class JaecooClusterNaviPresenceTest {
    private val events = mutableListOf<VDEvent>()
    private lateinit var app: Context

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        events.clear()
        JaecooClusterNaviPresence.resetForTest({ event -> events += event; true }, app)
        events.clear()
    }

    @After
    fun tearDown() = JaecooClusterNaviPresence.resetForTest(null, null)

    @Test
    fun `announce publishes display-cluster and display-area`() {
        JaecooClusterNaviPresence.setClusterMapShown(true)

        assertTrue(events.any { it.id == VDNaviDisplayCluster.EVENT_ID })
        assertTrue(events.any { it.id == VDNaviDisplayArea.EVENT_ID })
        assertTrue(events.any { it.id == VDNaviDigitalInfo.EVENT_ID })

        @Suppress("DEPRECATION")
        val cluster = events.last { it.id == VDNaviDisplayCluster.EVENT_ID }
            .payload!!.getParcelable<VDNaviDisplayCluster>(VDNaviDisplayCluster.PAYLOAD_KEY)!!
        assertEquals("true", cluster.displayCluster)
        assertEquals("true", cluster.naviFrontDeskStatus)
        assertEquals(0, cluster.perspective)
        assertEquals("false", cluster.perspectiveResult)
        assertEquals("false", cluster.requestDisplayNaviArea)

        @Suppress("DEPRECATION")
        val area = events.last { it.id == VDNaviDisplayArea.EVENT_ID }
            .payload!!.getParcelable<VDNaviDisplayArea>(VDNaviDisplayArea.PAYLOAD_KEY)!!
        assertEquals(JaecooClusterNaviPresence.AREA_WELL, area.naviDisplayArea)
        assertEquals("true", area.naviDisplayAreaResult)

        @Suppress("DEPRECATION")
        val digital = events.last { it.id == VDNaviDigitalInfo.EVENT_ID }
            .payload!!.getParcelable<VDNaviDigitalInfo>(VDNaviDigitalInfo.PAYLOAD_KEY)!!
        assertEquals(JaecooClusterNaviPresence.STATUS_AVAILABLE, digital.naviStatus)
    }

    @Test
    fun `full meter slot uses area 10`() {
        JaecooClusterNaviPresence.onMeterSlot(available = true, full = true)

        @Suppress("DEPRECATION")
        val area = events.last { it.id == VDNaviDisplayArea.EVENT_ID }
            .payload!!.getParcelable<VDNaviDisplayArea>(VDNaviDisplayArea.PAYLOAD_KEY)!!
        assertEquals(JaecooClusterNaviPresence.AREA_FULL, area.naviDisplayArea)
    }

    @Test
    fun `hidden meter slot clears DisplayCluster`() {
        JaecooClusterNaviPresence.setClusterMapShown(true)
        events.clear()
        JaecooClusterNaviPresence.onMeterSlot(available = false, full = false)

        @Suppress("DEPRECATION")
        val cluster = events.last { it.id == VDNaviDisplayCluster.EVENT_ID }
            .payload!!.getParcelable<VDNaviDisplayCluster>(VDNaviDisplayCluster.PAYLOAD_KEY)!!
        assertEquals("false", cluster.displayCluster)
        assertEquals("false", cluster.naviFrontDeskStatus)
        assertTrue(events.none { it.id == VDNaviDisplayArea.EVENT_ID })
    }

    @Test
    fun `instrument 721705 request is answered with the requested area`() {
        val request = VDNaviAreaDisplay()
        request.setRequestNaviAreaDisplay(10)
        // Simulate notify by going through the public meter path that mirrors the answer.
        JaecooClusterNaviPresence.onMeterSlot(available = true, full = true)

        @Suppress("DEPRECATION")
        val area = events.last { it.id == VDNaviDisplayArea.EVENT_ID }
            .payload!!.getParcelable<VDNaviDisplayArea>(VDNaviDisplayArea.PAYLOAD_KEY)!!
        assertEquals(10, area.naviDisplayArea)
    }
}
