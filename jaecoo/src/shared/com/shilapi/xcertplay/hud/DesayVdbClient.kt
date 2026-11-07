package com.shilapi.xcertplay.hud

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.desaysv.ivi.vdb.event.VDEvent

/**
 * Thin publisher for Desay VDB services (CarLan HUD, Media now-playing, …).
 *
 * Binds [packageName] / [serviceAction] and calls AIDL transact 2 (`IVDBus.set`).
 *
 * CarLan (default): send the real event id, same as YaSwitcher — HUD beans are
 * Parcelables that OEM [VDBinder.set] accepts via `onSet`. Do **not** inject here:
 * inject reads `InjectEventID` before setClassLoader, CNFEs on those Parcelables,
 * and silently drops the turn.
 *
 * Media: plain `set(realId)` is rejected ("can not be setting"); pass
 * [serviceMinEventId] so the client wraps as `id = min` + `InjectEventID`.
 */
internal class DesayVdbClient(
    private val publishHook: ((VDEvent) -> Boolean)? = null,
    private val packageName: String = CARLAN_PACKAGE,
    private val serviceAction: String = CARLAN_ACTION,
    private val logLabel: String = "CarLan",
    /** When non-null, wrap as InjectEventID envelope (Media VDB). Null for CarLan. */
    private val serviceMinEventId: Int? = null,
) {
    private val lock = Any()
    private var app: Context? = null
    private var binder: IBinder? = null
    private var binding = false
    private var loggedBind = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) {
                binder = service
                binding = false
                if (!loggedBind) {
                    loggedBind = true
                    Log.i(TAG, "$logLabel VDB connected ($name)")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                binder = null
                binding = false
                Log.w(TAG, "$logLabel VDB disconnected")
            }
        }
    }

    fun initialize(context: Context) = synchronized(lock) {
        if (app != null) return
        app = context.applicationContext
        ensureBoundLocked()
    }

    fun available(context: Context? = app): Boolean {
        if (publishHook != null) return true // test / injected publisher
        val ctx = context ?: return false
        return try {
            ctx.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun publish(event: VDEvent): Boolean {
        publishHook?.let { return it(event) }
        synchronized(lock) {
            val ctx = app ?: return false
            ensureBoundLocked()
            val remote = binder
            if (remote == null) {
                // Queue-less: try bind again next tick.
                return false
            }
            return transactPublish(remote, event)
        }
    }

    fun release() = synchronized(lock) {
        val ctx = app
        if (ctx != null && (binder != null || binding)) {
            try {
                ctx.unbindService(connection)
            } catch (_: Exception) {
            }
        }
        binder = null
        binding = false
        app = null
        loggedBind = false
    }

    private fun ensureBoundLocked() {
        val ctx = app ?: return
        if (binder != null || binding) return
        if (!available(ctx)) return
        binding = true
        val intent = Intent(serviceAction).setPackage(packageName)
        try {
            val ok = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!ok) {
                binding = false
                Log.w(TAG, "bindService($packageName) returned false")
            }
        } catch (e: SecurityException) {
            binding = false
            Log.w(TAG, "bindService denied: ${e.message}")
        } catch (e: Exception) {
            binding = false
            Log.w(TAG, "bindService failed: ${e.message}")
        }
    }

    private fun transactPublish(remote: IBinder, event: VDEvent): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val wire = toWireEvent(event)
            wire.createTimeMillis()
            data.writeInterfaceToken(IVDBUS_DESCRIPTOR)
            data.writeInt(1)
            wire.writeToParcel(data, 0)
            // IVDBus.TRANSACTION_set — clients have no direct publish; inject or onSet.
            remote.transact(TRANSACTION_SET, data, reply, 0)
            reply.readException()
            true
        } catch (e: Exception) {
            Log.w(TAG, "publish id=${event.id} failed: ${e.message}")
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Wrap as inject envelope when talking to a ranged VDB service. */
    private fun toWireEvent(event: VDEvent): VDEvent {
        val min = serviceMinEventId ?: return event
        if (event.id == min) return event
        val payload = Bundle(event.payload ?: Bundle())
        payload.putInt(INJECT_EVENT_ID, event.id)
        return VDEvent(min, payload)
    }

    companion object {
        private const val TAG = "DiPlay-DesayVdb"
        const val CARLAN_PACKAGE = "com.desaysv.ivi.vds.carlan"
        const val CARLAN_ACTION = "action.desaysv.ivi.vds.carlan.SERVICE"
        const val CARLAN_MIN = 720896
        const val MEDIA_PACKAGE = "com.desaysv.ivi.vds.media"
        const val MEDIA_ACTION = "action.desaysv.ivi.vds.media.SERVICE"
        const val MEDIA_MIN = 393216
        const val IVDBUS_DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBus"
        /** VDBinder InjectEventID key. */
        const val INJECT_EVENT_ID = "InjectEventID"
        /** IVDBus.Stub.TRANSACTION_set. */
        const val TRANSACTION_SET = 2

        fun media(publishHook: ((VDEvent) -> Boolean)? = null) = DesayVdbClient(
            publishHook = publishHook,
            packageName = MEDIA_PACKAGE,
            serviceAction = MEDIA_ACTION,
            logLabel = "Media",
            serviceMinEventId = MEDIA_MIN,
        )
    }
}
