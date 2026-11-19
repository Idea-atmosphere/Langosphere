package com.example.logic

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import com.example.model.OnlineCaptionTrack
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import com.example.model.OnlineStream
import com.example.model.OnlineVideo
import com.example.model.OnlineVideoDetails
import com.example.model.SubtitleEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.TreeSet

/**
 * «ذخیره هنگام پخش» — the Online tab's save-while-playing store.
 *
 * Two halves:
 *
 * 1. VIDEO BYTES: a single shared [SimpleCache]. While the toggle is on,
 *    [SaveWhilePlayingDataSourceFactory] routes the player's http(s) reads
 *    through a [CacheDataSource], so every byte downloaded for viewing is
 *    written to disk as it is watched. Later viewings of the same rendition
 *    are served from those spans — replays and seeks into watched parts no
 *    longer depend on the instance or on the signed URL still being alive.
 *    Keys are stable per video + rendition (see [streamKey]) because the
 *    signed googlevideo URLs change between sessions.
 *
 * 2. SUBTITLES: every caption track the learner actually watches (main or
 *    translation) is persisted as JSON under the video's cache directory.
 *    When a later lookup fails — the instance is gone, rate-limited or
 *    offline — the disk copy is used instead, so the clip keeps its
 *    subtitles «برای استفاده‌های بعدی».
 *
 * WHERE: the app's own folder in Downloads — `Download/Langosphere/online/`
 * (next to `models/` and `exports/`), with a `.nomedia` file so the gallery
 * never lists the chunks. Android 11+ lets the app create and use its own
 * files there through plain file paths without any storage permission;
 * where that is not possible (Android 10 and older, or an unmounted
 * storage) the same layout lives in app-private storage instead, and the
 * manager page says which. Data saved in the private location before this
 * change is moved to the public folder once, the first time it is usable.
 *
 * OFFLINE: everything a saved clip needs to play again without the network
 * is kept — the renditions' identities (for the cache keys), the caption
 * tracks, and the subtitle files — so [offlineCopy] can rebuild a playable
 * [OnlineVideoDetails] for the saved part of the clip (see
 * OnlineVideoViewModel.loadVideo). An adjustable least-recently-used limit
 * keeps the folder bounded (the manager's «storage limit» chips).
 */
object OnlineWatchCache {

    private const val ROOT_DIR = "online_watch_cache"
    private const val VIDEO_DIR = "video"
    private const val CAPTIONS_DIR = "captions"

    /** The public folder, relative to the primary shared storage (for display). */
    const val PUBLIC_RELATIVE_PATH = "Download/Langosphere/online"

    /** Default limit of the saved media in the public folder / in private storage. */
    const val DEFAULT_PUBLIC_LIMIT_BYTES: Long = 4L * 1024 * 1024 * 1024
    const val DEFAULT_PRIVATE_LIMIT_BYTES: Long = 512L * 1024 * 1024

    /** «Unlimited» for [setMaxBytes]: nothing is ever evicted, the learner deletes. */
    const val LIMIT_UNLIMITED: Long = Long.MAX_VALUE

    /** Kept for callers that still read the old constant name. */
    const val MAX_VIDEO_CACHE_BYTES: Long = DEFAULT_PRIVATE_LIMIT_BYTES

    /** Pseudo-instance of a clip played from its saved copy (no network). */
    const val OFFLINE_BASE_URL = "saved://offline"
    val OFFLINE_INSTANCE = OnlineInstance(baseUrl = OFFLINE_BASE_URL, kind = OnlineInstanceKind.INVIDIOUS, enabled = false)

    /**
     * Where the saved videos and subtitles live right now. [foreign] is set
     * when the public folder exists but holds files of an earlier
     * installation: after an uninstall Android hands those files to no one,
     * so the new installation can neither read nor replace them — it falls
     * back to private storage until the learner deletes that folder.
     */
    data class Storage(val root: File, val isPublic: Boolean, val foreign: Boolean = false) {
        /** `Download/Langosphere/online` or the private root's absolute path. */
        val displayPath: String get() = if (isPublic) PUBLIC_RELATIVE_PATH else root.absolutePath
    }

    @Volatile
    private var storage: Storage? = null
    private val storageLock = Any()

    /**
     * Resolves (once per process) the folder the cache uses: the public
     * `Download/Langosphere/online/` when it can be created and written,
     * else the private fallback. Creates `.nomedia` and moves data saved in
     * the private location earlier. Does file IO — call off the main thread.
     */
    fun storage(context: Context): Storage = storage ?: synchronized(storageLock) {
        storage ?: resolveStorage(context.applicationContext).also { storage = it }
    }

    /** Warm-up for the Online tab: resolve the folder (and migrate) before the first clip opens. */
    fun prepare(context: Context) {
        try {
            storage(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun privateRoot(context: Context): File = File(context.filesDir, ROOT_DIR)

    private fun publicRoot(): File? = try {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Langosphere/online")
    } catch (e: Exception) {
        null
    }

    private fun resolveStorage(context: Context): Storage {
        val priv = privateRoot(context)
        val pub = publicRoot()
        if (pub != null && isWritableDir(pub)) {
            if (hasForeignFiles(pub)) {
                priv.mkdirs()
                return Storage(priv, isPublic = false, foreign = true)
            }
            ensureNoMedia(pub)
            if (migrate(priv, pub)) return Storage(pub, isPublic = true)
        }
        priv.mkdirs()
        return Storage(priv, isPublic = false)
    }

    /**
     * True when [root] holds cache files this installation may not touch —
     * the index, the Media3 index or a media chunk that exists but cannot
     * be read and written. Using the folder anyway would make Media3 fail
     * its start-up (it must rewrite that index) and silently stop saving.
     */
    internal fun hasForeignFiles(root: File): Boolean {
        val sentinels = listOf(File(root, INDEX_FILE), File(root, "$VIDEO_DIR/$MEDIA3_INDEX_FILE"))
        if (sentinels.any { it.exists() && !(it.canRead() && it.canWrite()) }) return true
        val chunk = File(root, VIDEO_DIR).listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".exo") } ?: return false
        return !(chunk.canRead() && chunk.canWrite())
    }

    /** Media3's own index file inside the media directory (SimpleCache's legacy/file index). */
    private const val MEDIA3_INDEX_FILE = "cached_content_index.exi"

    /** True when [dir] exists (or could be created) and a small file can be written, read back and deleted in it. */
    internal fun isWritableDir(dir: File): Boolean {
        return try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return false
            val probe = File(dir, ".probe-${System.nanoTime()}")
            probe.writeBytes(byteArrayOf(7))
            val ok = probe.readBytes().contentEquals(byteArrayOf(7))
            probe.delete()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /**
     * `.nomedia` at the folder's root: the gallery and music apps skip the
     * whole subtree. Only at the root — SimpleCache deletes any file it
     * does not recognise inside its own `video/` directory.
     */
    internal fun ensureNoMedia(root: File) {
        try {
            root.mkdirs()
            val marker = File(root, ".nomedia")
            if (!marker.exists()) marker.createNewFile()
        } catch (e: Exception) {
            // Not fatal: without the marker the chunks may show up in a
            // file manager's "downloads" list, nothing else.
        }
    }

    /**
     * Moves the media chunks and the subtitle files saved in the private
     * location to the public folder, file by file (copy, verify length,
     * delete the source). The cache's `.uid` file travels with the chunks,
     * so its index rows in the app database keep matching. Returns true
     * when nothing is left in the private location; a failure (full
     * storage, …) leaves the rest for the next start and keeps this
     * session on the private copy, so no data is ever half-moved away.
     */
    internal fun migrate(priv: File, pub: File): Boolean {
        val oldVideo = File(priv, VIDEO_DIR)
        val oldCaptions = File(priv, CAPTIONS_DIR)
        val oldIndex = File(priv, INDEX_FILE)
        if (!oldVideo.isDirectory && !oldCaptions.isDirectory && !oldIndex.isFile) return true
        if (!moveTree(oldVideo, File(pub, VIDEO_DIR)) || !moveTree(oldCaptions, File(pub, CAPTIONS_DIR))) return false
        if (oldIndex.isFile) {
            val target = File(pub, INDEX_FILE)
            try {
                if (!target.isFile) oldIndex.copyTo(target, overwrite = true)
                oldIndex.delete()
            } catch (e: Exception) {
                return false
            }
        }
        return true
    }

    private fun moveTree(src: File, dst: File): Boolean {
        if (!src.isDirectory) return true
        val files = src.walkTopDown().filter { it.isFile }.toList()
        for (file in files) {
            val rel = file.relativeTo(src).path
            val target = File(dst, rel)
            try {
                target.parentFile?.mkdirs()
                if (!(target.isFile && target.length() == file.length())) {
                    val tmp = File(target.parentFile, target.name + ".part")
                    FileInputStream(file).use { input -> FileOutputStream(tmp).use { out -> input.copyTo(out) } }
                    if (tmp.length() != file.length()) {
                        tmp.delete()
                        return false
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        return false
                    }
                }
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        src.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() }
        return true
    }

    /** Slot name of the main (source-language) caption track. */
    const val SLOT_MAIN = "main"

    /** Slot name of the secondary (target-language) caption track. */
    const val SLOT_TRANSLATION = "translation"

    @Volatile
    private var videoCache: SimpleCache? = null

    // ───────────────────── settings (shared source of truth) ─────────────────────

    private const val PREFS = "online_watch_cache"
    private const val PREF_SAVE_ENABLED = "save_while_playing"
    private const val PREF_DEFAULT_QUALITY = "default_quality_height"
    private const val PREF_MAX_BYTES = "max_bytes"

    /** «ذخیره هنگام پخش» — the global switch every screen reads and writes. */
    fun isSaveEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_SAVE_ENABLED, true)

    fun setSaveEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(PREF_SAVE_ENABLED, enabled).apply()

    /**
     * The default quality new videos are opened (and saved) with. 0 keeps the
     * built-in selector (muxed 720p/360p first); [QUALITY_HIGHEST] always
     * takes the sharpest rendition; anything else is a target height (480,
     * 720, …) and the best rendition at or below it.
     */
    const val QUALITY_AUTO = 0
    const val QUALITY_HIGHEST = Int.MAX_VALUE

    fun defaultQualityHeight(context: Context): Int =
        prefs(context).getInt(PREF_DEFAULT_QUALITY, QUALITY_AUTO)

    fun setDefaultQualityHeight(context: Context, height: Int) =
        prefs(context).edit().putInt(PREF_DEFAULT_QUALITY, height).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The storage limit of the saved media: the oldest-watched chunks go
     * first once it is exceeded ([LIMIT_UNLIMITED] = never). The stored
     * choice, else 4 GB in the public folder / 512 MB in private storage.
     * Reads prefs only — safe on the main thread when [storage] resolved.
     */
    fun maxBytes(context: Context): Long {
        val stored = prefs(context).getLong(PREF_MAX_BYTES, 0L)
        if (stored > 0L) return stored
        val public = storage?.isPublic ?: true
        return if (public) DEFAULT_PUBLIC_LIMIT_BYTES else DEFAULT_PRIVATE_LIMIT_BYTES
    }

    /** Sets the limit, applies it to the live cache at once (trimming if needed). Call on an IO dispatcher. */
    fun setMaxBytes(context: Context, bytes: Long) {
        val value = if (bytes <= 0L) LIMIT_UNLIMITED else bytes
        prefs(context).edit().putLong(PREF_MAX_BYTES, value).apply()
        val cache = videoCache ?: return
        evictor?.let { ev ->
            ev.maxBytes = value
            try {
                synchronized(cache) { ev.trim(cache) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Volatile
    private var evictor: AdjustableLruEvictor? = null

    /**
     * The one [SimpleCache] for the video directory of [storage]. SimpleCache
     * refuses a second instance on the same folder, so this is a
     * double-checked singleton; the first call happens on a player loading
     * thread or an IO dispatcher, never the main thread.
     */
    fun videoCache(context: Context): SimpleCache = videoCache ?: synchronized(this) {
        videoCache ?: run {
            val appContext = context.applicationContext
            val dir = File(storage(appContext).root, VIDEO_DIR)
            val ev = AdjustableLruEvictor(maxBytes(appContext))
            evictor = ev
            // The cache index lives IN the folder (SimpleCache's file index,
            // `video/cached_content_index.exi`) rather than in the app's
            // database: that way the folder is self-contained — a reinstall
            // or a copy to another device keeps every saved clip, whereas
            // chunks whose index is gone would be swept away at start-up.
            // The database provider is still passed so an index written by
            // earlier builds is carried over into the file once.
            SimpleCache(
                dir,
                ev,
                StandaloneDatabaseProvider(appContext),
                /* legacyIndexSecretKey = */ null,
                /* legacyIndexEncrypt = */ false,
                /* preferLegacyIndex = */ true
            ).also { videoCache = it }
        }
    }

    /**
     * A stable cross-session cache key for one rendition: the signed URLs
     * rotate, but the video id, container, muxed/adaptive kind and quality
     * label identify the same media file every time. Two renditions that
     * share a label (mp4 + webm 720p) stay apart via the container.
     */
    fun streamKey(videoId: String, stream: OnlineStream): String {
        val container = stream.mimeType.substringAfter('/').substringBefore(';')
            .replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "bin" }
        val kind = if (stream.isProgressive) "muxed" else "dash"
        val label = stream.qualityLabel.replace(Regex("[^A-Za-z0-9]"), "")
            .ifBlank { if (stream.height > 0) "${stream.height}p" else "?" }
        return "v-${sanitize(videoId)}-$container-$kind-$label"
    }

    /** The adaptive pair's audio track is one file shared by every rendition. */
    fun audioKey(videoId: String): String = "v-${sanitize(videoId)}-audio"

    /**
     * The raw media addresses inside one of the locally built DASH manifests
     * (a `data:…;base64,…` URI): its <BaseURL> entries, XML-unescaped, video
     * track first, audio second. The adaptive player opens THESE http URLs,
     * not the manifest URI itself — so the cache keys must be resolved from
     * them (see OnlineVideoScreen's key map). Empty for plain URLs, foreign
     * data URIs and decode failures.
     */
    fun dashManifestUrls(manifestUri: String): List<String> {
        return try {
            if (!manifestUri.startsWith("data:") || !manifestUri.contains("base64,")) return emptyList()
            val payload = manifestUri.substringAfter("base64,", "")
            if (payload.isBlank()) return emptyList()
            val xml = String(android.util.Base64.decode(payload, android.util.Base64.DEFAULT), Charsets.UTF_8)
            Regex("<BaseURL>(.*?)</BaseURL>").findAll(xml)
                .map { m ->
                    m.groupValues[1]
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─────────────────────── storage manager (index) ───────────────────────

    /** One cached video as the manager page shows it. */
    data class CachedVideo(
        val videoId: String,
        val title: String,
        /** Watched media bytes held by the cache for this clip. */
        val videoBytes: Long,
        /** Persisted subtitle bytes for this clip. */
        val captionBytes: Long,
        /**
         * Share of the best-saved rendition that is on disk (0–1), or null
         * when its length is still unknown (nothing of it watched yet).
         */
        val coverage: Float? = null,
        /** True when at least one rendition is saved from start to end. */
        val complete: Boolean = false,
        /** True when the clip's main subtitle track is saved. */
        val hasCaptions: Boolean = false
    ) {
        val totalBytes: Long get() = videoBytes + captionBytes
    }

    /**
     * The clip index (titles, rendition identities, caption tracks) lives in
     * the folder next to the media, so a reinstall — or the folder copied to
     * another phone — still lists and replays every saved clip.
     */
    private const val INDEX_FILE = "index.json"

    private fun indexFile(context: Context) = File(storage(context.applicationContext).root, INDEX_FILE)

    /**
     * Records that [videoId] (with its [title] and every rendition [key] the
     * player may cache for it) is being watched. Called each time a clip
     * opens; the index entry is merged, so a refreshed title wins and new
     * keys join the old ones.
     */
    fun registerVideo(context: Context, videoId: String, title: String, keys: List<String>) =
        registerVideo(context, videoId, title, keys, details = null)

    /**
     * Same, from the resolved [details] of the clip: besides the keys it
     * stores what an offline replay needs — the video's shelf data, every
     * rendition's identity and (latest) URL, and the caption tracks — so
     * [offlineCopy] can rebuild the clip without asking any instance.
     */
    fun registerVideo(context: Context, details: OnlineVideoDetails) {
        if (details.offlineCopy) return
        val videoId = details.video.id
        registerVideo(
            context, videoId, details.video.title,
            keys = buildList {
                details.progressiveStreams.forEach { add(streamKey(videoId, it)) }
                add(audioKey(videoId))
            },
            details = details
        )
    }

    private fun registerVideo(context: Context, videoId: String, title: String, keys: List<String>, details: OnlineVideoDetails?) {
        if (keys.isEmpty()) return
        try {
            val file = indexFile(context)
            val root = if (file.isFile) JSONObject(file.readText()) else JSONObject()
            val entry = root.optJSONObject(videoId) ?: JSONObject()
            fillEntry(entry, title, keys, details)
            root.put(videoId, entry)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Merges one clip's registration into its index [entry] (see [registerVideo]). */
    internal fun fillEntry(entry: JSONObject, title: String, keys: List<String>, details: OnlineVideoDetails?) {
        if (title.isNotBlank() || !entry.has("title")) entry.put("title", title)
        val known = entry.optJSONArray("keys") ?: JSONArray()
        val knownSet = buildSet {
            for (i in 0 until known.length()) add(known.optString(i))
        }
        val merged = JSONArray()
        knownSet.union(keys.toSet()).forEach { merged.put(it) }
        entry.put("keys", merged)
        if (details != null) {
            val v = details.video
            if (v.author.isNotBlank()) entry.put("author", v.author)
            v.authorId?.let { entry.put("authorId", it) }
            v.thumbnailUrl?.let { entry.put("thumb", it) }
            if (v.lengthSeconds > 0L) entry.put("length", v.lengthSeconds)
            if (details.progressiveStreams.isNotEmpty()) {
                entry.put("streams", JSONArray().also { arr ->
                    details.progressiveStreams.forEach { st ->
                        arr.put(
                            JSONObject()
                                .put("url", st.url)
                                .put("label", st.qualityLabel)
                                .put("mime", st.mimeType)
                                .put("height", st.height)
                                .put("progressive", st.isProgressive)
                                .apply { st.audioUrl?.let { put("audio", it) } }
                        )
                    }
                })
            }
            if (details.captions.isNotEmpty()) {
                entry.put("captions", JSONArray().also { arr ->
                    details.captions.forEach { t ->
                        arr.put(
                            JSONObject()
                                .put("label", t.label)
                                .put("lang", t.languageCode)
                                .put("url", t.url)
                                .put("auto", t.autoGenerated)
                                .apply { t.mimeType?.let { put("mime", it) } }
                        )
                    }
                })
            }
            entry.put("updated", System.currentTimeMillis())
        }
    }

    /** Cached bytes and known total length of one key ([C.LENGTH_UNSET] when unknown). */
    private fun keyBytes(cache: SimpleCache, key: String): Pair<Long, Long> = try {
        cache.getCachedBytes(key, 0, Long.MAX_VALUE) to ContentMetadata.getContentLength(cache.getContentMetadata(key))
    } catch (e: Exception) {
        0L to C.LENGTH_UNSET.toLong()
    }

    /**
     * How much of one rendition is on disk: for an adaptive pair the lesser
     * of its video and the shared audio track. Null when nothing is known
     * about its length yet; 1f when complete.
     */
    private fun renditionCoverage(cache: SimpleCache, videoId: String, stream: OnlineStream): Float? {
        fun coverage(key: String): Float? {
            val (cached, total) = keyBytes(cache, key)
            if (total <= 0L) return if (cached > 0L) 0f else null
            return (cached.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        val video = coverage(streamKey(videoId, stream)) ?: return null
        if (stream.isProgressive && stream.audioUrl == null) return video
        val audio = coverage(audioKey(videoId)) ?: return 0f
        return minOf(video, audio)
    }

    private const val COMPLETE_COVERAGE = 0.999f

    private fun parseStreams(entry: JSONObject): List<OnlineStream> {
        val arr = entry.optJSONArray("streams") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url")
                if (url.isBlank()) continue
                val audio = o.optString("audio").takeIf { it.isNotBlank() }
                add(
                    OnlineStream(
                        url = url,
                        qualityLabel = o.optString("label"),
                        mimeType = o.optString("mime"),
                        audioUrl = audio,
                        height = o.optInt("height", 0),
                        isProgressive = o.optBoolean("progressive", audio == null)
                    )
                )
            }
        }
    }

    private fun parseCaptionTracks(entry: JSONObject): List<OnlineCaptionTrack> {
        val arr = entry.optJSONArray("captions") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    OnlineCaptionTrack(
                        label = o.optString("label"),
                        languageCode = o.optString("lang"),
                        url = o.optString("url"),
                        autoGenerated = o.optBoolean("auto", false),
                        mimeType = o.optString("mime").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun parseVideo(videoId: String, entry: JSONObject): OnlineVideo = OnlineVideo(
        id = videoId,
        title = entry.optString("title").ifBlank { videoId },
        author = entry.optString("author"),
        authorId = entry.optString("authorId").takeIf { it.isNotBlank() },
        thumbnailUrl = entry.optString("thumb").takeIf { it.isNotBlank() },
        lengthSeconds = entry.optLong("length", 0L)
    )

    /**
     * The playable saved copy of [videoId], or null when nothing of it is
     * on disk: the renditions that have cached bytes, best-covered first
     * (so index 0 is the one to play), with the stored shelf data and
     * caption tracks, flagged [OnlineVideoDetails.offlineCopy]. The player
     * reads the saved ranges from the cache through the same keys; only an
     * unsaved range would need the (stale) URL. Call on an IO dispatcher.
     */
    fun offlineCopy(context: Context, videoId: String): OnlineVideoDetails? {
        val appContext = context.applicationContext
        return try {
            val file = indexFile(appContext)
            if (!file.isFile) return null
            val entry = JSONObject(file.readText()).optJSONObject(videoId) ?: return null
            offlineCopyFrom(videoCache(appContext), videoId, entry)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** [offlineCopy] for one index [entry] against an open [cache]. */
    internal fun offlineCopyFrom(cache: SimpleCache, videoId: String, entry: JSONObject): OnlineVideoDetails? {
        val streams = parseStreams(entry)
        if (streams.isEmpty()) return null
        val covered = streams.mapNotNull { st ->
            val c = renditionCoverage(cache, videoId, st) ?: return@mapNotNull null
            val (cached, _) = keyBytes(cache, streamKey(videoId, st))
            if (cached <= 0L) null else st to c
        }
        if (covered.isEmpty()) return null
        val ordered = covered.sortedWith(compareByDescending<Pair<OnlineStream, Float>> { it.second }.thenByDescending { it.first.height })
        return OnlineVideoDetails(
            video = parseVideo(videoId, entry),
            progressiveStreams = ordered.map { it.first },
            captions = parseCaptionTracks(entry),
            instance = OFFLINE_INSTANCE,
            offlineCopy = true,
            offlineCoverage = ordered.first().second
        )
    }

    /** True when [coverage] means "saved from start to end". */
    fun isComplete(coverage: Float?): Boolean = coverage != null && coverage >= COMPLETE_COVERAGE

    /**
     * Every cached video with the storage it currently holds. Entries whose
     * bytes have fully evaporated (evicted by the LRU or never filled) are
     * pruned from the index on the way. Call on an IO dispatcher.
     */
    fun cachedVideos(context: Context): List<CachedVideo> {
        val appContext = context.applicationContext
        val file = indexFile(appContext)
        if (!file.isFile) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val result = mutableListOf<CachedVideo>()
            val survivors = JSONObject()
            val cache = videoCache(appContext)
            for (videoId in root.keys()) {
                val entry = root.optJSONObject(videoId) ?: continue
                val keys = buildList {
                    val arr = entry.optJSONArray("keys") ?: JSONArray()
                    for (i in 0 until arr.length()) add(arr.optString(i))
                }
                var videoBytes = 0L
                try {
                    keys.forEach { key ->
                        videoBytes += cache.getCachedBytes(key, 0, Long.MAX_VALUE)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val captionBytes = captionsDirSize(appContext, videoId)
                if (videoBytes <= 0L && captionBytes <= 0L) continue // pruned
                survivors.put(videoId, entry)
                val coverage = parseStreams(entry)
                    .mapNotNull { renditionCoverage(cache, videoId, it) }
                    .maxOrNull()
                result += CachedVideo(
                    videoId = videoId,
                    title = entry.optString("title").ifBlank { videoId },
                    videoBytes = videoBytes,
                    captionBytes = captionBytes,
                    coverage = coverage,
                    complete = isComplete(coverage),
                    hasCaptions = File(captionsDir(appContext, videoId), "$SLOT_MAIN.json").isFile
                )
            }
            file.writeText(survivors.toString())
            result.sortedByDescending { it.totalBytes }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Total storage the watch cache holds right now (media + subtitles). */
    fun totalBytes(context: Context): Long {
        val media = try {
            videoCache(context).cacheSpace
        } catch (e: Exception) {
            0L
        }
        return media + captionsRootSize(context)
    }

    /**
     * Deletes everything saved for one video: its cached media spans and its
     * subtitle files. Safe while the clip is playing — the running stream
     * keeps reading, later seeks simply re-download.
     */
    fun deleteVideo(context: Context, videoId: String) {
        val appContext = context.applicationContext
        try {
            val file = indexFile(appContext)
            val root = if (file.isFile) JSONObject(file.readText()) else JSONObject()
            val entry = root.optJSONObject(videoId) ?: return
            val arr = entry.optJSONArray("keys") ?: JSONArray()
            val cache = videoCache(appContext)
            for (i in 0 until arr.length()) {
                removeKey(cache, arr.optString(i))
            }
            root.remove(videoId)
            file.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        captionsDir(appContext, videoId).deleteRecursively()
    }

    /** Wipes the whole watch cache: media spans, subtitles and the index. */
    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        try {
            val cache = videoCache(appContext)
            // Cache#getCachedSpans is per key, so walk the key set. Orphan
            // spans (keys the index never knew) are swept up here too.
            cache.keys.toList().forEach { key ->
                removeKey(cache, key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        captionsRoot(appContext).deleteRecursively()
        indexFile(appContext).delete()
    }

    private fun removeKey(cache: SimpleCache, key: String) {
        try {
            cache.getCachedSpans(key).forEach { span -> removeSpan(cache, span) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeSpan(cache: SimpleCache, span: androidx.media3.datasource.cache.CacheSpan) {
        try {
            cache.removeSpan(span)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun captionsDirSize(context: Context, videoId: String): Long =
        captionsDir(context, videoId).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun captionsRoot(context: Context): File = File(storage(context).root, CAPTIONS_DIR)

    private fun captionsRootSize(context: Context): Long {
        val root = captionsRoot(context)
        if (!root.isDirectory) return 0L
        return root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Which rendition a newly opened clip should start with, honouring the
     * chosen default quality: [QUALITY_AUTO] keeps [OnlineStreamSelector]'s
     * muxed-first policy, [QUALITY_HIGHEST] takes the sharpest, a concrete
     * height takes the best rendition at or below it (or the smallest one
     * when nothing fits under the target).
     */
    fun pickDefaultStreamIndex(streams: List<OnlineStream>, preferredHeight: Int): Int {
        if (streams.isEmpty()) return -1
        if (preferredHeight == QUALITY_AUTO) return OnlineStreamSelector.defaultStreamIndex(streams)
        if (preferredHeight == QUALITY_HIGHEST) {
            return streams.indices.maxByOrNull { idx ->
                val h = streams[idx].height
                if (h > 0) h else -1
            } ?: 0
        }
        val fitting = streams.withIndex().filter { it.value.height in 1..preferredHeight }
        if (fitting.isNotEmpty()) return fitting.maxBy { it.value.height }.index
        return streams.withIndex().minByOrNull { if (it.value.height > 0) it.value.height else Int.MAX_VALUE }?.index ?: 0
    }

    // ────────────────────────── subtitle store ──────────────────────────

    /** What [loadCaptions] hands back for one slot. */
    data class CachedCaptions(
        val languageCode: String,
        val label: String,
        val entries: List<SubtitleEntry>
    )

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)

    private fun captionsDir(context: Context, videoId: String): File =
        File(captionsRoot(context), sanitize(videoId))

    /**
     * Persists one slot's captions as JSON. Call from an IO dispatcher; the
     * write is atomic (temp file + rename) so a killed process cannot leave
     * a half-written file behind.
     */
    fun saveCaptions(
        context: Context,
        videoId: String,
        slot: String,
        languageCode: String,
        label: String,
        entries: List<SubtitleEntry>
    ) = saveCaptionsToDir(captionsDir(context, videoId), slot, languageCode, label, entries)

    /** Disk copy of one slot, or null when nothing usable is stored. */
    fun loadCaptions(context: Context, videoId: String, slot: String): CachedCaptions? =
        loadCaptionsFromDir(captionsDir(context, videoId), slot)

    internal fun saveCaptionsToDir(
        dir: File,
        slot: String,
        languageCode: String,
        label: String,
        entries: List<SubtitleEntry>
    ) {
        if (entries.isEmpty()) return
        try {
            dir.mkdirs()
            val array = JSONArray()
            entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("s", entry.start)
                        .put("e", entry.end)
                        .put("t", entry.text)
                        .apply { if (entry.language.isNotBlank()) put("l", entry.language) }
                )
            }
            val payload = JSONObject()
                .put("lang", languageCode)
                .put("label", label)
                .put("entries", array)
            val target = File(dir, "$slot.json")
            val tmp = File(dir, "$slot.json.tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(target)) {
                tmp.delete()
                target.writeText(payload.toString())
            }
            // A plain .srt twin for the learner (and any other player): the
            // JSON is what the app reads back, the SRT is the human copy.
            try {
                File(dir, "$slot.srt").writeText(toSrt(entries))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** SubRip text of [entries] (HH:MM:SS,mmm). */
    internal fun toSrt(entries: List<SubtitleEntry>): String {
        fun stamp(seconds: Double): String {
            val ms = (seconds.coerceAtLeast(0.0) * 1000).toLong()
            val h = ms / 3_600_000
            val m = ms / 60_000 % 60
            val s = ms / 1000 % 60
            val r = ms % 1000
            return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", h, m, s, r)
        }
        val sb = StringBuilder()
        entries.forEachIndexed { i, e ->
            sb.append(i + 1).append('\n')
                .append(stamp(e.start)).append(" --> ").append(stamp(e.end)).append('\n')
                .append(e.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    internal fun loadCaptionsFromDir(dir: File, slot: String): CachedCaptions? {
        return try {
            val file = File(dir, "$slot.json")
            if (!file.isFile) return null
            val payload = JSONObject(file.readText())
            val array = payload.optJSONArray("entries") ?: return null
            val entries = buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val text = o.optString("t")
                    if (text.isBlank()) continue
                    add(
                        SubtitleEntry(
                            start = o.optDouble("s"),
                            end = o.optDouble("e"),
                            text = text,
                            language = o.optString("l")
                        )
                    )
                }
            }
            if (entries.isEmpty()) null
            else CachedCaptions(
                languageCode = payload.optString("lang"),
                label = payload.optString("label"),
                entries = entries
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Media3's least-recently-used evictor with a limit that can change while
 * the cache is open (the manager's «storage limit» chips): the stock
 * [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor] fixes
 * its limit at construction. Same algorithm: spans ordered by last touch,
 * the oldest removed until the cache fits. [LIMIT_UNLIMITED] disables
 * eviction altogether.
 */
class AdjustableLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compareSpans)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {
        // Nothing to do: spans are reported through onSpanAdded during initialization.
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evictCache(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /** Applies a lowered [maxBytes] now; call while holding the cache's monitor. */
    fun trim(cache: Cache) = evictCache(cache, 0)

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        val limit = maxBytes
        if (limit == Long.MAX_VALUE) return
        while (currentSize + requiredSpace > limit && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }

    private companion object {
        fun compareSpans(a: CacheSpan, b: CacheSpan): Int {
            val delta = a.lastTouchTimestamp - b.lastTouchTimestamp
            if (delta == 0L) return a.compareTo(b)
            return if (delta < 0L) -1 else 1
        }
    }
}

/**
 * The cache's upstream: records a file's total length from a ranged
 * response (`Content-Range: bytes a-b/total`) into the cache's content
 * metadata. CacheDataSource learns the length only from unbounded
 * requests, and the adaptive (DASH) renditions are fetched in ranges
 * only — without this the manager could never call them complete, and
 * the saved copy could not be told from a partial one.
 */
internal class ContentLengthRecordingDataSource(
    private val upstream: DataSource,
    private val cache: Cache
) : DataSource by upstream {

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        try {
            // CacheDataSource hands the upstream the request with the
            // resolved cache key set.
            val key = dataSpec.key ?: return length
            val total = contentRangeTotal(upstream.responseHeaders) ?: return length
            if (ContentMetadata.getContentLength(cache.getContentMetadata(key)) == C.LENGTH_UNSET.toLong()) {
                cache.applyContentMetadataMutations(key, ContentMetadataMutations.setContentLength(ContentMetadataMutations(), total))
            }
        } catch (e: Exception) {
            // Metadata only: playback and caching go on as before.
        }
        return length
    }

    companion object {
        /** The `total` of a `bytes a-b/total` header, or null (`*`, missing, malformed). */
        fun contentRangeTotal(headers: Map<String, List<String>>): Long? {
            val value = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                ?.value?.firstOrNull()?.trim() ?: return null
            val slash = value.lastIndexOf('/')
            if (slash < 0) return null
            return value.substring(slash + 1).trim().toLongOrNull()?.takeIf { it > 0L }
        }
    }
}

/**
 * Volatile, recomposition-proof holder the data source reads at open()
 * time: flipping «ذخیره هنگام پخش» takes effect for the NEXT opened
 * source (seek, quality switch, next clip) without rebuilding the player.
 */
class StreamCacheController {
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var keyFor: (Uri) -> String? = { null }
}

/**
 * A [DataSource.Factory] for the player: while [StreamCacheController.enabled]
 * is set, http(s) opens go through the shared disk cache — writes happen as
 * the learner watches — while everything else, and the whole Video tab,
 * goes through the plain [DefaultDataSource.Factory] exactly as before.
 * Non-http schemes are never cached, so local files and the locally built
 * DASH manifests keep working untouched.
 */
class SaveWhilePlayingDataSourceFactory(
    context: Context,
    private val httpFactory: DefaultHttpDataSource.Factory,
    private val controller: StreamCacheController
) : DataSource.Factory {

    private val appContext = context.applicationContext
    private val plainFactory = DefaultDataSource.Factory(appContext, httpFactory)

    @Volatile
    private var cacheFactory: CacheDataSource.Factory? = null

    private fun cacheDataSourceFactory(): CacheDataSource.Factory =
        cacheFactory ?: synchronized(this) {
            val cache = OnlineWatchCache.videoCache(appContext)
            val upstream = DefaultDataSource.Factory(appContext, httpFactory)
            cacheFactory ?: CacheDataSource.Factory()
                .setCache(cache)
                // Same upstream stack as the uncached path, plus the
                // file-length recorder (see ContentLengthRecordingDataSource).
                .setUpstreamDataSourceFactory(
                    DataSource.Factory { ContentLengthRecordingDataSource(upstream.createDataSource(), cache) }
                )
                // A volatile-read cache key factory: the signed URLs rotate
                // between sessions, so the caller resolves the stable key
                // (video + rendition) at open time; unknown URLs keep their
                // own address as the key.
                .setCacheKeyFactory { dataSpec ->
                    controller.keyFor(dataSpec.uri)
                        ?: dataSpec.key
                        ?: dataSpec.uri.toString()
                }
                // A dead cache must never break playback.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .also { cacheFactory = it }
        }

    override fun createDataSource(): DataSource =
        SwitchingDataSource(
            plain = plainFactory.createDataSource(),
            cacheFactory = { cacheDataSourceFactory().createDataSource() },
            controller = controller
        )

    private class SwitchingDataSource(
        private val plain: DataSource,
        private val cacheFactory: () -> DataSource,
        private val controller: StreamCacheController
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()

        // Built on first cached open, on a player loading thread: the
        // cache's initial index read must stay off the main thread.
        private var cached: DataSource? = null

        private fun cacheDataSource(): DataSource = cached ?: synchronized(this) {
            cached ?: cacheFactory()
                .also { ds -> synchronized(transferListeners) { transferListeners.forEach(ds::addTransferListener) } }
                .also { cached = it }
        }

        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            plain.addTransferListener(transferListener)
            cached?.addTransferListener(transferListener)
            synchronized(transferListeners) { transferListeners.add(transferListener) }
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme?.lowercase()
            val useCache = controller.enabled && (scheme == "http" || scheme == "https")
            val source = if (useCache) cacheDataSource() else plain
            return source.open(dataSpec).also { active = source }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(active) { "read before open" }.read(buffer, offset, length)

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                active?.close()
            } finally {
                active = null
            }
        }
    }
}
