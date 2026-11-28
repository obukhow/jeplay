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
import com.desaysv.ivi.vdb.event.base.VDKey
import com.desaysv.ivi.vdb.event.id.sms.VDEventSms
import com.desaysv.ivi.vdb.event.id.sms.VDValueKey

/**
 * Jaecoo/Desay steering-wheel keys over VDB SMS.
 *
 * The voice button is not an Android [android.view.KeyEvent]: `com.desaysv.ivi.vds.sms` publishes
 * [VDEventSms.ID_SMS_KEY_EVENT] with `type` / `action`. The OEM CarPlay stack and other Desay clients
 * subscribe the same way. This listener mirrors that subscribe and forwards the VR short-click
 * to CarPlay Siri while a session is active.
 *
 * VDB is fan-out — other subscribers (OEM CarPlay, iFlytek) still receive the same event.
 */
object DesaySmsKeys {
    private const val TAG = "DiPlay-DesaySms"
    private const val SMS_PACKAGE = "com.desaysv.ivi.vds.sms"
    private const val SMS_ACTION = "action.desaysv.ivi.vds.sms.SERVICE"
    private const val RETRY_MILLIS = 3_000L
    private const val IVDBUS_DESCRIPTOR = DesayVdbClient.IVDBUS_DESCRIPTOR
    private const val IVDBUS_NOTIFY_DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBusNotify"
    private const val TRANSACTION_SUBSCRIBE_CUSTOMIZED = 4
    private const val TRANSACTION_UNSUBSCRIBE_CUSTOMIZED = 5
    private const val TRANSACTION_ON_NOTIFY = 1

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var app: Context? = null
    private var onVrRelease: (() -> Unit)? = null
    private var binder: IBinder? = null
    private var binding = false
    private var subscribed = false
    private var notifyStub: NotifyStub? = null

    fun available(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SMS_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Binds and subscribes while CarPlay is active; safe to call repeatedly. */
    fun start(context: Context, onVrRelease: () -> Unit) {
        synchronized(lock) {
            this.onVrRelease = onVrRelease
            app = context.applicationContext
            if (!available(context.applicationContext)) {
                Log.i(TAG, "SMS VDB package missing; wheel VR key ignored")
                return
            }
            ensureBoundLocked()
        }
    }

    /** Unsubscribes and unbinds when CarPlay ends. */
    fun stop() {
        synchronized(lock) {
            val remote = binder
            val stub = notifyStub
            if (remote != null && stub != null && subscribed) {
                runCatching { transactUnsubscribe(remote, subscribeEvent(), stub) }
            }
            subscribed = false
            notifyStub = null
            onVrRelease = null
            handler.removeCallbacksAndMessages(null)
            val ctx = app
            if (ctx != null && (binder != null || binding)) {
                runCatching { ctx.unbindService(connection) }
            }
            binder = null
            binding = false
            app = null
        }
    }

    private fun ensureBoundLocked() {
        val ctx = app ?: return
        if (binder != null) {
            ensureSubscribedLocked()
            return
        }
        if (binding) return
        binding = true
        val intent = Intent(SMS_ACTION).setPackage(SMS_PACKAGE)
        try {
            val ok = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!ok) {
                binding = false
                Log.w(TAG, "bindService($SMS_PACKAGE) returned false")
            } else {
                Log.i(TAG, "bind SMS VDB requested")
            }
        } catch (e: SecurityException) {
            binding = false
            Log.w(TAG, "bindService denied: ${e.message}")
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
        }.onFailure { Log.w(TAG, "subscribeCustomizedEvent failed", it) }.getOrDefault(false)
        subscribed = ok
        if (ok) Log.i(TAG, "subscribed SMS key event types=[${VDValueKey.KEYCODE_VR}]")
    }

    private fun dispatch(event: VDEvent?) {
        if (event == null || event.id != VDEventSms.ID_SMS_KEY_EVENT) return
        val payload = event.payload ?: return
        val type = payload.getInt(VDKey.TYPE, Int.MIN_VALUE)
        val action = payload.getInt(VDKey.ACTION, Int.MIN_VALUE)
        if (!DesaySmsKeyPolicy.opensSiri(type, action)) return
        val callback = synchronized(lock) { onVrRelease } ?: return
        Log.i(TAG, "VR release type=$type action=$action → Siri")
        runCatching { callback() }
            .onFailure { Log.w(TAG, "Siri callback failed", it) }
    }

    private fun subscribeEvent(): VDEvent {
        val payload = Bundle()
        payload.putIntArray(VDKey.TYPE, intArrayOf(VDValueKey.KEYCODE_VR))
        return VDEvent(VDEventSms.ID_SMS_KEY_EVENT, payload).also { it.createTimeMillis() }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) {
                binder = service
                binding = false
                Log.i(TAG, "SMS VDB connected ($name)")
                ensureSubscribedLocked()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            val retry: Boolean
            synchronized(lock) {
                binder = null
                binding = false
                subscribed = false
                notifyStub = null
                retry = app != null && onVrRelease != null
                Log.w(TAG, "SMS VDB disconnected")
            }
            if (retry) {
                handler.postDelayed({
                    synchronized(lock) {
                        if (app != null && onVrRelease != null && binder == null) {
                            ensureBoundLocked()
                        }
                    }
                }, RETRY_MILLIS)
            }
        }
    }

    private fun transactSubscribe(remote: IBinder, event: VDEvent, notify: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(IVDBUS_DESCRIPTOR)
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
            data.writeInterfaceToken(IVDBUS_DESCRIPTOR)
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

    /**
     * Local [IVDBusNotify] endpoint. Do not [attachInterface]: the SMS service must take the
     * Proxy/transact path (we are not the OEM [IVDBusNotify] type).
     */
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
