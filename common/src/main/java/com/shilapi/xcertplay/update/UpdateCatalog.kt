package com.shilapi.xcertplay.update

/** The published release that a newer build can be downloaded from. */
internal data class UpdateRelease(
    val tagName: String,
    val apkName: String,
    val apkUrl: String,
    /** Empty when the host does not publish checksums; download then skips verification. */
    val checksumsUrl: String,
)

internal object UpdateCatalog {
    internal const val CHECKSUMS_FILE = "SHA256SUMS.txt"
    // JAECOO
    private const val APK_PREFIX = "JePlay-"

    /**
     * Reads the first non-empty, non-comment line from [version.txt](https://jeplay.ru/version.txt).
     * Accepts `0.2.19.1`, `v0.2.19.1`, or a line that merely contains that version.
     */
    internal fun parseVersionFile(text: String): String? {
        val line = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: return null
        return UpdateVersion.normalize(line)
    }

    /** Builds download URLs under `https://jeplay.ru/download/` for a remote version. */
    internal fun releaseFor(version: String): UpdateRelease {
        val tag = UpdateVersion.normalize(version) ?: version.trim().removePrefix("v")
        val apkName = "$APK_PREFIX$tag.apk"
        return UpdateRelease(
            tagName = tag,
            apkName = apkName,
            apkUrl = UpdateClient.DOWNLOAD_BASE + apkName,
            // Optional companion file; download treats a missing file as “no checksums”.
            checksumsUrl = UpdateClient.DOWNLOAD_BASE + CHECKSUMS_FILE,
        )
    }
}
