package com.shilapi.xcertplay.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCatalogTest {
    @Test
    fun parseVersionFileTakesFirstNonCommentLine() {
        assertEquals(
            "0.2.19.1",
            UpdateCatalog.parseVersionFile("# published JePlay build\n0.2.19.1\n"),
        )
        assertEquals("0.2.19.1", UpdateCatalog.parseVersionFile("v0.2.19.1\n"))
    }

    @Test
    fun parseVersionFileRejectsEmptyOrUnparseable() {
        assertNull(UpdateCatalog.parseVersionFile(""))
        assertNull(UpdateCatalog.parseVersionFile("# only a comment\n"))
        assertNull(UpdateCatalog.parseVersionFile("public-preview\n"))
    }

    @Test
    fun releaseForBuildsJePlayDownloadUrls() {
        val release = UpdateCatalog.releaseFor("v0.2.19.1")
        assertEquals("0.2.19.1", release.tagName)
        assertEquals("JePlay-0.2.19.1.apk", release.apkName)
        assertEquals("https://jeplay.ru/download/JePlay-0.2.19.1.apk", release.apkUrl)
        assertEquals("https://jeplay.ru/download/SHA256SUMS.txt", release.checksumsUrl)
    }
}
