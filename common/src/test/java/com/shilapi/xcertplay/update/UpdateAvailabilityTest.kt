package com.shilapi.xcertplay.update

import android.content.Context
import android.net.Uri
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29, 35])
class UpdateAvailabilityTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After fun clear() = UpdateAvailability.clearAllForTest(context)

    @Test fun persistsAReleaseThatIsNewerThanTheInstalledBuild() {
        val release = release("v0.2.16")
        UpdateAvailability.save(context, release)

        assertEquals(release, UpdateAvailability.available(context, "0.2.15"))
    }

    @Test fun removesAReleaseAfterTheAppCatchesUp() {
        UpdateAvailability.save(context, release("v0.2.16"))

        assertNull(UpdateAvailability.available(context, "0.2.16"))
        assertNull(UpdateAvailability.available(context, "0.2.15"))
    }

    @Test fun backgroundChecksDefaultOnAndPersistTheChoice() {
        assertTrue(UpdateAvailability.backgroundChecksEnabled(context))
        UpdateAvailability.saveBackgroundChecksEnabled(context, false)
        assertFalse(UpdateAvailability.backgroundChecksEnabled(context))
    }

    @Test fun reportShowsStateWithRelativeAges() {
        assertEquals(
            "Update check: background=on; lastSuccess=never; cachedRelease=none; ready=none; " +
                "lastResult=none (never); lastInstall=none (never)",
            UpdateAvailability.report(context, 10_000_000L),
        )
        UpdateAvailability.save(context, release("v0.2.16"))
        UpdateAvailability.recordAttempt(context, 10_000_000L - 5 * 60_000)
        UpdateAvailability.recordResult(context, 10_000_000L - 60_000, "no validated network (attempt 2)")
        UpdateAvailability.recordInstall(context, 10_000_000L - 2 * 60_000, "chooser uri=public installers=2")
        UpdateAvailability.saveBackgroundChecksEnabled(context, false)
        assertEquals(
            "Update check: background=off; lastSuccess=5 min ago; cachedRelease=v0.2.16; ready=none; " +
                "lastResult=no validated network (attempt 2) (1 min ago); " +
                "lastInstall=chooser uri=public installers=2 (2 min ago)",
            UpdateAvailability.report(context, 10_000_000L),
        )
    }

    @Test fun checksAtMostOncePerDayButRecoversFromAClockReset() {
        val now = 10L * UpdateAvailability.CHECK_INTERVAL_MILLIS
        assertTrue(UpdateAvailability.shouldCheck(context, now))
        UpdateAvailability.recordAttempt(context, now)

        assertFalse(UpdateAvailability.shouldCheck(context, now + UpdateAvailability.CHECK_INTERVAL_MILLIS - 1))
        assertTrue(UpdateAvailability.shouldCheck(context, now + UpdateAvailability.CHECK_INTERVAL_MILLIS))
        assertTrue(UpdateAvailability.shouldCheck(context, now - 1))
    }

    @Test fun readySurvivesAcrossReadsWhileTheApkFileRemains() {
        val release = release("v0.2.16")
        val apk = File(context.cacheDir, "update/ready/DiPlay.apk").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val saved = UpdateApkExport.Saved("Download/JePlay/DiPlay.apk", Uri.parse("content://media/external/downloads/7"))
        UpdateAvailability.save(context, release)
        UpdateAvailability.saveReady(context, release, apk, saved)

        val ready = UpdateAvailability.ready(context, "0.2.15")!!
        assertEquals(release, ready.release)
        assertEquals(apk.absolutePath, ready.apk.absolutePath)
        assertEquals(saved.path, ready.savedPath)
        assertEquals(saved.uri, ready.savedUri)
        assertEquals(
            "Update check: background=on; lastSuccess=never; cachedRelease=v0.2.16; " +
                "ready=v0.2.16 at Download/JePlay/DiPlay.apk; lastResult=none (never); lastInstall=none (never)",
            UpdateAvailability.report(context, 10_000_000L),
        )
    }

    @Test fun readyClearsWhenTheApkFileDisappears() {
        val release = release("v0.2.16")
        val apk = File(context.cacheDir, "update/missing/DiPlay.apk")
        UpdateAvailability.save(context, release)
        UpdateAvailability.saveReady(context, release, apk, null)

        assertNull(UpdateAvailability.ready(context, "0.2.15"))
        assertEquals(release, UpdateAvailability.available(context, "0.2.15"))
    }

    @Test fun savingANewerReleaseInvalidatesThePreviousDownload() {
        val old = release("v0.2.16")
        val newer = release("v0.2.17")
        val apk = File(context.cacheDir, "update/old/DiPlay.apk").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        UpdateAvailability.save(context, old)
        UpdateAvailability.saveReady(context, old, apk, UpdateApkExport.Saved("Download/JePlay/old.apk", null))
        UpdateAvailability.save(context, newer)

        assertNull(UpdateAvailability.ready(context, "0.2.15"))
        assertEquals(newer, UpdateAvailability.available(context, "0.2.15"))
    }

    private fun release(tag: String) = UpdateRelease(
        tagName = tag,
        apkName = "DiPlay-$tag.apk",
        apkUrl = "https://example.com/DiPlay-$tag.apk",
        checksumsUrl = "https://example.com/SHA256SUMS.txt",
    )
}
