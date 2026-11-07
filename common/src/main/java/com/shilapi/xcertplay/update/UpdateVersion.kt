package com.shilapi.xcertplay.update

internal object UpdateVersion {
    // JAECOO: four-part JePlay versions (0.2.19.1) plus classic DiPlay triples.
    // At least major.minor so strings like "psa.2" stay unparseable.
    private val VERSION = Regex("(?<![0-9])v?(\\d+(?:\\.\\d+)+)")

    /** Numeric components left-to-right, or null when none are present. */
    internal fun parse(value: String): List<Int>? =
        VERSION.find(value)?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

    /** Strips a leading `v` and returns the first parseable dotted version, or null. */
    internal fun normalize(value: String): String? =
        parse(value)?.joinToString(".")

    internal fun isNewer(remote: String, installed: String): Boolean {
        val target = parse(remote) ?: return false
        val current = parse(installed) ?: return true
        val length = maxOf(target.size, current.size)
        for (index in 0 until length) {
            val remotePart = target.getOrElse(index) { 0 }
            val installedPart = current.getOrElse(index) { 0 }
            if (remotePart != installedPart) return remotePart > installedPart
        }
        return false
    }
}
