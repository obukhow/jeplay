package com.shilapi.xcertplay.hud

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviAreaDisplay
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster

/**
 * Tell the Desay instrument that JePlay has a cluster map, so the meter stops showing
 * «карт нет».
 *
 * Protocol:
 * - Subscribe to [VDNaviAreaDisplay.EVENT_ID] (721705) from the instrument.
 * - Answer with [VDNaviDisplayCluster.EVENT_ID] (721699) + [VDNaviDisplayArea.EVENT_ID] (721702).
 * - Keep announcing while the cluster Presentation / stream 111 is up.
 *
 * Not a package whitelist: TurboDog is not required.
 */
object JaecooClusterNaviPresence {
    private const val TAG = "DiPlay-JaecooNavi"
    private const val RETRY_MILLIS = 3_000L
    private const val IVDBUS_NOTIFY_DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBusNotify"
    private const val TRANSACTION_SUBSCRIBE_CUSTOMIZED = 4
    private const val TRANSACTION_UNSUBSCRIBE_CUSTOMIZED = 5
    private const val TRANSACTION_ON_NOTIFY = 1

    /** Full-panel area when `map_status=1` and `map_area=1`. */
    const val AREA_FULL = 10
    /** Well / small-window area. */
    const val AREA_WELL = 9
    /**
     * Digital status when navi is present but not actively guiding.
     * Guiding stays [VDNaviDigitalInfo.STATUS_GUIDING] from [DesayHudOutput].
     */
    const val STATUS_AVAILABLE = 4

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var app: Context? = null
    private var client = DesayVdbClient()
    private var binder: IBinder? = null
    private var binding = false
    private var subscribed = false
    private var notifyStub: NotifyStub? = null
    private var clusterMapShown = false
    private var announced = false
    private var lastArea: Int = AREA_WELL
    private var lastAvailable: Boolean = false

    fun start(context: Context) = synchronized(lock) {
        val application = context.applicationContext
        app = application
        client.initialize(application)
        if (!client.available(application)) {
            Log.i(TAG, "CarLan missing; cluster navi presence skipped")
            return
        }
        ensureBoundLocked()
        // Proactive announce — the instrument often asks only once, and Desay may
        // already be in a map slot with no new 721705 after JePlay connects.
        publishPresenceLocked(available = true, area = lastArea, force = true)
    }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        if (announced || lastAvailable) {
            publishPresenceLocked(available = false, area = lastArea, force = true)
        }
        clusterMapShown = false
        announced = false
        val remote = binder
        val stub = notifyStub
        if (remote != null && stub != null && subscribed) {
            runCatching { transactUnsubscribe(remote, subscribeEvent(), stub) }
        }
        subscribed = false
        notifyStub = null
        handler.removeCallbacksAndMessages(null)
        val ctx = app
        if (ctx != null && (binder != null || binding)) {
            runCatching { ctx.unbindService(connection) }
        }
        binder = null
        binding = false
        client.release()
        client = DesayVdbClient()
        app = null
        Log.i(TAG, "cluster navi presence stopped")
    }

    /** Cluster Presentation / stream 111 visibility from the host. */
    fun setClusterMapShown(shown: Boolean) = synchronized(lock) {
        if (clusterMapShown == shown && announced == shown) return
        clusterMapShown = shown
        if (app == null) return
        publishPresenceLocked(available = shown, area = lastArea, force = true)
    }

    /**
     * Meter slot from `desay.broadcast.map.meter.interaction`.
     * @param available false when Desay sends `close_all_variants`
     * @param full true for full panel (`map_status=1` + `map_area=1`), else well
     */
    fun onMeterSlot(available: Boolean, full: Boolean) = synchronized(lock) {
        val area = if (full) AREA_FULL else AREA_WELL
        lastArea = area
        if (app != null) publishPresenceLocked(available = available, area = area, force = true)
    }

    /** Test hook. */
    internal fun resetForTest(publisher: ((VDEvent) -> Boolean)?, appContext: Context?) = synchronized(lock) {
        stopLocked()
        client = if (publisher != null) DesayVdbClient(publisher) else DesayVdbClient()
        app = appContext
        clusterMapShown = false
        announced = false
        lastArea = AREA_WELL
        lastAvailable = false
        if (appContext != null) {
            client.initialize(appContext)
            publishPresenceLocked(available = true, area = AREA_WELL, force = true)
        }
    }

    private fun publishPresenceLocked(available: Boolean, area: Int, force: Boolean) {
        if (!force && lastAvailable == available && lastArea == area && announced == available) return
        lastAvailable = available
        lastArea = area
        val flag = if (available) "true" else "false"
        val cluster = VDNaviDisplayCluster()
        cluster.setNaviFrontDeskStatus(flag)
        cluster.setDisplayCluster(flag)
        cluster.setPerspective(0)
        cluster.setPerspectiveResult("false")
        cluster.setRequestDisplayNaviArea("false")
        val clusterOk = client.publish(VDNaviDisplayCluster.createEvent(cluster))

        var areaOk = true
        if (available) {
            val displayArea = VDNaviDisplayArea()
            displayArea.setNaviDisplayArea(area)
            displayArea.setNaviDisplayAreaResult("true")
            areaOk = client.publish(VDNaviDisplayArea.createEvent(displayArea))
            // Presence without active turn-by-turn: status 4 so the meter
            // does not stay on «карт нет». Guiding (3) still comes from DesayHudOutput.
            val digital = VDNaviDigitalInfo()
            digital.setNaviStatus(STATUS_AVAILABLE)
            client.publish(VDNaviDigitalInfo.createEvent(digital))
        }

        announced = available && clusterOk
        Log.i(
            TAG,
            "presence available=$available area=$area " +
                "clusterOk=$clusterOk areaOk=$areaOk",
        )
        if (available && !clusterOk) {
            handler.postDelayed({
                synchronized(lock) {
                    if (app != null && lastAvailable) {
                        publishPresenceLocked(available = true, area = lastArea, force = true)
                    }
                }
            }, RETRY_MILLIS)
        }
    }

    private fun onAreaRequest(requestArea: Int) = synchronized(lock) {
        Log.i(TAG, "721705 RequestNaviAreaDisplay=$requestArea")
        val area = when {
            requestArea > 0 -> requestArea
            lastArea > 0 -> lastArea
            else -> AREA_WELL
        }
        lastArea = area
        // Instrument asked — answer even if Presentation is still coming up.
        publishPresenceLocked(available = true, area = area, force = true)
    }

    private fun ensureBoundLocked() {
        val ctx = app ?: return
        if (binder != null) {
            ensureSubscribedLocked()
            return
        }
        if (binding) return
        if (!packagePresent(ctx)) return
        binding = true
        val intent = Intent(DesayVdbClient.CARLAN_ACTION).setPackage(DesayVdbClient.CARLAN_PACKAGE)
        try {
            val ok = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!ok) {
                binding = false
                Log.w(TAG, "bindService(${DesayVdbClient.CARLAN_PACKAGE}) returned false")
            }
        } catch (e: Exception) {
            binding = false
            Log.w(TAG, "bindService failed: ${e.message}")
        }
    }

    private fun ensureSubscribedLocked() {
        val remote = binder ?: return
        if (subscribed) return
        val stub = NotifyStub { event -> dispatch(event) }
        notifyStub = stub
        val ok = runCatching {
            transactSubscribe(remote, subscribeEvent(), stub)
            true
        }.onFailure { Log.w(TAG, "subscribe 721705 failed", it) }.getOrDefault(false)
        subscribed = ok
        if (ok) Log.i(TAG, "subscribed NAVIGATION_AREA_DISPLAY_REQUEST (721705)")
    }

    private fun dispatch(event: VDEvent?) {
        if (event == null || event.id != VDNaviAreaDisplay.EVENT_ID) return
        val value = VDNaviAreaDisplay.getValue(event) ?: return
        onAreaRequest(value.requestNaviAreaDisplay)
    }

    private fun subscribeEvent(): VDEvent =
        VDEvent(VDNaviAreaDisplay.EVENT_ID, Bundle()).also { it.createTimeMillis() }

    private fun packagePresent(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(DesayVdbClient.CARLAN_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) {
                binder = service
                binding = false
                Log.i(TAG, "CarLan VDB connected for navi presence ($name)")
                ensureSubscribedLocked()
                if (lastAvailable || clusterMapShown) {
                    publishPresenceLocked(available = true, area = lastArea, force = true)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            val retry: Boolean
            synchronized(lock) {
                binder = null
                binding = false
                subscribed = false
                notifyStub = null
                retry = app != null
                Log.w(TAG, "CarLan VDB disconnected (navi presence)")
            }
            if (retry) {
                handler.postDelayed({
                    synchronized(lock) {
                        if (app != null && binder == null) ensureBoundLocked()
                    }
                }, RETRY_MILLIS)
            }
        }
    }

    private fun transactSubscribe(remote: IBinder, event: VDEvent, notify: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DesayVdbClient.IVDBUS_DESCRIPTOR)
            data.writeInt(1)
            event.writeToParcel(data, 0)
            data.writeStrongBinder(notify)
            remote.transact(TRANSACTION_SUBSCRIBE_CUSTOMIZED, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactUnsubscribe(remote: IBinder, event: VDEvent, notify: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DesayVdbClient.IVDBUS_DESCRIPTOR)
            data.writeInt(1)
            event.writeToParcel(data, 0)
            data.writeStrongBinder(notify)
            remote.transact(TRANSACTION_UNSUBSCRIBE_CUSTOMIZED, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private class NotifyStub(
        private val onNotify: (VDEvent?) -> Unit,
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(IVDBUS_NOTIFY_DESCRIPTOR)
                return true
            }
            if (code != TRANSACTION_ON_NOTIFY) {
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(IVDBUS_NOTIFY_DESCRIPTOR)
            val event = if (data.readInt() != 0) {
                VDEvent.CREATOR.createFromParcel(data)
            } else {
                null
            }
            onNotify(event)
            return true
        }
    }
}
