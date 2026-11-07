package com.shilapi.xcertplay.hud

import android.util.Log

/**
 * One-line summaries of CarPlay route/lane frames so a live Yandex/2GIS session can be
 * compared against the HUD probe without guessing what the phone sent.
 *
 * Filter: `adb logcat -s DiPlay-JaecooCarPlay`
 */
internal object JaecooCarPlayHudDiag {
    private const val TAG = "DiPlay-JaecooCarPlay"
    private var lastSignature = ""

    /** Deduped one-liner for logcat + diplay.log / diagnostic export. */
    fun summarizeForSession(messageId: Int, payload: ByteArray): String? {
        val summary = when (messageId) {
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE -> summarizeRoute(payload)
            BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE -> summarizeManeuver(payload)
            BydLaneGuidanceState.LANE_GUIDANCE_UPDATE -> summarizeLanes(payload)
            else -> null
        } ?: return null
        if (summary == lastSignature) return null
        lastSignature = summary
        Log.i(TAG, summary)
        return summary
    }

    fun onHudPublish(
        type: Int,
        icon: Int,
        distanceM: Int,
        road: String,
        laneCount: Int,
        laneShowing: Boolean,
        laneIcons: String = "",
    ) {
        val line =
            "→VDB type=$type icon=$icon dist=${distanceM}m road='${road.ifEmpty { "∅" }}' " +
                "lanes=$laneCount showing=$laneShowing" +
                if (laneIcons.isNotEmpty()) " icons=$laneIcons" else ""
        if (line == lastSignature) return
        lastSignature = line
        Log.i(TAG, line)
        BydNavigationOutputs.sessionNote?.invoke(line)
    }

    fun reset() {
        lastSignature = ""
    }

    private fun summarizeRoute(data: ByteArray): String? {
        if (!validTlvs(data)) return "0x5201 malformed len=${data.size}"
        var state: Int? = null
        var distance: Int? = null
        var currentRoad: String? = null
        var firstManeuver: Int? = null
        var listEmpty = false
        var laneIndex: Int? = null
        var laneShowing: Int? = null
        forEachTlv(data) { id, value, valueLength ->
            when (id) {
                0x01 -> if (valueLength >= 1) state = data[value].toInt() and 0xff
                0x03 -> currentRoad = utf8(data, value, valueLength)
                0x0a -> if (valueLength >= 4) distance = u32(data, value).toInt()
                0x0d -> {
                    if (valueLength >= 2) firstManeuver = u16(data, value)
                    else listEmpty = true
                }
                0x10 -> if (valueLength >= 2) laneIndex = u16(data, value)
                0x12 -> if (valueLength >= 1) laneShowing = data[value].toInt() and 0xff
            }
        }
        return "0x5201 state=$state dist=$distance mIdx=$firstManeuver listEmpty=$listEmpty " +
            "curRoad='${currentRoad ?: "—"}' laneIdx=$laneIndex laneShow=$laneShowing"
    }

    private fun summarizeManeuver(data: ByteArray): String? {
        if (!validTlvs(data)) return "0x5202 malformed len=${data.size}"
        var index: Int? = null
        var type: Int? = null
        var afterRoad: String? = null
        var afterPresent = false
        var description: String? = null
        var descPresent = false
        var drivingSide: Int? = null
        forEachTlv(data) { id, value, valueLength ->
            when (id) {
                0x01 -> if (valueLength >= 2) index = u16(data, value)
                0x02 -> {
                    descPresent = true
                    description = utf8(data, value, valueLength)
                }
                0x03 -> if (valueLength >= 1) type = data[value].toInt() and 0xff
                0x04 -> {
                    afterPresent = true
                    afterRoad = utf8(data, value, valueLength)
                }
                0x08 -> if (valueLength >= 1) drivingSide = data[value].toInt() and 0xff
            }
        }
        val roadTag = when {
            !afterPresent -> "afterRoad=ABSENT"
            afterRoad.isNullOrEmpty() -> "afterRoad=EMPTY"
            else -> "afterRoad='${afterRoad}'"
        }
        val descTag = when {
            !descPresent -> "desc=ABSENT"
            description.isNullOrEmpty() -> "desc=EMPTY"
            else -> "desc='${description!!.take(40)}'"
        }
        return "0x5202 idx=$index type=$type side=$drivingSide $roadTag $descTag"
    }

    private fun summarizeLanes(data: ByteArray): String? {
        if (!validTlvs(data)) return "0x5204 malformed len=${data.size}"
        var eventIndex: Int? = null
        var laneBytes = 0
        // Repeated 0x02 = one LaneGuidanceInfo each (same as BydLaneGuidanceState).
        val laneBlobs = mutableListOf<ByteArray>()
        forEachTlv(data) { id, value, valueLength ->
            when (id) {
                0x01 -> if (valueLength >= 2) eventIndex = u16(data, value)
                0x02 -> {
                    laneBytes += valueLength
                    laneBlobs += data.copyOfRange(value, value + valueLength)
                }
            }
        }
        val parts = laneBlobs.flatMap(::summarizeLaneBlob)
        val detail = if (parts.isEmpty()) "" else " lanes=${parts.size}${parts.joinToString("")}"
        return "0x5204 eventIdx=$eventIndex laneBlobBytes=$laneBytes$detail"
    }

    /** Compact per-lane dump parts: `[i:st=2 ang=-90,+90 hi=-90]`. */
    private fun summarizeLaneBlob(blob: ByteArray): List<String> {
        if (!validTlvs(blob)) return listOf("[PARSE_FAIL]")
        val parts = mutableListOf<String>()
        var index = 0
        var status = 0
        var angles = mutableListOf<Int>()
        var highlighted: Int? = null
        var have = false
        fun flush() {
            if (!have) return
            val ang = angles.joinToString(",")
            val hi = highlighted?.toString() ?: "—"
            parts += "[$index:st=$status ang=$ang hi=$hi]"
            have = false
            index = parts.size
            status = 0
            angles = mutableListOf()
            highlighted = null
        }
        forEachTlv(blob) { id, value, valueLength ->
            when (id) {
                0x00 -> {
                    flush()
                    if (valueLength >= 2) index = u16(blob, value)
                    have = true
                }
                0x01 -> {
                    if (!have) have = true
                    if (valueLength >= 1) status = blob[value].toInt() and 0xff
                }
                0x02 -> {
                    // Repeated LaneAngle TLVs — accumulate, do not replace.
                    if (!have) have = true
                    var i = 0
                    while (i + 1 < valueLength) {
                        val u = u16(blob, value + i)
                        val a = if (u >= 0x8000) u - 0x10000 else u
                        if (a != 1000) angles += a
                        i += 2
                    }
                }
                0x03 -> {
                    if (!have) have = true
                    if (valueLength >= 2) {
                        val u = u16(blob, value)
                        val a = if (u >= 0x8000) u - 0x10000 else u
                        highlighted = a.takeUnless { it == 1000 }
                    }
                }
            }
        }
        flush()
        return parts
    }

    private inline fun forEachTlv(data: ByteArray, block: (Int, Int, Int) -> Unit) {
        var offset = 0
        while (offset < data.size) {
            val length = u16(data, offset)
            block(u16(data, offset + 2), offset + 4, length - 4)
            offset += length
        }
    }

    private fun validTlvs(data: ByteArray): Boolean {
        var offset = 0
        while (offset < data.size) {
            if (offset + 4 > data.size) return false
            val length = u16(data, offset)
            if (length < 4 || length > data.size - offset) return false
            offset += length
        }
        return offset == data.size
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun u32(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xff) shl 24) or
            ((data[offset + 1].toLong() and 0xff) shl 16) or
            ((data[offset + 2].toLong() and 0xff) shl 8) or
            (data[offset + 3].toLong() and 0xff)

    private fun utf8(data: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && data[end] != 0.toByte()) end++
        return String(data, offset, end - offset, Charsets.UTF_8).trim()
    }
}
