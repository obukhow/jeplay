package com.shilapi.xcertplay.media

import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.base.VDKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Opens Desay DSP [CHANNEL_NAVI] while CarPlay navigation guidance is focused.
 *
 * OEM Android Auto does this on nav focus (`p1.h` → [VDEventDsp.CHANNEL] with
 * `type` + `enable`). Stock CarPlay relies on the privileged phonelink path; JePlay is a
 * third-party app, so BUS02 alone still mixes to the cabin unless the amp channel is armed.
 * Headrest mix for navigation is the DSP nav channel (see [VDValueDsp.DspChannelType.CHANNEL_NAVI]).
 */
object DesayDspNavChannel {
    private const val TAG = "DiPlay-DesayDsp"
    private const val DSP_SERVICE = "com.desaysv.ivi.vds.dsp.service.DspService"
    private const val IVDBUS_DESCRIPTOR = "com.desaysv.ivi.vdb.IVDBus"
    /** [com.desaysv.ivi.vdb.IVDBus.Stub.TRANSACTION_get] */
    private const val TRANSACTION_GET = 1
    /** [com.desaysv.ivi.vdb.IVDBus.Stub.TRANSACTION_set] — SoundSettingModel.setDSPChannel */
    private const val TRANSACTION_SET = 2

    /** [com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp.CHANNEL] */
    private const val EVENT_CHANNEL = 983041

    /** [com.desaysv.ivi.vdb.event.id.dsp.VDEventDsp.DSP_CUSTOM_EFFECT] */
    private const val EVENT_CUSTOM_EFFECT = 983243

    /** [com.desaysv.ivi.vdb.event.id.dsp.VDValueDsp.DspChannelType.CHANNEL_NAVI] */
    private const val CHANNEL_NAVI = 3

    /**
     * OEM Android Auto / VR guidance shares the driver-headrest amp bus on channel 4.
     * Arm both so either amp table matches.
     */
    private const val CHANNEL_VR = 4

    /**
     * [VDValueDsp.DspEffectType.DSP_EFFECT_HEADREST_SPEAKER_MODE] — SoundSettingModel
     * get/setHeadRestSpeaker.
     */
    private const val EFFECT_HEADREST = 11

    /** [VDValueDsp.ExtAMPHeadrestSpeakerMode.HEADREST_SPEAKER_DRIVE_MODE] */
    private const val HEADREST_DRIVE = 2

    private val active = AtomicBoolean(false)
    private val savedHeadrest = AtomicInteger(-1)

    fun setNavigationActive(enabled: Boolean) {
        if (active.getAndSet(enabled) == enabled) return
        val remote = dspBinder() ?: run {
            Log.w(TAG, "DSP binder missing; cannot arm headrest path")
            if (!enabled) active.set(false)
            return
        }
        if (enabled) {
            ensureHeadrestDrive(remote)
        }
        val okNavi = setChannel(remote, CHANNEL_NAVI, enabled)
        val okVr = setChannel(remote, CHANNEL_VR, enabled)
        val readNavi = getChannel(remote, CHANNEL_NAVI)
        val readVr = getChannel(remote, CHANNEL_VR)
        val headrest = getHeadrestMode(remote)
        Log.i(
            TAG,
            "DSP CHANNEL_NAVI/VR enable=$enabled set=($okNavi,$okVr) " +
                "read=(navi=$readNavi,vr=$readVr) headrestMode=$headrest",
        )
        if (!enabled) {
            restoreHeadrest(remote)
        }
    }

    private fun ensureHeadrestDrive(remote: IBinder) {
        val current = getHeadrestMode(remote)
        savedHeadrest.set(current)
        // DRIVE (2) and PRIVATE (3) already feed the headrest amp; SHARED/OFF do not.
        if (current == HEADREST_DRIVE || current == 3) return
        val ok = setHeadrestMode(remote, HEADREST_DRIVE)
        Log.i(TAG, "DSP headrest mode $current → $HEADREST_DRIVE ok=$ok")
    }

    private fun restoreHeadrest(remote: IBinder) {
        val previous = savedHeadrest.getAndSet(-1)
        if (previous < 0 || previous == HEADREST_DRIVE || previous == 3) return
        val ok = setHeadrestMode(remote, previous)
        Log.i(TAG, "DSP headrest mode restored → $previous ok=$ok")
    }

    private fun setChannel(remote: IBinder, type: Int, enable: Boolean): Boolean {
        val payload = Bundle().apply {
            putInt(VDKey.TYPE, type)
            putBoolean(VDKey.ENABLE, enable)
        }
        return transactSet(remote, VDEvent(EVENT_CHANNEL, payload))
    }

    private fun getChannel(remote: IBinder, type: Int): Boolean? {
        val payload = Bundle().apply { putInt(VDKey.TYPE, type) }
        val once = transactGet(remote, VDEvent(EVENT_CHANNEL, payload)) ?: return null
        return once.payload?.getBoolean(VDKey.ENABLE)
    }

    private fun getHeadrestMode(remote: IBinder): Int {
        val payload = Bundle().apply { putInt(VDKey.TYPE, EFFECT_HEADREST) }
        val once = transactGet(remote, VDEvent(EVENT_CUSTOM_EFFECT, payload))
        return once?.payload?.getInt(VDKey.VALUE, -1) ?: -1
    }

    private fun setHeadrestMode(remote: IBinder, mode: Int): Boolean {
        val payload = Bundle().apply {
            putInt(VDKey.TYPE, EFFECT_HEADREST)
            putInt(VDKey.VALUE, mode)
        }
        return transactSet(remote, VDEvent(EVENT_CUSTOM_EFFECT, payload))
    }

    private fun dspBinder(): IBinder? = runCatching {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, DSP_SERVICE) as? IBinder
    }.onFailure { Log.w(TAG, "ServiceManager.getService($DSP_SERVICE): ${it.message}") }
        .getOrNull()

    private fun transactSet(remote: IBinder, event: VDEvent): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            event.createTimeMillis()
            data.writeInterfaceToken(IVDBUS_DESCRIPTOR)
            data.writeInt(1)
            event.writeToParcel(data, 0)
            remote.transact(TRANSACTION_SET, data, reply, 0)
            reply.readException()
            true
        } catch (e: Exception) {
            Log.w(TAG, "DSP set id=${event.id} failed: ${e.message}")
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactGet(remote: IBinder, event: VDEvent): VDEvent? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            event.createTimeMillis()
            data.writeInterfaceToken(IVDBUS_DESCRIPTOR)
            data.writeInt(1)
            event.writeToParcel(data, 0)
            remote.transact(TRANSACTION_GET, data, reply, 0)
            reply.readException()
            if (reply.readInt() == 0) null
            else VDEvent.CREATOR.createFromParcel(reply)
        } catch (e: Exception) {
            Log.w(TAG, "DSP get id=${event.id} failed: ${e.message}")
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
