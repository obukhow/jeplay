package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.hud.DesayLaneIcons.LEFT90
import com.shilapi.xcertplay.hud.DesayLaneIcons.RIGHT90
import com.shilapi.xcertplay.hud.DesayLaneIcons.STRAIGHT

/**
 * Settings HUD probe: fixed 3-lane scene + next-street name encodings.
 *
 * Lanes (driver view, left → right):
 * - left: straight + left, **white on left**
 * - middle: straight, gray
 * - right: right, gray
 *
 * Street: distinctive Cyrillic «ул. Ленина» so it is obvious on the HUD.
 */
object JaecooLaneProbe {
    const val HOLD_MS = 25_000L
    const val PROBE_NEXT_STREET = "ул. Ленина"
    const val PROBE_CURRENT_STREET = "пр. Мира"
    const val PROBE_NEXT_LATIN = "Lenina St"

    data class Scheme(val id: String, val title: String, val detail: String)

    val laneSchemes: List<Scheme> = listOf(
        Scheme("yaswitcher", "1 · YaSwitcher (LaneInfo=1 + iconId)",
            "Exact YaSwitcher: LaneInfo always 1; white/gray in LaneIconId"),
        Scheme("yaswitcher_mirrored", "2 · YaSwitcher mirrored L↔R",
            "Same as #1 after Apple angle negate (production pipeline)"),
        Scheme("laneinfo_12_bg", "3 · LaneInfo 2/1 + background 0–12",
            "White=2 gray=1; LaneIconId = ||dirs only (no highlight id)"),
        Scheme("laneinfo_12_icon", "4 · LaneInfo 2/1 + full iconId",
            "White=2 gray=1, but LaneIconId still has highlight||dirs"),
        Scheme("laneinfo_10_icon", "5 · LaneInfo 1/0 + iconId",
            "Recommended=1, others=0; full iconId on all"),
        Scheme("all_bg", "6 · All LaneInfo=1, background only",
            "No highlight in icons — should look all gray"),
        Scheme("hardcoded_31_0_3", "7 · Hardcoded icons 31 / 0 / 3",
            "LEFT90||LEFT90|STRAIGHT=31, ||STRAIGHT=0, ||RIGHT90=3"),
        Scheme("hardcoded_65_0_1", "8 · Hardcoded singles 65 / 0 / 1",
            "RIGHT90||RIGHT90=65; straight; LEFT90 gray"),
        Scheme("apple_pipeline", "9 · Via Apple angles (live encoder)",
            "BydLaneGuidance → current publishLanes() path"),
    )

    val roadSchemes: List<Scheme> = listOf(
        Scheme("road_both", "A · RoadName = NextRoadName = ул. Ленина",
            "Current production: both VDB fields + Autonavi NEXT_ROAD_NAME"),
        Scheme("road_next_only", "B · только NextRoadName",
            "RoadName empty; NextRoadName = ул. Ленина"),
        Scheme("road_name_only", "C · только RoadName",
            "NextRoadName empty; RoadName = ул. Ленина"),
        Scheme("road_swapped", "D · RoadName=пр. Мира, Next=ул. Ленина",
            "Current vs next street separated"),
        Scheme("road_latin", "E · NextRoadName = Lenina St (Latin)",
            "Same as A but Latin — checks Cyrillic encoding"),
        Scheme("road_vdb_only", "F · только VDB (без Autonavi)",
            "Both fields set; no AUTONAVI_STANDARD_BROADCAST_SEND"),
        Scheme("road_autonavi_rich", "G · VDB + Autonavi CUR+NEXT+ROAD",
            "Adds CUR_ROAD_NAME / ROAD_NAME extras"),
        Scheme("road_autonavi_only", "H · только Autonavi broadcast",
            "No VDNaviRoadInfo — if HUD shows the street, it listens to broadcast"),
    )

    /** @deprecated use [laneSchemes]; kept so older UI loops still compile if referenced. */
    val schemes: List<Scheme> get() = laneSchemes

    fun run(context: Context, scheme: Scheme): String {
        JaecooHudBridge.initialize(context.applicationContext)
        return when {
            scheme.id.startsWith("road_") -> runRoad(scheme)
            scheme.id == "apple_pipeline" -> {
                val guidance = BydLaneGuidance(
                    listOf(
                        BydLaneGuidance.BydLane(0, status = 2, anglesDegrees = listOf(0, -90), highlightedDegrees = -90),
                        BydLaneGuidance.BydLane(1, status = 0, anglesDegrees = listOf(0), highlightedDegrees = null),
                        BydLaneGuidance.BydLane(2, status = 0, anglesDegrees = listOf(90), highlightedDegrees = null),
                    ),
                )
                JaecooHudBridge.applyLaneProbeGuidance(
                    guidance,
                    defaultLaneRoad(),
                    HOLD_MS,
                )
                "Probe #9 apple_pipeline + «$PROBE_NEXT_STREET» → 25s"
            }
            else -> {
                val rows = rowsFor(scheme.id)
                JaecooHudBridge.applyLaneProbe(rows, defaultLaneRoad(), HOLD_MS)
                val ids = rows.joinToString { "L${it.laneId}:${it.laneInfo}/${it.laneIconId}" }
                "Probe ${scheme.id} → $ids + «$PROBE_NEXT_STREET» (25s)"
            }
        }
    }

    fun clear(context: Context): String {
        JaecooHudBridge.initialize(context.applicationContext)
        JaecooHudBridge.clearLaneProbe()
        return "Lane/road probe cleared"
    }

    private fun runRoad(scheme: Scheme): String {
        val g = roadGuidance(scheme.id)
        // Keep a known-good lane strip so the HUD looks "navigating".
        val rows = rowsFor("yaswitcher")
        JaecooHudBridge.applyLaneProbe(rows, g, HOLD_MS)
        return "Road ${scheme.id}: Road='${g.roadName}' Next='${g.nextRoadName}' " +
            "auto=${g.autonavi} → 25s"
    }

    private fun defaultLaneRoad() = DesayHudOutput.Guidance(
        icon = DesayManeuverCodes.LEFT,
        distanceMeters = 120,
        roadName = PROBE_NEXT_STREET,
        nextRoadName = PROBE_NEXT_STREET,
        autonavi = DesayHudOutput.AutonaviMode.BOTH,
        curRoadName = PROBE_CURRENT_STREET,
    )

    private fun roadGuidance(id: String): DesayHudOutput.Guidance {
        val baseIcon = DesayManeuverCodes.LEFT
        val dist = 250
        return when (id) {
            "road_both" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_NEXT_STREET, PROBE_NEXT_STREET,
                DesayHudOutput.AutonaviMode.BOTH, PROBE_CURRENT_STREET,
            )
            "road_next_only" -> DesayHudOutput.Guidance(
                baseIcon, dist, "", PROBE_NEXT_STREET,
                DesayHudOutput.AutonaviMode.BOTH, null,
            )
            "road_name_only" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_NEXT_STREET, "",
                DesayHudOutput.AutonaviMode.BOTH, null,
            )
            "road_swapped" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_CURRENT_STREET, PROBE_NEXT_STREET,
                DesayHudOutput.AutonaviMode.BOTH, PROBE_CURRENT_STREET,
            )
            "road_latin" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_NEXT_LATIN, PROBE_NEXT_LATIN,
                DesayHudOutput.AutonaviMode.BOTH, "Mira Ave",
            )
            "road_vdb_only" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_NEXT_STREET, PROBE_NEXT_STREET,
                DesayHudOutput.AutonaviMode.VDB_ONLY, null,
            )
            "road_autonavi_rich" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_NEXT_STREET, PROBE_NEXT_STREET,
                DesayHudOutput.AutonaviMode.BOTH, PROBE_CURRENT_STREET,
            )
            "road_autonavi_only" -> DesayHudOutput.Guidance(
                baseIcon, dist, PROBE_NEXT_STREET, PROBE_NEXT_STREET,
                DesayHudOutput.AutonaviMode.BROADCAST_ONLY, PROBE_CURRENT_STREET,
            )
            else -> defaultLaneRoad()
        }
    }

    private fun rowsFor(id: String): List<DesayHudOutput.LaneRow> {
        val desayLeft = listOf(LEFT90, STRAIGHT)
        val desayMid = listOf(STRAIGHT)
        val desayRight = listOf(RIGHT90)
        val mirroredLeft = listOf(RIGHT90, STRAIGHT)
        val mirroredMid = listOf(STRAIGHT)
        val mirroredRight = listOf(LEFT90)
        fun row(laneInfo: Int, laneId: Int, iconId: Int) =
            DesayHudOutput.LaneRow(laneInfo, laneId, iconId)
        return when (id) {
            "yaswitcher" -> listOf(
                row(1, 0, DesayLaneIcons.iconId(LEFT90, desayLeft)),
                row(1, 1, DesayLaneIcons.iconId(null, desayMid)),
                row(1, 2, DesayLaneIcons.iconId(null, desayRight)),
            )
            "yaswitcher_mirrored" -> listOf(
                row(1, 0, DesayLaneIcons.iconId(RIGHT90, mirroredLeft)),
                row(1, 1, DesayLaneIcons.iconId(null, mirroredMid)),
                row(1, 2, DesayLaneIcons.iconId(null, mirroredRight)),
            )
            "laneinfo_12_bg" -> listOf(
                row(2, 0, DesayLaneIcons.backgroundIconId(desayLeft)),
                row(1, 1, DesayLaneIcons.backgroundIconId(desayMid)),
                row(1, 2, DesayLaneIcons.backgroundIconId(desayRight)),
            )
            "laneinfo_12_icon" -> listOf(
                row(2, 0, DesayLaneIcons.iconId(LEFT90, desayLeft)),
                row(1, 1, DesayLaneIcons.iconId(null, desayMid)),
                row(1, 2, DesayLaneIcons.iconId(null, desayRight)),
            )
            "laneinfo_10_icon" -> listOf(
                row(1, 0, DesayLaneIcons.iconId(LEFT90, desayLeft)),
                row(0, 1, DesayLaneIcons.iconId(null, desayMid)),
                row(0, 2, DesayLaneIcons.iconId(null, desayRight)),
            )
            "all_bg" -> listOf(
                row(1, 0, DesayLaneIcons.backgroundIconId(desayLeft)),
                row(1, 1, DesayLaneIcons.backgroundIconId(desayMid)),
                row(1, 2, DesayLaneIcons.backgroundIconId(desayRight)),
            )
            "hardcoded_31_0_3" -> listOf(row(1, 0, 31), row(1, 1, 0), row(1, 2, 3))
            "hardcoded_65_0_1" -> listOf(row(1, 0, 65), row(1, 1, 0), row(1, 2, 1))
            else -> emptyList()
        }
    }
}
