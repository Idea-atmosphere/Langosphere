package com.example.logic

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The in-app updater. It talks to the project's GitHub Releases — the same
 * releases the Release workflow publishes on every `v*` tag — and drives the
 * whole "a new version is out → show the changelog → download the APK →
 * hand it to the system installer" flow.
 *
 * The single watched source is the project's public home,
 * Idea-atmosphere/Langosphere — the same repository the About dialog links
 * to. This repository (rrtwy/Langosphere) is only a build/test staging area
 * that must never be referenced by shipped builds: from the next release
 * onwards, versions are published from the public home.
 *
 * Nothing here needs a new dependency: the GitHub REST call uses the OkHttp
 * client already shipped for the AI service, JSON is parsed with the
 * platform's org.json, and the download is delegated to the system's
 * [DownloadManager] (so it survives the app going to the background and gets
 * its own progress notification).
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

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** What the latest GitHub release looks like to the UI. */
    data class UpdateInfo(
        val tagName: String,
        val version: SemanticVersion,
        val releaseName: String,
        /** The release notes ("body"), markdown, rendered as the changelog. */
        val releaseNotes: String,
        val htmlUrl: String,
        val apkUrl: String,
        val apkFileName: String,
        val apkSizeBytes: Long
    )

    /** A download handed to the system [DownloadManager]. */
    data class ApkDownload(
        val downloadId: Long,
        /** The file name inside the app's external Download directory. */
        val fileName: String
    )

    /** Live status of a [DownloadManager] download, read by polling. */
    sealed class DownloadStatus {
        /** Queued or connecting; no bytes are known yet. */
        data object Pending : DownloadStatus()

        data class Running(val bytesSoFar: Long, val totalBytes: Long) : DownloadStatus()

        data class Successful(val localUri: Uri?) : DownloadStatus()

        data class Failed(val reason: Int) : DownloadStatus()
    }

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

    // ── Downloading ────────────────────────────────────────────────────────

    /**
     * Hands the release's APK to the system [DownloadManager].
     *
     * The destination is the app's own external Download directory
     * (`Android/data/<package>/files/Download/`): DownloadManager can write
     * there with no storage permission, the app can read it back with no
     * permission, and FileProvider can grant exactly that one file to the
     * package installer (see res/xml/file_paths.xml). Any APK left over from
     * an earlier version is deleted first — only the newest one is ever kept.
     */
    fun startApkDownload(context: Context, info: UpdateInfo): ApkDownload? {
        val fileName = sanitizeFileName(info.apkFileName)
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: run {
            Log.e(TAG, "External files directory is unavailable; cannot download the APK")
            return null
        }

        // Older APKs are dead weight once a newer one is requested.
        targetDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) && it.name != fileName }
            ?.forEach { it.delete() }
        // A half-written file from a cancelled attempt would make the
        // installer refuse the package, so always start from scratch.
        File(targetDir, fileName).takeIf { it.exists() }?.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle(info.apkFileName)
            .setDescription("Langosphere ${info.tagName}")
            .setMimeType(APK_MIME_TYPE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: run {
                Log.e(TAG, "DownloadManager is unavailable on this device")
                return null
            }
        return try {
            ApkDownload(downloadManager.enqueue(request), fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue the APK download", e)
            null
        }
    }

    /** Reads the current state of a download from the [DownloadManager]. */
    fun queryDownload(context: Context, downloadId: Long): DownloadStatus {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return DownloadStatus.Failed(0)
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                // The download is gone from DownloadManager (removed, or the
                // whole history was cleared by the user).
                return DownloadStatus.Failed(0)
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val localUri = cursor.getString(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                    )
                    DownloadStatus.Successful(localUri?.let { runCatching { Uri.parse(it) }.getOrNull() })
                }
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_PENDING -> {
                    val bytesSoFar = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    if (status == DownloadManager.STATUS_RUNNING && totalBytes > 0) {
                        DownloadStatus.Running(bytesSoFar, totalBytes)
                    } else {
                        DownloadStatus.Pending
                    }
                }
                else -> DownloadStatus.Failed(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                )
            }
        }
    }

    /** Cancels an in-flight download and removes its partial file. */
    fun cancelDownload(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        runCatching { downloadManager.remove(downloadId) }
            .onFailure { Log.w(TAG, "Could not cancel download $downloadId", it) }
    }

    /**
     * The downloaded APK as a content URI shareable with the system package
     * installer. Only FileProvider-granted URIs let another process (the
     * installer) read from the app's private storage.
     */
    fun downloadedApkContentUri(context: Context, fileName: String): Uri? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(dir, fileName)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "The downloaded APK is outside the FileProvider's declared paths", e)
            null
        }
    }

    // ── Installing ─────────────────────────────────────────────────────────

    /**
     * Whether this app is allowed to ask the system to install an APK.
     * Before Android 8.0 the answer is always yes; from 8.0 on, the user
     * must have granted "Install unknown apps" for this app.
     */
    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /**
     * Opens the Android 8.0+ system screen where the user can grant
     * "Install unknown apps" to this app. Returns false when the screen
     * could not be opened (vendor-specific launchers swallowing the intent).
     */
    fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the install-permission settings", e)
            false
        }
    }

    /**
     * Hands the downloaded APK to the system package installer with
     * ACTION_VIEW + `application/vnd.android.package-archive`, granting read
     * access to exactly this file via FLAG_GRANT_READ_URI_PERMISSION.
     * FLAG_ACTIVITY_NEW_TASK is required because the call comes from outside
     * an Activity context (the dialog's context is fine, but this also
     * works from the app's process at large).
     */
    fun installApk(context: Context, apkUri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch the package installer", e)
            false
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Keeps DownloadManager/FileProvider safely inside one file name. */
    internal fun sanitizeFileName(name: String): String =
        name.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "langosphere-update.apk" }

    /** "12.3 MB" / "456 KB", fixed to Locale.US so the digits never flip. */
    internal fun formatByteSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024L * 1024L -> String.format(Locale.US, "%d KB", bytes / 1024L)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
