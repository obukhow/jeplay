package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import java.net.NetworkInterface

/**
 * Reads the live Jaecoo/Desay SoftAP identity the head unit actually broadcasts
 * (factory names like `HS0099374`), so JePlay can hand the same credentials to the
 * iPhone over iAP2 `0x5703` instead of unrelated values typed in settings.
 *
 * SoftAP start via [JaecooSoftApStarter] does not change SSID/password — SystemUI
 * raises the saved SoftApConfiguration. Desay OEM CarPlay reads that same config
 * with system UID; this probe tries the public/hidden app-reachable paths.
 */
object JaecooSoftApCredentials {
    data class Snapshot(
        val ssid: String,
        val passphrase: String?,
        val security: ManualHotspotSecurity?,
        val channel: Int?,
        val source: String,
    ) {
        val hasPassphrase: Boolean get() = !passphrase.isNullOrEmpty()
    }

    fun read(context: Context, log: (String) -> Unit = {}): Snapshot? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        softAp(wifi, log)?.let { return it }
        legacyAp(wifi, log)?.let { return it }
        settings(context, log)?.let { return it }
        properties(log)?.let { return it }
        scanSsid(wifi, log)?.let { return it }
        log("jaecoo softap credentials: unavailable from SoftAp/WifiAp/Settings/props/scan")
        return null
    }

    /**
     * Prefer live SoftAP identity; fill a missing passphrase from [savedPassphrase].
     * Returns null only when neither live nor saved SSID is usable.
     */
    fun resolve(
        context: Context,
        savedSsid: String?,
        savedPassphrase: String?,
        savedSecurity: ManualHotspotSecurity,
        log: (String) -> Unit = {},
    ): Snapshot? {
        val live = read(context, log)
        val ssid = live?.ssid?.takeIf { it.isNotBlank() } ?: savedSsid?.trim()?.takeIf { it.isNotBlank() }
        if (ssid.isNullOrBlank()) {
            log("jaecoo softap credentials: no SSID from system or settings")
            return null
        }
        val liveMatchesSaved = live == null ||
            savedSsid.isNullOrBlank() ||
            live.ssid.equals(savedSsid.trim(), ignoreCase = true)
        // Never reuse an invented passphrase for a different SoftAP SSID — that overwrites the
        // iPhone's saved keys for HS00… via iAP2 0x5703.
        val passphrase = when {
            live?.hasPassphrase == true -> live.passphrase
            liveMatchesSaved && !savedPassphrase.isNullOrEmpty() -> savedPassphrase
            else -> null
        }
        val security = live?.security ?: savedSecurity
        val source = when {
            live == null -> "settings"
            live.hasPassphrase -> live.source
            liveMatchesSaved && !savedPassphrase.isNullOrEmpty() -> "${live.source}+settings-passphrase"
            live != null && !liveMatchesSaved -> "${live.source}+ssid-mismatch-passphrase-dropped"
            else -> "${live.source}+no-passphrase"
        }
        if (live != null && !liveMatchesSaved) {
            log(
                "jaecoo softap credentials: live ssid=${live.ssid} differs from saved " +
                    "ssid=${savedSsid ?: "<blank>"}; dropping saved passphrase to protect iPhone SoftAP keys",
            )
        }
        log(
            "jaecoo softap credentials: source=$source ssid=$ssid " +
                "passphraseLen=${passphrase?.length ?: 0} security=$security " +
                "channel=${live?.channel ?: 0}",
        )
        return Snapshot(
            ssid = ssid,
            passphrase = passphrase,
            security = security,
            channel = live?.channel,
            source = source,
        )
    }

    @SuppressLint("PrivateApi")
    private fun softAp(wifi: WifiManager?, log: (String) -> Unit): Snapshot? {
        if (wifi == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val configuration = WifiManager::class.java
                .getMethod("getSoftApConfiguration")
                .invoke(wifi) as? SoftApConfiguration
            if (configuration == null) {
                log("jaecoo softap credentials: getSoftApConfiguration returned null")
                return null
            }
            val ssid = softApSsid(configuration)
            if (ssid.isNullOrBlank()) {
                log("jaecoo softap credentials: SoftApConfiguration has no SSID")
                return null
            }
            val passphrase = runCatching {
                SoftApConfiguration::class.java.getMethod("getPassphrase").invoke(configuration) as? String
            }.getOrNull()?.takeIf { it.isNotEmpty() }
            val securityType = runCatching {
                SoftApConfiguration::class.java.getMethod("getSecurityType").invoke(configuration) as? Int
            }.getOrNull()
            val channel = softApChannel(configuration)
            Snapshot(
                ssid = ssid,
                passphrase = passphrase,
                security = securityOfSoftAp(securityType),
                channel = channel,
                source = "SoftApConfiguration",
            )
        } catch (error: Throwable) {
            log(
                "jaecoo softap credentials: getSoftApConfiguration failed " +
                    "${error.javaClass.simpleName}:${error.message ?: error.cause?.javaClass?.simpleName ?: ""}",
            )
            null
        }
    }

    private fun softApSsid(configuration: SoftApConfiguration): String? {
        configuration.ssid?.takeIf { it.isNotBlank() }?.let { return it }
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val wifiSsid = SoftApConfiguration::class.java.getMethod("getWifiSsid").invoke(configuration)
                    ?: return@runCatching null
                val bytes = wifiSsid.javaClass.methods
                    .firstOrNull { it.name == "getBytes" && it.parameterTypes.isEmpty() }
                    ?.invoke(wifiSsid) as? ByteArray
                bytes?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: wifiSsid.toString().trim().trim('"').takeIf { it.isNotEmpty() && it != "null" }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun softApChannel(configuration: SoftApConfiguration): Int? = runCatching {
        if (Build.VERSION.SDK_INT >= 36) {
            val channels = configuration.channels
            if (channels.size() == 0) null else channels.valueAt(0)
        } else {
            (
                SoftApConfiguration::class.java.getMethod("getChannel").invoke(configuration) as? Number
                )?.toInt()?.takeIf { it > 0 }
        }
    }.getOrNull()

    @SuppressLint("PrivateApi")
    private fun legacyAp(wifi: WifiManager?, log: (String) -> Unit): Snapshot? {
        if (wifi == null) return null
        return try {
            val configuration = WifiManager::class.java
                .getMethod("getWifiApConfiguration")
                .invoke(wifi) as? WifiConfiguration
            val ssid = unquote(configuration?.SSID)
            if (ssid.isNullOrBlank()) {
                log("jaecoo softap credentials: getWifiApConfiguration unavailable")
                return null
            }
            val passphrase = unquote(configuration?.preSharedKey)
            val security = when {
                configuration?.allowedKeyManagement?.get(WifiConfiguration.KeyMgmt.WPA2_PSK) == true ->
                    ManualHotspotSecurity.WPA2
                configuration?.allowedKeyManagement?.cardinality() == 0 ||
                    configuration?.allowedKeyManagement?.get(WifiConfiguration.KeyMgmt.NONE) == true ->
                    ManualHotspotSecurity.OPEN
                else -> ManualHotspotSecurity.WPA2
            }
            val channel = runCatching {
                WifiConfiguration::class.java.getField("apChannel").getInt(configuration)
            }.getOrNull()?.takeIf { it > 0 }
            Snapshot(ssid, passphrase, security, channel, source = "WifiConfiguration")
        } catch (error: Throwable) {
            log(
                "jaecoo softap credentials: getWifiApConfiguration failed " +
                    error.javaClass.simpleName,
            )
            null
        }
    }

    private fun settings(context: Context, log: (String) -> Unit): Snapshot? {
        val resolver = context.applicationContext.contentResolver
        val keys = listOf(
            "wifi_ap_ssid" to "wifi_ap_passwd",
            "wifi_ap_ssid" to "wifi_ap_password",
            "softap_ssid" to "softap_passwd",
            "tether_ssid" to "tether_passwd",
        )
        for ((ssidKey, passKey) in keys) {
            val ssid = settingsString(resolver, ssidKey)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val passphrase = settingsString(resolver, passKey)?.takeIf { it.isNotEmpty() }
            log("jaecoo softap credentials: Settings key=$ssidKey ssid=$ssid passphraseLen=${passphrase?.length ?: 0}")
            return Snapshot(ssid, passphrase, ManualHotspotSecurity.WPA2, channel = null, source = "Settings:$ssidKey")
        }
        return null
    }

    private fun settingsString(resolver: android.content.ContentResolver, key: String): String? {
        fun read(table: (android.content.ContentResolver, String) -> String?) = runCatching {
            table(resolver, key)
        }.getOrNull()
        return read(Settings.Global::getString)
            ?: read(Settings.System::getString)
            ?: read(Settings.Secure::getString)
    }

    @SuppressLint("PrivateApi")
    private fun properties(log: (String) -> Unit): Snapshot? {
        val get = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
        }.getOrNull() ?: return null
        fun prop(key: String): String? =
            (get.invoke(null, key, "") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val pairs = listOf(
            "persist.sys.softap.ssid" to "persist.sys.softap.pwd",
            "persist.vendor.wifi.softap.ssid" to "persist.vendor.wifi.softap.pwd",
            "ro.wifi.ap.ssid" to "ro.wifi.ap.password",
        )
        for ((ssidKey, passKey) in pairs) {
            val ssid = prop(ssidKey) ?: continue
            val passphrase = prop(passKey)
            log("jaecoo softap credentials: property $ssidKey=$ssid passphraseLen=${passphrase?.length ?: 0}")
            return Snapshot(ssid, passphrase, ManualHotspotSecurity.WPA2, null, source = "prop:$ssidKey")
        }
        return null
    }

    /** Best-effort SSID from a scan hit that matches SoftAP iface hardware address. */
    private fun scanSsid(wifi: WifiManager?, log: (String) -> Unit): Snapshot? {
        if (wifi == null) return null
        val mac = runCatching {
            NetworkInterface.getByName(JaecooBuiltinHotspot.INTERFACE)?.hardwareAddress
        }.getOrNull() ?: return null
        val macString = mac.joinToString(":") { "%02x".format(it) }
        val results = try {
            wifi.scanResults
        } catch (_: SecurityException) {
            return null
        } ?: return null
        val hit = results.firstOrNull { result ->
            result.BSSID.equals(macString, ignoreCase = true) && result.SSID.isNotBlank()
        } ?: return null
        log("jaecoo softap credentials: scan matched BSSID=$macString ssid=${hit.SSID}")
        return Snapshot(
            ssid = hit.SSID,
            passphrase = null,
            security = ManualHotspotSecurity.WPA2,
            channel = frequencyToChannel(hit.frequency),
            source = "scan:${JaecooBuiltinHotspot.INTERFACE}",
        )
    }

    private fun securityOfSoftAp(securityType: Int?): ManualHotspotSecurity? = when (securityType) {
        SoftApConfiguration.SECURITY_TYPE_OPEN -> ManualHotspotSecurity.OPEN
        SoftApConfiguration.SECURITY_TYPE_WPA2_PSK -> ManualHotspotSecurity.WPA2
        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION -> ManualHotspotSecurity.WPA3_TRANSITION
        SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> ManualHotspotSecurity.WPA3
        null -> null
        else -> ManualHotspotSecurity.WPA2
    }

    private fun unquote(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    private fun frequencyToChannel(frequencyMhz: Int): Int? = when (frequencyMhz) {
        in 2_400..2_500 -> (frequencyMhz - 2_407) / 5
        in 5_000..5_895 -> (frequencyMhz - 5_000) / 5
        else -> null
    }.takeIf { it != null && it > 0 }
}
