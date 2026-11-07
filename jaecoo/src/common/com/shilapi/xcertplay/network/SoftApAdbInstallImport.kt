package com.shilapi.xcertplay.network

import android.content.Context
import android.util.Base64
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File

/**
 * SoftAP credentials the WebUSB installer writes after shell `apconfig-read`, so the first
 * JePlay launch can seed Built-in Jaecoo without typing SSID/password by hand.
 *
 * File: [/data/local/tmp/ru.jeplay.app-softap-import] — world-readable; deleted after apply.
 *
 * ```
 * DIPLAY_SOFTAP_IMPORT_V1
 * ssid_b64=…
 * pass_b64=…
 * security=1
 * ```
 */
object SoftApAdbInstallImport {
    const val PATH = "/data/local/tmp/ru.jeplay.app-softap-import"
    const val HEADER = "DIPLAY_SOFTAP_IMPORT_V1"

    fun applyIfPresent(context: Context, log: (String) -> Unit = {}): Boolean {
        val file = File(PATH)
        if (!file.isFile) return false
        return try {
            val lines = file.readLines().map(String::trim).filter { it.isNotEmpty() }
            if (lines.firstOrNull() != HEADER) {
                log("softap import: unexpected header")
                return false
            }
            val map = lines.drop(1).mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }.toMap()
            val ssid = decodeB64(map["ssid_b64"]).orEmpty()
            if (ssid.isBlank()) {
                log("softap import: empty ssid")
                return false
            }
            val passphrase = decodeB64(map["pass_b64"]).orEmpty()
            val security = when (map["security"]?.toIntOrNull()) {
                0 -> ManualHotspotSecurity.OPEN
                2 -> ManualHotspotSecurity.WPA3_TRANSITION
                3 -> ManualHotspotSecurity.WPA3
                else -> ManualHotspotSecurity.WPA2
            }
            AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.JAECOO_BUILTIN)
            AirPlayPersistence.saveManualHotspotSsid(context, ssid)
            AirPlayPersistence.saveManualHotspotPassphrase(context, passphrase)
            AirPlayPersistence.saveManualHotspotSecurity(context, security)
            AirPlayPersistence.saveManualHotspotInterface(context, JaecooBuiltinHotspot.INTERFACE)
            AirPlayPersistence.saveManualHotspotBand(context, JaecooBuiltinHotspot.BAND)
            AirPlayPersistence.saveManualHotspotChannel(context, JaecooBuiltinHotspot.CHANNEL)
            log("softap import: saved $ssid (${security.name}, pass=${passphrase.isNotEmpty()})")
            true
        } catch (error: Exception) {
            log("softap import: ${error.message}")
            false
        } finally {
            runCatching { file.delete() }
        }
    }

    private fun decodeB64(field: String?): String? {
        if (field.isNullOrEmpty()) return ""
        return runCatching {
            val decoded = Base64.decode(field, Base64.NO_WRAP)
            String(decoded).takeIf { Base64.encodeToString(decoded, Base64.NO_WRAP) == field }
        }.getOrNull()
    }
}
