package com.example.logic

import android.content.Context
import android.content.SharedPreferences
import com.example.model.OnlineVideo
import org.json.JSONArray
import org.json.JSONObject

/**
 * The learner's own shelf in the Online hub:
 *  * **Saved** («ذخیره‌شده‌ها») — clips bookmarked with the ⭐ button;
 *  * **History** («تاریخچه») — clips opened recently, newest first;
 *  * **Seen marks** — per followed channel, the upload time the learner has
 *    already caught up to, which drives the "new video" dot on the channel
 *    tray.
 *
 * Everything lives in one small SharedPreferences file as JSON. The list
 * operations are pure functions (unit tested); the load/save helpers only
 * do the I/O.
 */
object OnlineLibraryStore {

    private const val PREFS = "online_library"
    private const val KEY_SAVED = "saved_v1"
    private const val KEY_HISTORY = "history_v1"
    private const val KEY_SEEN = "channel_seen_v1"

    const val MAX_HISTORY = 100
    const val MAX_SAVED = 500

    /** A clip on the shelf and when it got there (epoch millis). */
    data class Entry(val video: OnlineVideo, val at: Long)

    // ── Pure list operations ──

    /** [video] moved (or added) to the top of the history, capped at [max]. */
    fun addToHistory(list: List<Entry>, video: OnlineVideo, now: Long, max: Int = MAX_HISTORY): List<Entry> {
        if (video.id.isBlank()) return list
        val previous = list.firstOrNull { it.video.id == video.id }?.video
        val merged = if (previous != null) mergeMetadata(previous, video) else video
        return (listOf(Entry(merged, now)) + list.filter { it.video.id != video.id }).take(max)
    }

    /** Saves [video] at the top, or removes it when it is already saved. */
    fun toggleSaved(list: List<Entry>, video: OnlineVideo, now: Long): List<Entry> =
        if (list.any { it.video.id == video.id }) remove(list, video.id)
        else (listOf(Entry(video, now)) + list).take(MAX_SAVED)

    fun remove(list: List<Entry>, videoId: String): List<Entry> = list.filter { it.video.id != videoId }

    /**
     * Refreshes the stored metadata of [video] (title, thumbnail, length …
     * once the player resolved them) without moving it in the list.
     */
    fun refresh(list: List<Entry>, video: OnlineVideo): List<Entry> =
        list.map { if (it.video.id == video.id) it.copy(video = mergeMetadata(it.video, video)) else it }

    /** Newer non-blank fields win; blanks never wipe what was known. */
    internal fun mergeMetadata(old: OnlineVideo, new: OnlineVideo): OnlineVideo = OnlineVideo(
        id = old.id,
        title = new.title.ifBlank { old.title },
        author = new.author.ifBlank { old.author },
        authorId = new.authorId ?: old.authorId,
        thumbnailUrl = new.thumbnailUrl ?: old.thumbnailUrl,
        lengthSeconds = if (new.lengthSeconds > 0) new.lengthSeconds else old.lengthSeconds,
        viewCount = new.viewCount ?: old.viewCount,
        publishedText = new.publishedText ?: old.publishedText,
        publishedAt = if (new.publishedAt > 0) new.publishedAt else old.publishedAt,
        isLive = new.isLive || old.isLive
    )

    /**
     * Channels of [feed] with an upload newer than the learner's seen mark.
     * A channel with no mark yet is never "new": its first sighting only
     * sets the baseline (see [baselineSeen]), so a fresh install does not
     * light up every avatar at once.
     */
    fun channelsWithNewVideos(feed: List<OnlineVideo>, seen: Map<String, Long>): Set<String> =
        latestUploadByChannel(feed).filter { (channelId, latest) ->
            val mark = seen[channelId]
            mark != null && latest > mark
        }.keys

    /** Adds a seen mark for every channel of [feed] that has none yet. */
    fun baselineSeen(feed: List<OnlineVideo>, seen: Map<String, Long>): Map<String, Long> {
        val latest = latestUploadByChannel(feed)
        val missing = latest.filterKeys { it !in seen }
        return if (missing.isEmpty()) seen else seen + missing
    }

    /** Newest known upload time (epoch seconds) per channel in [feed]. */
    internal fun latestUploadByChannel(feed: List<OnlineVideo>): Map<String, Long> =
        feed.filter { !it.authorId.isNullOrBlank() && it.publishedAt > 0 }
            .groupBy { it.authorId!! }
            .mapValues { (_, videos) -> videos.maxOf { it.publishedAt } }

    // ── JSON ──

    fun encode(list: List<Entry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            val v = e.video
            arr.put(
                JSONObject()
                    .put("id", v.id)
                    .put("title", v.title)
                    .put("author", v.author)
                    .put("authorId", v.authorId ?: "")
                    .put("thumbnailUrl", v.thumbnailUrl ?: "")
                    .put("lengthSeconds", v.lengthSeconds)
                    .put("viewCount", v.viewCount ?: -1L)
                    .put("publishedText", v.publishedText ?: "")
                    .put("publishedAt", v.publishedAt)
                    .put("isLive", v.isLive)
                    .put("at", e.at)
            )
        }
        return arr.toString()
    }

    fun decode(text: String?): List<Entry> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                Entry(
                    video = OnlineVideo(
                        id = id,
                        title = o.optString("title"),
                        author = o.optString("author"),
                        authorId = o.optString("authorId").takeIf { it.isNotBlank() },
                        thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        lengthSeconds = o.optLong("lengthSeconds"),
                        viewCount = o.optLong("viewCount", -1L).takeIf { it >= 0 },
                        publishedText = o.optString("publishedText").takeIf { it.isNotBlank() },
                        publishedAt = o.optLong("publishedAt"),
                        isLive = o.optBoolean("isLive")
                    ),
                    at = o.optLong("at")
                )
            }.distinctBy { it.video.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encodeSeen(seen: Map<String, Long>): String =
        JSONObject().apply { seen.forEach { (k, v) -> put(k, v) } }.toString()

    fun decodeSeen(text: String?): Map<String, Long> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(text)
            o.keys().asSequence().associateWith { o.optLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Storage ──

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSaved(context: Context): List<Entry> = decode(prefs(context).getString(KEY_SAVED, null))
    fun storeSaved(context: Context, list: List<Entry>) {
        prefs(context).edit().putString(KEY_SAVED, encode(list)).apply()
    }

    fun loadHistory(context: Context): List<Entry> = decode(prefs(context).getString(KEY_HISTORY, null))
    fun storeHistory(context: Context, list: List<Entry>) {
        prefs(context).edit().putString(KEY_HISTORY, encode(list)).apply()
    }

    fun loadSeen(context: Context): Map<String, Long> = decodeSeen(prefs(context).getString(KEY_SEEN, null))
    fun storeSeen(context: Context, seen: Map<String, Long>) {
        prefs(context).edit().putString(KEY_SEEN, encodeSeen(seen)).apply()
    }
}
