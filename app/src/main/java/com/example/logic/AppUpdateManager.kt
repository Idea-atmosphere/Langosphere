package com.example.logic

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The in-app update check. It talks to the project's GitHub Releases — the
 * same releases the Release workflow publishes — and drives the "a new
 * version is out → show the changelog → open the APK's download link in the
 * browser" flow.
 *
 * The app deliberately does NOT download or install the APK itself any more:
 * the download link is handed to the user's browser, the browser downloads
 * the file, and the system installer takes it from there exactly as with any
 * other APK the user downloads. That keeps the app free of the
 * REQUEST_INSTALL_PACKAGES permission, the DownloadManager plumbing and the
 * "Install unknown apps" grant for Langosphere.
 *
 * The single watched source is the project's public home,
 * Idea-atmosphere/Langosphere — the same repository the About dialog links
 * to. This repository (rrtwy/Langosphere) is only a build/test staging area
 * that must never be referenced by shipped builds: from the next release
 * onwards, versions are published from the public home.
 *
 * Nothing here needs a new dependency: the GitHub REST call uses the OkHttp
 * client already shipped for the AI service and JSON is parsed with the
 * platform's org.json.
 *
 * Network failures are never thrown at callers that pass through [Result]:
 * a user who is offline simply sees no update dialog.
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    /**
     * The only repository whose releases the updater watches, as
     * `owner/repo`. Deliberately a single, public source of truth: the
     * project publishes its APKs on this repository's Releases and nowhere
     * else, and nothing from the build/test staging repository may appear
     * inside a shipped build (guarded by a unit test).
     */
    const val RELEASE_SOURCE = "Idea-atmosphere/Langosphere"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/$RELEASE_SOURCE/releases/latest"

    /** GitHub's stable REST contract header; also skips deprecated warnings. */
    private const val GITHUB_API_ACCEPT = "application/vnd.github+json"

    /**
     * The tag the user dismissed with "Later" is stored under this key in the
     * app's shared preferences, so the automatic launch check stops nagging
     * until a NEWER release appears (or the user checks manually).
     */
    const val PREFS_SKIPPED_TAG_KEY = "update_skipped_tag"

    /** What the latest GitHub release looks like to the UI. */
    data class UpdateInfo(
        val tagName: String,
        val version: SemanticVersion,
        val releaseName: String,
        /** The release notes ("body"), markdown, rendered as the changelog. */
        val releaseNotes: String,
        /** The release page on GitHub. */
        val htmlUrl: String,
        /** The APK asset's `browser_download_url`; this is what the browser opens. */
        val apkUrl: String,
        val apkFileName: String,
        val apkSizeBytes: Long
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Checking ───────────────────────────────────────────────────────────

    /**
     * Fetches `/repos/Idea-atmosphere/Langosphere/releases/latest` (the
     * newest non-prerelease, non-draft release of the public home) and
     * compares it with [currentVersionName] using [SemanticVersion].
     *
     * @return `Result.success(null)` when the app is already up to date (or
     *   the release carries no APK / an unparsable tag), a non-null
     *   [UpdateInfo] when an update is available, and `Result.failure` when
     *   the API call itself failed (offline, rate limit, …).
     */
    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", GITHUB_API_ACCEPT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub API returned HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: throw IllegalStateException("GitHub API returned an empty body")
                val release = parseReleaseJson(JSONObject(body))
                val current = SemanticVersion.parse(currentVersionName)
                // An unparsable current version (or release) means "cannot
                // compare": no update is offered rather than a wrong one.
                if (release == null || current == null || release.version <= current) {
                    null
                } else {
                    release
                }
            }
        }
    }

    /**
     * Maps a GitHub "get release" payload onto [UpdateInfo]. Returns null when
     * the tag is not MAJOR.MINOR.PATCH-like or the release has no APK asset —
     * a release without an installable file is not an in-app update.
     *
     * Internal (not private) so the unit tests can feed it real payloads.
     */
    internal fun parseReleaseJson(json: JSONObject): UpdateInfo? {
        val tagName = json.optString("tag_name")
        val version = SemanticVersion.parse(tagName) ?: return null

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = -1L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                apkUrl = url
                apkName = name
                apkSize = asset.optLong("size", -1L)
                break
            }
        }
        val url = apkUrl ?: return null

        return UpdateInfo(
            tagName = tagName,
            version = version,
            releaseName = json.optString("name").ifBlank { tagName },
            releaseNotes = json.optString("body"),
            htmlUrl = json.optString("html_url"),
            apkUrl = url,
            apkFileName = apkName ?: "Langosphere-$tagName.apk",
            apkSizeBytes = apkSize
        )
    }

    // ── Handing the download to the browser ────────────────────────────────

    /**
     * Opens [url] with whatever the user has for https links — normally the
     * browser, which downloads the APK into its Downloads and offers to open
     * it with the system installer when done. Returns false when no app on
     * the device could take the link, so the UI can fall back to showing it.
     */
    fun openInBrowser(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            // The call may come from a dialog rather than an Activity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No app can open $url", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to open $url", e)
            false
        }
    }

    /** The APK download link, straight into the browser. */
    fun openApkDownload(context: Context, info: UpdateInfo): Boolean =
        openInBrowser(context, info.apkUrl)

    /** The release page on GitHub (notes, all assets, checksums). */
    fun openReleasePage(context: Context, info: UpdateInfo): Boolean =
        openInBrowser(context, info.htmlUrl.ifBlank { "https://github.com/$RELEASE_SOURCE/releases/tag/${info.tagName}" })

    // ── Helpers ────────────────────────────────────────────────────────────

    /** "12.3 MB" / "456 KB", fixed to Locale.US so the digits never flip. */
    internal fun formatByteSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024L * 1024L -> String.format(Locale.US, "%d KB", bytes / 1024L)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
