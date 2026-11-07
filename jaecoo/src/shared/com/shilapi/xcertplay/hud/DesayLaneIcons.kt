package com.shilapi.xcertplay.hud

/**
 * Apple lane angles → Desay CarLan [VDNaviLaneInfo.LaneInfo.LaneIconId].
 *
 * YaSwitcher builds a key `highlight||dir1|dir2|…` (DriveLabs direction names) and looks it up in
 * the same table Desay ships; 45°/shift variants are collapsed first so they hit that table.
 */
internal object DesayLaneIcons {
    const val STRAIGHT = "STRAIGHT_AHEAD"
    const val LEFT90 = "LEFT90"
    const val RIGHT90 = "RIGHT90"
    const val LEFT180 = "LEFT180"
    const val RIGHT180 = "RIGHT180"
    const val LEFT45 = "LEFT45"
    const val RIGHT45 = "RIGHT45"
    const val LEFT135 = "LEFT135"
    const val RIGHT135 = "RIGHT135"

    /** Pattern → LaneIconId, mirrored from YaSwitcher `a3.y`. */
    private val ICONS = mapOf(
        "||STRAIGHT_AHEAD" to 0,
        "||LEFT90" to 1,
        "||LEFT90|STRAIGHT_AHEAD" to 2,
        "||RIGHT90" to 3,
        "||STRAIGHT_AHEAD|RIGHT90" to 4,
        "||LEFT180" to 5,
        "||LEFT90|RIGHT90" to 6,
        "||LEFT90|STRAIGHT_AHEAD|RIGHT90" to 7,
        "||RIGHT180" to 8,
        "||LEFT180|STRAIGHT_AHEAD" to 9,
        "||STRAIGHT_AHEAD|RIGHT180" to 10,
        "||LEFT180|LEFT90" to 11,
        "||RIGHT90|RIGHT180" to 12,
        "STRAIGHT_AHEAD||LEFT90|STRAIGHT_AHEAD" to 30,
        "LEFT90||LEFT90|STRAIGHT_AHEAD" to 31,
        "STRAIGHT_AHEAD||STRAIGHT_AHEAD|RIGHT90" to 32,
        "RIGHT90||STRAIGHT_AHEAD|RIGHT90" to 33,
        "LEFT90||LEFT90|RIGHT90" to 34,
        "RIGHT90||LEFT90|RIGHT90" to 35,
        "STRAIGHT_AHEAD||LEFT90|STRAIGHT_AHEAD|RIGHT90" to 36,
        "LEFT90||LEFT90|STRAIGHT_AHEAD|RIGHT90" to 37,
        "RIGHT90||LEFT90|STRAIGHT_AHEAD|RIGHT90" to 38,
        "STRAIGHT_AHEAD||LEFT180|STRAIGHT_AHEAD" to 39,
        "LEFT180||LEFT180|STRAIGHT_AHEAD" to 40,
        "STRAIGHT_AHEAD||STRAIGHT_AHEAD|RIGHT180" to 41,
        "RIGHT180||STRAIGHT_AHEAD|RIGHT180" to 42,
        "LEFT90||LEFT180|LEFT90" to 43,
        "LEFT180||LEFT180|LEFT90" to 44,
        "RIGHT90||RIGHT90|RIGHT180" to 45,
        "RIGHT180||RIGHT90|RIGHT180" to 46,
        "||LEFT180|LEFT90|STRAIGHT_AHEAD" to 49,
        "STRAIGHT_AHEAD||STRAIGHT_AHEAD" to 63,
        "LEFT90||LEFT90" to 64,
        "RIGHT90||RIGHT90" to 65,
        "LEFT180||LEFT180" to 66,
        "RIGHT180||RIGHT180" to 67,
        "STRAIGHT_AHEAD||LEFT180|LEFT90|STRAIGHT_AHEAD" to 71,
        "LEFT90||LEFT180|LEFT90|STRAIGHT_AHEAD" to 72,
        "LEFT180||LEFT180|LEFT90|STRAIGHT_AHEAD" to 73,
        "RIGHT90||LEFT180|RIGHT90" to 74,
        "LEFT180||LEFT180|RIGHT90" to 75,
        "STRAIGHT_AHEAD||LEFT180|STRAIGHT_AHEAD|RIGHT90" to 79,
        "RIGHT90||LEFT180|STRAIGHT_AHEAD|RIGHT90" to 80,
        "LEFT180||LEFT180|STRAIGHT_AHEAD|RIGHT90" to 81,
        "LEFT90||LEFT90|RIGHT180" to 82,
        "RIGHT180||LEFT90|RIGHT180" to 83,
    )

    private val ORDER = listOf(
        LEFT180, LEFT135, LEFT90, LEFT45, STRAIGHT, RIGHT45, RIGHT90, RIGHT135, RIGHT180,
    )

    /**
     * Quantize a signed CarPlay lane angle (degrees) to a DriveLabs direction name.
     *
     * Apple: negative = left, positive = right. Desay LaneIconId keys use the same sense
     * (LEFT90 / RIGHT90) — do **not** mirror here. An earlier L↔R negate made Yandex
     * "take left lane" show as a bright right chevron on the Jaecoo HUD.
     */
    fun direction(angleDegrees: Int): String {
        var a = ((angleDegrees % 360) + 360) % 360
        if (a > 180) a -= 360
        return when {
            a in -22..22 -> STRAIGHT
            a in 23..67 -> RIGHT45
            a in 68..112 -> RIGHT90
            a in 113..157 -> RIGHT135
            a >= 158 || a <= -158 -> if (a > 0) RIGHT180 else LEFT180
            a in -67..-23 -> LEFT45
            a in -112..-68 -> LEFT90
            a in -157..-113 -> LEFT135
            else -> STRAIGHT
        }
    }

    /** Same collapses YaSwitcher applies before the icon lookup. */
    fun simplify(direction: String): String = when (direction) {
        LEFT45, "LEFT_SHIFT", "LEFT_FROM_RIGHT" -> STRAIGHT
        RIGHT45, "RIGHT_SHIFT", "RIGHT_FROM_LEFT" -> STRAIGHT
        LEFT135 -> LEFT180
        RIGHT135 -> RIGHT180
        else -> direction
    }

    /**
     * YaSwitcher combined glyph id (highlight||dirs → 0..83).
     * White recommended = highlight prefix present; gray = `||dirs` only (0..12 / fallbacks).
     */
    fun iconId(highlighted: String?, available: Collection<String>): Int {
        val sorted = sortedDirs(highlighted, available)
        val highlight = highlighted?.let(::simplify)?.takeIf { it.isNotEmpty() }
        val key = "${highlight.orEmpty()}||${sorted.joinToString("|")}"
        ICONS[key]?.let { return it }
        if (highlight != null) {
            ICONS["$highlight||$highlight"]?.let { return it }
            ICONS.entries
                .filter { (k, _) -> k.startsWith("$highlight||") }
                .maxByOrNull { (k, _) -> overlap(k.substringAfter("||"), sorted) }
                ?.let { return it.value }
        }
        return backgroundIconId(available)
    }

    /** Gaode/YaSwitcher background type 0..12 (`||dirs` keys) for Jaecoo LaneIconId. */
    fun backgroundIconId(available: Collection<String>): Int {
        val sorted = sortedDirs(null, available)
        val key = "||${sorted.joinToString("|")}"
        ICONS[key]?.let { return it }
        ICONS.entries
            .filter { (k, _) -> k.startsWith("||") }
            .maxByOrNull { (k, _) -> overlap(k.substringAfter("||"), sorted) }
            ?.let { return it.value }
        return 0
    }

    private fun sortedDirs(highlighted: String?, available: Collection<String>): List<String> {
        val dirs = linkedSetOf<String>()
        available.map(::simplify).filter { it.isNotEmpty() }.forEach { dirs.add(it) }
        highlighted?.let(::simplify)?.takeIf { it.isNotEmpty() }?.let { dirs.add(it) }
        if (dirs.isEmpty()) dirs.add(STRAIGHT)
        return dirs.sortedBy { ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }

    private fun overlap(pattern: String, dirs: List<String>): Int {
        // YaSwitcher counts how many of *our* dirs appear in the pattern.
        val parts = pattern.split('|').filter { it.isNotEmpty() }.toSet()
        return dirs.count { it in parts }
    }
}
