package com.example.logic

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.model.OnlineStream
import com.example.model.SubtitleEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
 * The cache lives in app-private storage; a least-recently-used evictor
 * keeps it bounded.
 */
object OnlineWatchCache {

    private const val ROOT_DIR = "online_watch_cache"
    private const val VIDEO_DIR = "video"
    private const val CAPTIONS_DIR = "captions"

    /** Generous for a rewatch-oriented learner, bounded for the device. */
    const val MAX_VIDEO_CACHE_BYTES: Long = 512L * 1024 * 1024

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
     * The one [SimpleCache] for the video cache directory. SimpleCache
     * refuses a second instance on the same folder, so this is a
     * double-checked singleton; the first call happens on a player loading
     * thread, never the main thread.
     */
    fun videoCache(context: Context): SimpleCache = videoCache ?: synchronized(this) {
        videoCache ?: SimpleCache(
            File(context.filesDir, "$ROOT_DIR/$VIDEO_DIR"),
            LeastRecentlyUsedCacheEvictor(MAX_VIDEO_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { videoCache = it }
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
        val captionBytes: Long
    ) {
        val totalBytes: Long get() = videoBytes + captionBytes
    }

    private fun indexFile(context: Context) =
        File(context.filesDir, "$ROOT_DIR/index.json")

    /**
     * Records that [videoId] (with its [title] and every rendition [key] the
     * player may cache for it) is being watched. Called each time a clip
     * opens; the index entry is merged, so a refreshed title wins and new
     * keys join the old ones.
     */
    fun registerVideo(context: Context, videoId: String, title: String, keys: List<String>) {
        if (keys.isEmpty()) return
        try {
            val file = indexFile(context)
            val root = if (file.isFile) JSONObject(file.readText()) else JSONObject()
            val entry = root.optJSONObject(videoId) ?: JSONObject()
            entry.put("title", title)
            val known = entry.optJSONArray("keys") ?: JSONArray()
            val knownSet = buildSet {
                for (i in 0 until known.length()) add(known.optString(i))
            }
            val merged = JSONArray()
            knownSet.union(keys.toSet()).forEach { merged.put(it) }
            entry.put("keys", merged)
            root.put(videoId, entry)
            file.parentFile?.mkdirs()
            file.writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
                result += CachedVideo(
                    videoId = videoId,
                    title = entry.optString("title").ifBlank { videoId },
                    videoBytes = videoBytes,
                    captionBytes = captionBytes
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
        File(appContext.filesDir, "$ROOT_DIR/$CAPTIONS_DIR").deleteRecursively()
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

    private fun captionsRootSize(context: Context): Long {
        val root = File(context.filesDir, "$ROOT_DIR/$CAPTIONS_DIR")
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
        File(File(context.filesDir, "$ROOT_DIR/$CAPTIONS_DIR"), sanitize(videoId))

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            cacheFactory ?: CacheDataSource.Factory()
                .setCache(OnlineWatchCache.videoCache(appContext))
                // Same upstream stack as the uncached path.
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(appContext, httpFactory))
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
