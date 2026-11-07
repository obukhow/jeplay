package com.shilapi.xcertplay.network

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.SystemClock

/**
 * Starts the Jaecoo/Desay SoftAP the way DriveLabs wifihelper does: a SystemUI broadcast plus
 * reflective [WifiManager.setWifiApEnabled], without network ADB or system UID.
 *
 * Does not stop SoftAP on session end (same as MANUAL car-hotspot policy).
 */
object JaecooSoftApStarter {
    const val ACTION_SET_AP_STATUS = "com.desay.systemui.set_ap_status"
    const val EXTRA_AP_STATUS = "ap_status"

    /** WifiManager AP state: enabling / enabled. */
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val WIFI_AP_STATE_ENABLED = 13

    private const val POLL_MS = 250L
    private const val POLL_ATTEMPTS = 20

    enum class Result(val diagnostic: String) {
        READY("Jaecoo SoftAP is on"),
        ALREADY_ON("Jaecoo SoftAP was already on"),
        FAILED("Could not start the Jaecoo SoftAP"),
        CANCELLED("Jaecoo SoftAP startup was cancelled"),
        UNSUPPORTED("WifiManager is unavailable"),
    }

    fun enable(
        context: Context,
        isCancelled: () -> Boolean = { false },
        log: (String) -> Unit = {},
    ): Result {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return Result.UNSUPPORTED.also { log("jaecoo softap: WifiManager missing") }

        if (isCancelled()) return Result.CANCELLED
        if (isApEnabled(wifi) == true) {
            log("jaecoo softap: already enabled")
            return Result.ALREADY_ON
        }

        val steps = mutableListOf<String>()
        sendApStatus(context, enabled = true)
        steps += "Desay hotspot ON broadcast sent"

        val config = readApConfiguration(wifi)
        if (config != null) {
            steps += "getWifiApConfiguration ssid=${config.SSID ?: "<null>"}"
        } else {
            steps += "getWifiApConfiguration unavailable"
        }

        val setResult = setWifiApEnabled(wifi, config, enabled = true)
        steps += "setWifiApEnabled(true)=$setResult"
        log("jaecoo softap: ${steps.joinToString("; ")}")

        if (isCancelled()) return Result.CANCELLED
        if (waitUntil(enabled = true, wifi = wifi, isCancelled = isCancelled)) {
            log("jaecoo softap: AP state enabled")
            return Result.READY
        }
        if (isCancelled()) return Result.CANCELLED
        // Broadcast alone may have worked even when reflection failed / state is unreadable.
        when (isApEnabled(wifi)) {
            true -> {
                log("jaecoo softap: AP reported enabled after wait")
                return Result.READY
            }
            null -> {
                log("jaecoo softap: AP state unreadable after start; continuing")
                return Result.READY
            }
            false -> {
                log("jaecoo softap: still off after ${steps.joinToString("; ")}")
                return Result.FAILED
            }
        }
    }

    internal fun enableIntent(enabled: Boolean): Intent =
        Intent(ACTION_SET_AP_STATUS).putExtra(EXTRA_AP_STATUS, enabled)

    private fun sendApStatus(context: Context, enabled: Boolean) {
        runCatching { context.applicationContext.sendBroadcast(enableIntent(enabled)) }
    }

    private fun readApConfiguration(wifi: WifiManager): WifiConfiguration? = runCatching {
        wifi.javaClass.getMethod("getWifiApConfiguration").invoke(wifi) as? WifiConfiguration
    }.getOrNull()

    private fun setWifiApEnabled(
        wifi: WifiManager,
        configuration: WifiConfiguration?,
        enabled: Boolean,
    ): String = try {
        val method = wifi.javaClass.getMethod(
            "setWifiApEnabled",
            WifiConfiguration::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.invoke(wifi, configuration, enabled).toString()
    } catch (error: Exception) {
        error.javaClass.simpleName + (error.cause?.let { "/${it.javaClass.simpleName}" }.orEmpty())
    }

    private fun isApEnabled(wifi: WifiManager): Boolean? = runCatching {
        val state = wifi.javaClass.getMethod("getWifiApState").invoke(wifi) as Int
        state == WIFI_AP_STATE_ENABLING || state == WIFI_AP_STATE_ENABLED
    }.getOrNull()

    private fun waitUntil(
        enabled: Boolean,
        wifi: WifiManager,
        isCancelled: () -> Boolean,
    ): Boolean {
        repeat(POLL_ATTEMPTS) {
            if (isCancelled()) return false
            when (isApEnabled(wifi)) {
                enabled -> return true
                null -> {
                    SystemClock.sleep(1000L)
                    return true
                }
                else -> SystemClock.sleep(POLL_MS)
            }
        }
        return isApEnabled(wifi) == enabled
    }
}
