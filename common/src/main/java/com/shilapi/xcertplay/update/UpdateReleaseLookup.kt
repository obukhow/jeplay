package com.shilapi.xcertplay.update

import java.net.URL
import java.net.URLConnection

internal object UpdateReleaseLookup {
    fun latest(
        installedVersion: String,
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? {
        // JAECOO: version from jeplay.ru/version.txt; APK from jeplay.ru/download/.
        val body = UpdateClient.fetchText(
            UpdateClient.VERSION_URL,
            null,
            userAgent,
            openConnection,
        )
        val remoteVersion = UpdateCatalog.parseVersionFile(body) ?: return null
        if (!UpdateVersion.isNewer(remoteVersion, installedVersion)) return null
        return UpdateCatalog.releaseFor(remoteVersion)
    }
}
