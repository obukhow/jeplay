package com.shilapi.xcertplay.update

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File

/**
 * Durable update metadata: which release is available, and — after an explicit download —
 * where the verified APK still lives so Install survives activity recreation.
 */
internal object UpdateAvailability {
    private const val PREFERENCES = "diplay_update_availability"
    private const val TAG = "tag"
    private const val APK_NAME = "apk_name"
    private const val APK_URL = "apk_url"
    private const val CHECKSUMS_URL = "checksums_url"
    private const val LAST_ATTEMPT_MILLIS = "last_attempt_millis"
    private const val BACKGROUND_CHECKS = "background_checks"
    private const val LAST_RESULT = "last_result"
    private const val LAST_RESULT_MILLIS = "last_result_millis"
    private const val LAST_INSTALL = "last_install"
    private const val LAST_INSTALL_MILLIS = "last_install_millis"
    private const val READY_TAG = "ready_tag"
    private const val READY_APK_PATH = "ready_apk_path"
    private const val READY_SAVED_PATH = "ready_saved_path"
    private const val READY_SAVED_URI = "ready_saved_uri"

    internal const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    /** A verified download that Install can resume after the activity is recreated. */
    data class ReadyUpdate(
        val release: UpdateRelease,
        val apk: File,
        val savedPath: String?,
        val savedUri: Uri?,
    )

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun backgroundChecksEnabled(context: Context): Boolean =
        preferences(context).getBoolean(BACKGROUND_CHECKS, true)

    fun saveBackgroundChecksEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(BACKGROUND_CHECKS, enabled).apply()
    }

    fun available(context: Context, installedVersion: String): UpdateRelease? {
        val values = preferences(context)
        val release = UpdateRelease(
            tagName = values.getString(TAG, null) ?: return null,
            apkName = values.getString(APK_NAME, null) ?: return null,
            apkUrl = values.getString(APK_URL, null) ?: return null,
            checksumsUrl = values.getString(CHECKSUMS_URL, null) ?: return null,
        )
        if (UpdateVersion.isNewer(release.tagName, installedVersion)) return release
        clear(context)
        return null
    }

    fun save(context: Context, release: UpdateRelease) {
        val values = preferences(context)
        val editor = values.edit()
            .putString(TAG, release.tagName)
            .putString(APK_NAME, release.apkName)
            .putString(APK_URL, release.apkUrl)
            .putString(CHECKSUMS_URL, release.checksumsUrl)
        // A different remote release invalidates any previously downloaded APK.
        if (values.getString(READY_TAG, null) != release.tagName) clearReadyFields(editor)
        editor.apply()
    }

    fun clear(context: Context) {
        preferences(context).edit()
            .remove(TAG)
            .remove(APK_NAME)
            .remove(APK_URL)
            .remove(CHECKSUMS_URL)
            .also { clearReadyFields(it) }
            .apply()
    }

    fun saveReady(context: Context, release: UpdateRelease, apk: File, saved: UpdateApkExport.Saved?) {
        preferences(context).edit()
            .putString(READY_TAG, release.tagName)
            .putString(READY_APK_PATH, apk.absolutePath)
            .putString(READY_SAVED_PATH, saved?.path)
            .putString(READY_SAVED_URI, saved?.uri?.toString())
            .apply()
    }

    fun clearReady(context: Context) {
        preferences(context).edit().also { clearReadyFields(it) }.apply()
    }

    /**
     * Returns the downloaded APK when it is still on disk and still newer than the installed build.
     * Stale paths are cleared so Settings does not show a dead Install button.
     */
    fun ready(context: Context, installedVersion: String): ReadyUpdate? {
        val release = available(context, installedVersion) ?: return null
        val values = preferences(context)
        if (values.getString(READY_TAG, null) != release.tagName) {
            clearReady(context)
            return null
        }
        val path = values.getString(READY_APK_PATH, null) ?: return null
        val apk = File(path)
        if (!apk.isFile) {
            clearReady(context)
            return null
        }
        val savedUri = values.getString(READY_SAVED_URI, null)?.let(Uri::parse)
        return ReadyUpdate(
            release = release,
            apk = apk,
            savedPath = values.getString(READY_SAVED_PATH, null),
            savedUri = savedUri,
        )
    }

    fun shouldCheck(context: Context, nowMillis: Long): Boolean {
        val lastAttempt = preferences(context).getLong(LAST_ATTEMPT_MILLIS, 0L)
        return lastAttempt <= 0L || nowMillis < lastAttempt || nowMillis - lastAttempt >= CHECK_INTERVAL_MILLIS
    }

    fun recordAttempt(context: Context, nowMillis: Long) {
        preferences(context).edit().putLong(LAST_ATTEMPT_MILLIS, nowMillis).apply()
    }

    fun recordResult(context: Context, nowMillis: Long, result: String) {
        preferences(context).edit().putString(LAST_RESULT, result).putLong(LAST_RESULT_MILLIS, nowMillis).apply()
    }

    fun recordInstall(context: Context, nowMillis: Long, outcome: String) {
        preferences(context).edit().putString(LAST_INSTALL, outcome).putLong(LAST_INSTALL_MILLIS, nowMillis).apply()
    }

    /** One diagnostic line. Ages are relative so the report needs no clock or locale. */
    fun report(context: Context, nowMillis: Long): String {
        val values = preferences(context)
        fun age(key: String) = values.getLong(key, 0L).takeIf { it > 0L }
            ?.let { "${(nowMillis - it).coerceAtLeast(0L) / 60_000} min ago" } ?: "never"
        val ready = values.getString(READY_TAG, null)?.let { tag ->
            val path = values.getString(READY_SAVED_PATH, null) ?: values.getString(READY_APK_PATH, null) ?: "apk"
            "$tag at $path"
        } ?: "none"
        return "Update check: background=${if (backgroundChecksEnabled(context)) "on" else "off"}; " +
            "lastSuccess=${age(LAST_ATTEMPT_MILLIS)}; cachedRelease=${values.getString(TAG, null) ?: "none"}; " +
            "ready=$ready; " +
            "lastResult=${values.getString(LAST_RESULT, null) ?: "none"} (${age(LAST_RESULT_MILLIS)}); " +
            "lastInstall=${values.getString(LAST_INSTALL, null) ?: "none"} (${age(LAST_INSTALL_MILLIS)})"
    }

    private fun clearReadyFields(editor: SharedPreferences.Editor) {
        editor.remove(READY_TAG).remove(READY_APK_PATH).remove(READY_SAVED_PATH).remove(READY_SAVED_URI)
    }

    internal fun clearAllForTest(context: Context) {
        preferences(context).edit().clear().commit()
    }
}
