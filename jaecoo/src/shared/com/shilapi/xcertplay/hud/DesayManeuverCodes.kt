package com.shilapi.xcertplay.hud

/**
 * Apple iAP2 RouteGuidanceManeuverType → Desay / AutoNavi ICON integers used by Jaecoo
 * CarLan VDB ([VDNaviRoadInfo.RoadIcon]) and `AUTONAVI_STANDARD_BROADCAST_SEND`.
 *
 * These are **not** BYD Gaode codes ([BydManeuverCodes]). YaSwitcher maps Yandex manoeuvre
 * names to this vocabulary (`left`→2, `right`→3, `straight`→9, `finish`→15).
 */
internal object DesayManeuverCodes {
    const val NONE = 0
    const val LEFT = 2
    const val RIGHT = 3
    const val SLIGHT_LEFT = 4
    const val SLIGHT_RIGHT = 5
    const val SHARP_LEFT = 6
    const val SHARP_RIGHT = 7
    const val U_TURN_LEFT = 8
    const val STRAIGHT = 9
    const val ROUNDABOUT_ENTER = 11
    const val ROUNDABOUT_EXIT = 12
    const val DESTINATION = 15
    const val U_TURN_RIGHT = 19

    /** [drivingSide] 1 is Apple's left-hand driving. */
    fun icon(appleType: Int, drivingSide: Int): Int {
        val leftHand = drivingSide == 1
        if (appleType in 28..46) {
            // YaSwitcher has no numbered exits — any leave_roundabout uses 12.
            return ROUNDABOUT_EXIT
        }
        return when (appleType) {
            1, 20 -> LEFT
            2, 21 -> RIGHT
            47 -> SHARP_LEFT
            48 -> SHARP_RIGHT
            13, 22, 49, 52 -> SLIGHT_LEFT
            14, 23, 50, 53 -> SLIGHT_RIGHT
            4, 18, 19, 26 -> if (leftHand) U_TURN_RIGHT else U_TURN_LEFT
            6 -> ROUNDABOUT_ENTER
            7 -> ROUNDABOUT_EXIT
            10, 12, 24, 25, 27 -> DESTINATION
            3, 5, 8, 9, 11, 15, 16, 17, 51 -> STRAIGHT
            else -> NONE
        }
    }
}
