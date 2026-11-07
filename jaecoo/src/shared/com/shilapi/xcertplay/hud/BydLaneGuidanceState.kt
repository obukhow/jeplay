package com.shilapi.xcertplay.hud

/**
 * iAP2 lane guidance: cache [LaneGuidanceInfoUpdate] (`0x5204`) by event index, and select the
 * active strip from [RouteGuidanceUpdate] (`0x5201`) TLVs 0x10 / 0x12.
 *
 * iOS sends a window of up to MaxLaneGuidanceStorageCapacity events (starting at the current
 * index) **before** the 0x5201 that makes one of them current — keeping only the latest 0x5204
 * would show a future strip.
 */
internal data class BydLaneGuidance(
    val lanes: List<BydLane>,
) {
    data class BydLane(
        val index: Int,
        /** 0 not recommended, 1 good, 2 preferred. */
        val status: Int,
        val anglesDegrees: List<Int>,
        val highlightedDegrees: Int?,
    )
}

internal class BydLaneGuidanceState {
    private val cache = linkedMapOf<Int, BydLaneGuidance>()
    private var currentIndex: Int? = null
    private var showing = false

    fun accept(messageId: Int, payload: ByteArray): Boolean {
        if (!validTlvs(payload)) return false
        return when (messageId) {
            LANE_GUIDANCE_UPDATE -> acceptLaneEvent(payload)
            BydHudRouteState.ROUTE_GUIDANCE_UPDATE -> acceptRouteUpdate(payload)
            else -> false
        }
    }

    /**
     * Active strip for the HUD, or null when iOS says lanes are hidden / not yet selected.
     * Empty list means "clear the previous strip".
     */
    fun current(): BydLaneGuidance? {
        if (!showing) return BydLaneGuidance(emptyList()).takeIf { cache.isNotEmpty() || currentIndex != null }
        val index = currentIndex ?: return null
        return cache[index]
    }

    fun clear() {
        cache.clear()
        currentIndex = null
        showing = false
    }

    private fun acceptLaneEvent(data: ByteArray): Boolean {
        var eventIndex: Int? = null
        // iAP2 repeats group parameters as sibling TLVs with the same ID — each 0x02 is
        // one LaneGuidanceInfo. Keeping only the last blob dropped every left-hand lane.
        val laneBlobs = mutableListOf<ByteArray>()
        forEachTlv(data) { id, value, valueLength ->
            when (id) {
                0x01 -> if (valueLength >= 2) eventIndex = u16(data, value)
                0x02 -> laneBlobs += data.copyOfRange(value, value + valueLength)
            }
        }
        val index = eventIndex ?: return false
        val lanes = laneBlobs.flatMap(::parseLanes)
        cache[index] = BydLaneGuidance(lanes)
        // Yandex often sends 0x5204 without ever setting LaneGuidanceShowing (0x12).
        // Treat the first non-empty event as current+visible; later indices stay precache.
        if (lanes.isNotEmpty() && currentIndex == null) {
            currentIndex = index
            showing = true
        }
        // Drop events below the oldest window we still need (iOS never reuses lower indices).
        while (cache.size > MAX_CACHE) {
            val first = cache.keys.firstOrNull() ?: break
            if (first == currentIndex) break
            cache.remove(first)
        }
        return true
    }

    private fun acceptRouteUpdate(data: ByteArray): Boolean {
        var changed = false
        var state: Int? = null
        var indexUpdate: Int? = null
        var showingUpdate: Boolean? = null
        forEachTlv(data) { id, value, valueLength ->
            when (id) {
                0x01 -> if (valueLength >= 1) state = data[value].toInt() and 0xff
                0x10 -> if (valueLength >= 2) {
                    indexUpdate = u16(data, value)
                }
                0x12 -> if (valueLength >= 1) {
                    showingUpdate = (data[value].toInt() and 0xff) != 0
                }
            }
        }
        indexUpdate?.let { next ->
            if (currentIndex != next) {
                currentIndex = next
                changed = true
            }
            // iOS never needs indices below the current one again.
            cache.keys.filter { it < next }.forEach { cache.remove(it) }
            // Selecting an index without an explicit hide means "show this strip".
            if (showingUpdate == null && !showing) {
                showing = true
                changed = true
            }
        }
        showingUpdate?.let { next ->
            if (showing != next) {
                showing = next
                changed = true
            }
        }
        // NoRouteSet / Arrived: drop the strip with the route.
        if (state == 0 || state == 2) {
            if (cache.isNotEmpty() || showing || currentIndex != null) {
                clear()
                changed = true
            }
        }
        return changed
    }

    private fun parseLanes(data: ByteArray): List<BydLaneGuidance.BydLane> {
        if (!validTlvs(data)) return emptyList()
        val lanes = mutableListOf<BydLaneGuidance.BydLane>()
        var index = lanes.size
        var status = 0
        var angles = listOf<Int>()
        var highlighted: Int? = null
        var haveLane = false

        fun flush() {
            if (!haveLane) return
            lanes += BydLaneGuidance.BydLane(index, status, angles, highlighted)
            haveLane = false
            index = lanes.size
            status = 0
            angles = emptyList()
            highlighted = null
        }

        forEachTlv(data) { id, value, valueLength ->
            when (id) {
                0x00 -> { // LaneIndex — starts a new lane
                    flush()
                    if (valueLength >= 2) index = u16(data, value)
                    haveLane = true
                }
                0x01 -> {
                    if (!haveLane) haveLane = true
                    if (valueLength >= 1) status = data[value].toInt() and 0xff
                }
                0x02 -> {
                    // LaneAngle is also a repeated parameter (one angle per TLV).
                    if (!haveLane) haveLane = true
                    angles = angles + parseAngles(data, value, valueLength)
                }
                0x03 -> {
                    if (!haveLane) haveLane = true
                    if (valueLength >= 2) {
                        val angle = i16(data, value)
                        // Apple sentinel 1000 = "no highlight".
                        highlighted = angle.takeUnless { it == UNKNOWN_ANGLE }
                    }
                }
            }
        }
        flush()
        return lanes
    }

    private fun parseAngles(data: ByteArray, offset: Int, length: Int): List<Int> {
        val out = ArrayList<Int>(length / 2)
        var i = 0
        while (i + 1 < length) {
            val angle = i16(data, offset + i)
            if (angle != UNKNOWN_ANGLE) out += angle
            i += 2
        }
        return out
    }

    private inline fun forEachTlv(data: ByteArray, block: (Int, Int, Int) -> Unit) {
        var offset = 0
        while (offset < data.size) {
            val length = u16(data, offset)
            block(u16(data, offset + 2), offset + TLV_HEADER, length - TLV_HEADER)
            offset += length
        }
    }

    private fun validTlvs(data: ByteArray): Boolean {
        var offset = 0
        while (offset < data.size) {
            if (offset + TLV_HEADER > data.size) return false
            val length = u16(data, offset)
            if (length < TLV_HEADER || length > data.size - offset) return false
            offset += length
        }
        return offset == data.size
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun i16(data: ByteArray, offset: Int): Int {
        val u = u16(data, offset)
        return if (u >= 0x8000) u - 0x10000 else u
    }

    companion object {
        const val LANE_GUIDANCE_UPDATE = 0x5204
        private const val TLV_HEADER = 4
        private const val MAX_CACHE = 8
        private const val UNKNOWN_ANGLE = 1000
    }
}
