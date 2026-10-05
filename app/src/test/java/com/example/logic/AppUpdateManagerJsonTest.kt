package com.example.logic

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the parsing half of [AppUpdateManager]: how a GitHub
 * "get release" payload (`/repos/{owner}/{repo}/releases/latest`) becomes an
 * [AppUpdateManager.UpdateInfo], and which releases are considered not
 * installable at all. Uses offline fixtures — api.github.com is neither
 * reachable nor reliable from CI machines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateManagerJsonTest {

    private fun apkAsset(
        name: String = "Langosphere-v0.0.73.apk",
        url: String = "https://github.com/Idea-atmosphere/Langosphere/releases/download/v0.0.73/Langosphere-v0.0.73.apk",
        size: Long = 12_345_678L
    ): JSONObject = JSONObject()
        .put("name", name)
        .put("browser_download_url", url)
        .put("size", size)

    private fun release(
        tag: String = "v0.0.73",
        assets: JSONArray = JSONArray().put(apkAsset()),
        body: String = "## What's Changed\n* Something new",
        name: String = "Langosphere v0.0.73"
    ): JSONObject = JSONObject()
        .put("tag_name", tag)
        .put("name", name)
        .put("body", body)
        .put("html_url", "https://github.com/Idea-atmosphere/Langosphere/releases/tag/$tag")
        .put("draft", false)
        .put("prerelease", false)
        .put("assets", assets)

    @Test
    fun `parses a full release payload`() {
        val info = AppUpdateManager.parseReleaseJson(release())

        assertEquals("v0.0.73", info?.tagName)
        assertEquals(SemanticVersion(0, 0, 73), info?.version)
        assertEquals("Langosphere v0.0.73", info?.releaseName)
        assertEquals("## What's Changed\n* Something new", info?.releaseNotes)
        assertEquals("https://github.com/Idea-atmosphere/Langosphere/releases/tag/v0.0.73", info?.htmlUrl)
        assertEquals(
            "https://github.com/Idea-atmosphere/Langosphere/releases/download/v0.0.73/Langosphere-v0.0.73.apk",
            info?.apkUrl
        )
        assertEquals("Langosphere-v0.0.73.apk", info?.apkFileName)
        assertEquals(12_345_678L, info?.apkSizeBytes)
    }

    @Test
    fun `picks the apk asset and skips everything else`() {
        val assets = JSONArray()
            .put(JSONObject().put("name", "checksums.txt").put("browser_download_url", "https://x/checksums.txt"))
            .put(apkAsset(name = "app.apk", url = "https://x/app.apk", size = 42L))
        val info = AppUpdateManager.parseReleaseJson(release(assets = assets))

        assertEquals("app.apk", info?.apkFileName)
        assertEquals("https://x/app.apk", info?.apkUrl)
        assertEquals(42L, info?.apkSizeBytes)
    }

    @Test
    fun `uppercase APK extension counts`() {
        val info = AppUpdateManager.parseReleaseJson(
            release(assets = JSONArray().put(apkAsset(name = "APP.APK", url = "https://x/APP.APK")))
        )
        assertEquals("APP.APK", info?.apkFileName)
    }

    @Test
    fun `a release without an apk asset is not an update`() {
        val assets = JSONArray()
            .put(JSONObject().put("name", "notes.txt").put("browser_download_url", "https://x/notes.txt"))
        assertNull(AppUpdateManager.parseReleaseJson(release(assets = assets)))
    }

    @Test
    fun `missing or empty assets are tolerated`() {
        assertNull(AppUpdateManager.parseReleaseJson(release(assets = JSONArray())))
        assertNull(
            AppUpdateManager.parseReleaseJson(
                JSONObject().put("tag_name", "v0.0.73")
            )
        )
    }

    @Test
    fun `an apk asset without a download url is skipped`() {
        val assets = JSONArray().put(JSONObject().put("name", "app.apk").put("size", 1L))
        assertNull(AppUpdateManager.parseReleaseJson(release(assets = assets)))
    }

    @Test
    fun `a tag that is not major minor patch is not an update`() {
        assertNull(AppUpdateManager.parseReleaseJson(release(tag = "weekly")))
        assertNull(AppUpdateManager.parseReleaseJson(release(tag = "")))
    }

    @Test
    fun `a blank release name falls back to the tag`() {
        val info = AppUpdateManager.parseReleaseJson(release(name = ""))
        assertEquals("v0.0.73", info?.releaseName)
    }

    // ── file name / size helpers ──

    @Test
    fun `file names are sanitized for the download directory`() {
        assertEquals("Langosphere-v0.0.73.apk", AppUpdateManager.sanitizeFileName("Langosphere-v0.0.73.apk"))
        assertEquals("my_app__1_.apk", AppUpdateManager.sanitizeFileName("my app (1).apk"))
        assertEquals("langosphere-update.apk", AppUpdateManager.sanitizeFileName("   "))
    }

    @Test
    fun `byte sizes render as KB and MB`() {
        assertEquals("", AppUpdateManager.formatByteSize(0L))
        assertEquals("", AppUpdateManager.formatByteSize(-5L))
        assertEquals("488 KB", AppUpdateManager.formatByteSize(500_000L))
        assertEquals("11.8 MB", AppUpdateManager.formatByteSize(12_345_678L))
        assertEquals("1.0 MB", AppUpdateManager.formatByteSize(1024L * 1024L))
    }

    // ── the watched release source ──

    @Test
    fun `the updater watches only the public home`() {
        // A shipped build must never look for updates on the build/test
        // staging repository: the public home is the single source of truth.
        assertEquals("Idea-atmosphere/Langosphere", AppUpdateManager.RELEASE_SOURCE)
    }

    @Test
    fun `the release notes come from the release body`() {
        // The changelog shown in the dialog is exactly the release's "body"
        // field — the markdown GitHub generates (or the maintainer edits).
        val info = AppUpdateManager.parseReleaseJson(
            release(body = "## What's Changed\n* Something new")
        )
        assertEquals("## What's Changed\n* Something new", info?.releaseNotes)
    }
}
