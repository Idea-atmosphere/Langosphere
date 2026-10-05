package com.example.logic

import android.util.Base64
import com.example.model.OnlineCaptionTrack
import com.example.model.OnlineChannel
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import com.example.model.OnlineStream
import com.example.model.OnlineVideo
import com.example.model.OnlineVideoDetails
import com.example.model.OnlineVideoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to Invidious and Piped instances for the Online tab.
 *
 * Every public function takes the user's instance list and walks it in
 * order until one instance answers (skipping disabled ones). The instance
 * that succeeded is reported back so the caller can (a) remember it as the
 * preferred one and (b) fetch captions for a video from the *same* instance
 * that produced the stream URLs — caption URLs returned by an instance are
 * only valid on that instance.
 *
 * Only the two APIs' publicly documented JSON is used:
 *  * Invidious: `/api/v1/videos/:id?local=1`, `/api/v1/captions/:id`,
 *    `/api/v1/channels/:id`, `/api/v1/channels/:id/videos`, `/api/v1/search`,
 *    `/api/v1/resolveurl`, plus the Atom feed at `/feed/channel/:id`.
 *  * Piped: `/streams/:id`, `/channel/:id`, `/nextpage/channel/:id`,
 *    `/search`, `/c/:name`, `/user/:name`, `/@/:handle`.
 *
 * Channel listings have one extra, last-resort source: the channel's public
 * Atom feed on youtube.com (`/feeds/videos.xml?channel_id=`). Public
 * instances are frequently rate-limited by YouTube and answer channel
 * requests with an empty list or an HTML error page while the feed — which
 * needs no API and no login — keeps working. Playback and captions always
 * go through an instance; the feed is only used to *list* videos.
 */
object OnlineVideoRepository {

    open class OnlineException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * A non-2xx answer. [code] plus [structured] (the body was the API's own
     * JSON error, e.g. Piped answers a bot-blocked `/streams` with a 500 and
     * `{"error": …}`) let the failover tell a dead host from a live one that
     * merely refused this request.
     */
    class HttpException(val code: Int, message: String, val structured: Boolean = false) : OnlineException(message)

    /** An instance answered, but with an empty list — treated as a failure so the next source is tried. */
    class EmptyPageException : OnlineException("No videos returned")

    /**
     * Result of a call plus the instance that served it. [instance] is null
     * when the answer did not come from an instance at all (the youtube.com
     * feed fallback), so callers must not remember it as "preferred".
     */
    data class Served<T>(val value: T, val instance: OnlineInstance?)

    /** One failed attempt, kept so the UI can show what went wrong per source. */
    data class Failure(val host: String, val reason: String)

    class AllInstancesFailed(val failures: List<Failure>) :
        IOException(failures.joinToString("\n") { "${it.host}: ${it.reason}" })

    /** Result of the instance health check; latency is the full API request round-trip. */
    data class InstanceProbe(val latencyMs: Long)

    /** Channel Atom feed on youtube.com (used when no instance can list a channel). */
    private const val YOUTUBE_FEED = "https://www.youtube.com/feeds/videos.xml?channel_id="
    /** YouTube's public caption endpoint — last resort when no instance lists a usable track. */
    private const val YOUTUBE_TIMEDTEXT = "https://www.youtube.com/api/timedtext"
    /*
     * A small, stable public video is used for the manual health check. This
     * deliberately probes the same video-details endpoint used for playback,
     * rather than merely checking whether the host serves an HTML homepage.
     */
    private const val PROBE_VIDEO_ID = "dQw4w9WgXcQ"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val USER_AGENT = "Langosphere/1.0 (+https://github.com/gysysy/Langosphere)"

    /**
     * Instances that were unreachable (connection error, timeout, gateway
     * 5xx) recently, by base URL → time of failure. For a couple of minutes
     * they are tried *last*, or — by callers that have another source, such
     * as the channel feed — not at all, so a dead host does not add its
     * connect timeout to every one of the fourteen channels in the feed.
     * An instance that *answered* (even with an error) is never in here.
     */
    private val recentOutages = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val OUTAGE_MEMORY_MS = 2 * 60 * 1000L

    // ── Public API ──

    /**
     * How many instances one playback request tries: the first one plus up
     * to three automatic retries on the next mirrors. A 403 (YouTube blocked
     * that instance's IP), a 5xx (instance overloaded or bot-blocked) or a
     * timeout moves on to the next instance; after this many attempts the
     * caller shows the error / offers the fallback web player instead of
     * leaving the user in front of an endless spinner.
     */
    const val PLAYBACK_MAX_ATTEMPTS = 4

    /**
     * Full details of one video (streams, captions, related), with automatic
     * failover across [instances] — at most [maxAttempts] of them are asked.
     */
    suspend fun videoDetails(
        instances: List<OnlineInstance>,
        videoId: String,
        preferred: String? = null,
        maxAttempts: Int = PLAYBACK_MAX_ATTEMPTS
    ): Served<OnlineVideoDetails> =
        firstSuccess(instances, preferred, maxAttempts = maxAttempts) { inst ->
            val details = when (inst.kind) {
                OnlineInstanceKind.INVIDIOUS -> invidiousVideo(inst, videoId)
                OnlineInstanceKind.PIPED -> pipedVideo(inst, videoId)
            }
            // A metadata-only response is not a playback success. Continue to
            // the next configured instance instead of opening a dead player.
            if (details.playbackUrl == null) throw OnlineException("No playable stream returned")
            details
        }

    /**
     * Latest uploads of a channel, newest first. [continuation] is the token
     * from the previous page.
     *
     * The first page is tried in this order, stopping at the first source
     * that returns at least one video:
     *  1. each enabled instance — Piped `/channel/:id`; Invidious
     *     `/api/v1/channels/:id/videos`, then that instance's own Atom feed
     *     `/feed/channel/:id` (kept alive on most instances even when the
     *     JSON API is switched off);
     *  2. the channel's Atom feed on youtube.com.
     * Later pages only exist for instance-served lists (feeds have no paging).
     */
    suspend fun channelVideos(instances: List<OnlineInstance>, channelId: String, continuation: String? = null, preferred: String? = null): Served<OnlineVideoPage> {
        if (continuation != null) {
            return firstSuccess(instances, preferred) { inst ->
                when (inst.kind) {
                    OnlineInstanceKind.INVIDIOUS -> invidiousChannelVideos(inst, channelId, continuation)
                    OnlineInstanceKind.PIPED -> pipedChannelVideos(inst, channelId, continuation)
                }
            }
        }
        val failures = mutableListOf<Failure>()
        try {
            // Instances that were unreachable a moment ago are skipped here
            // (not merely tried last): with fourteen channels in the feed, a
            // dead host would otherwise add its connect timeout to each one.
            return firstSuccess(instances, preferred, skipRecentOutages = true) { inst ->
                val page = when (inst.kind) {
                    OnlineInstanceKind.INVIDIOUS -> invidiousChannelVideosOrFeed(inst, channelId)
                    OnlineInstanceKind.PIPED -> pipedChannelVideos(inst, channelId, null)
                }
                if (page.videos.isEmpty()) throw EmptyPageException()
                page
            }
        } catch (e: AllInstancesFailed) {
            failures.addAll(e.failures)
        } catch (e: OnlineException) {
            // e.g. every instance is disabled — the feed can still answer.
            failures.add(Failure("instances", e.message ?: "unavailable"))
        }
        return withContext(Dispatchers.IO) {
            try {
                Served(feedPage(YOUTUBE_FEED + enc(channelId), channelId), null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add(Failure("youtube.com", e.message ?: e.javaClass.simpleName))
                throw AllInstancesFailed(failures)
            }
        }
    }

    /** Channel name / avatar / description (falls back to the name in the youtube.com feed). */
    suspend fun channelInfo(instances: List<OnlineInstance>, channelId: String, preferred: String? = null): Served<OnlineChannel> {
        val failures = mutableListOf<Failure>()
        try {
            return firstSuccess(instances, preferred, skipRecentOutages = true) { inst ->
                when (inst.kind) {
                    OnlineInstanceKind.INVIDIOUS -> invidiousChannelInfo(inst, channelId)
                    OnlineInstanceKind.PIPED -> pipedChannelInfo(inst, "channel/$channelId")
                }
            }
        } catch (e: AllInstancesFailed) {
            failures.addAll(e.failures)
        } catch (e: OnlineException) {
            failures.add(Failure("instances", e.message ?: "unavailable"))
        }
        return withContext(Dispatchers.IO) {
            try {
                val feed = OnlineFeedParser.parse(getFeedXml(YOUTUBE_FEED + enc(channelId)), channelId)
                val name = feed.channelTitle?.takeIf { it.isNotBlank() } ?: throw OnlineException("Channel not found")
                Served(OnlineChannel(id = feed.channelId ?: channelId, name = name), null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add(Failure("youtube.com", e.message ?: e.javaClass.simpleName))
                throw AllInstancesFailed(failures)
            }
        }
    }

    /** Resolves `@handle`, `c/name` or `user/name` to a channel. */
    suspend fun resolveChannelHandle(instances: List<OnlineInstance>, handle: String, preferred: String? = null): Served<OnlineChannel> =
        firstSuccess(instances, preferred) { inst ->
            when (inst.kind) {
                OnlineInstanceKind.INVIDIOUS -> {
                    val url = "https://www.youtube.com/" + handle.trimStart('/')
                    val json = getJson(inst, "/api/v1/resolveurl?url=${enc(url)}")
                    val ucid = json.optString("ucid").takeIf { it.isNotBlank() }
                        ?: throw OnlineException("Could not resolve $handle")
                    invidiousChannelInfo(inst, ucid)
                }
                OnlineInstanceKind.PIPED -> {
                    val path = when {
                        handle.startsWith("@") -> "@/${handle.removePrefix("@")}"
                        else -> handle.trimStart('/')
                    }
                    pipedChannelInfo(inst, path)
                }
            }
        }

    /** Video search. */
    suspend fun searchVideos(instances: List<OnlineInstance>, query: String, preferred: String? = null): Served<List<OnlineVideo>> =
        firstSuccess(instances, preferred) { inst ->
            when (inst.kind) {
                OnlineInstanceKind.INVIDIOUS -> {
                    val arr = getJsonArray(inst, "/api/v1/search?q=${enc(query)}&type=video")
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                        .filter { it.optString("type") == "video" }
                        .map { invidiousVideoItem(inst, it) }
                }
                OnlineInstanceKind.PIPED -> {
                    val json = getJson(inst, "/search?q=${enc(query)}&filter=videos")
                    pipedStreamItems(json.optJSONArray("items"))
                }
            }
        }

    /** Channel search (to follow new channels from inside the app). */
    suspend fun searchChannels(instances: List<OnlineInstance>, query: String, preferred: String? = null): Served<List<OnlineChannel>> =
        firstSuccess(instances, preferred) { inst ->
            when (inst.kind) {
                OnlineInstanceKind.INVIDIOUS -> {
                    val arr = getJsonArray(inst, "/api/v1/search?q=${enc(query)}&type=channel")
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                        .filter { it.optString("type") == "channel" }
                        .map { o ->
                            OnlineChannel(
                                id = o.optString("authorId"),
                                name = o.optString("author"),
                                avatarUrl = bestThumb(inst, o.optJSONArray("authorThumbnails")),
                                description = o.optString("description").takeIf { it.isNotBlank() }
                            )
                        }
                        .filter { it.id.isNotBlank() }
                }
                OnlineInstanceKind.PIPED -> {
                    val json = getJson(inst, "/search?q=${enc(query)}&filter=channels")
                    val items = json.optJSONArray("items") ?: JSONArray()
                    (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                        .filter { it.optString("type") == "channel" }
                        .map { o ->
                            OnlineChannel(
                                id = o.optString("url").substringAfter("/channel/").substringBefore('/'),
                                name = o.optString("name"),
                                avatarUrl = absolutize(inst, o.optString("thumbnail")),
                                description = o.optString("description").takeIf { it.isNotBlank() }
                            )
                        }
                        .filter { it.id.startsWith("UC") }
                }
            }
        }

    /**
     * Downloads one caption track and parses it into subtitle entries.
     * Invidious serves WebVTT; Piped serves whatever YouTube gave it (VTT or
     * TTML), so the text is sniffed rather than trusting the declared type.
     */
    suspend fun fetchCaptions(track: OnlineCaptionTrack, lang: String): List<com.example.model.SubtitleEntry> =
        withContext(Dispatchers.IO) {
            val body = getText(track.url)
            OnlineCaptionParser.parse(body, lang)
        }

    /** One caption track that was downloaded and parsed into at least one cue. */
    data class CaptionResult(
        val track: OnlineCaptionTrack,
        val entries: List<com.example.model.SubtitleEntry>,
        /** Every track the winning source listed (for the caption picker). */
        val tracks: List<OnlineCaptionTrack>
    )

    /**
     * Finds usable captions for [videoId] no matter which source plays it.
     *
     * Playback and captions used to be tied together: captions were only
     * asked from the instance that produced the stream, so when that
     * instance's caption URL failed — or when no instance could play the clip
     * and the web fallback took over — the transcript stayed empty. The
     * lookup now walks every source and stops at the first track that parses
     * into at least one cue:
     *  1. [knownTracks] (the tracks the playing instance already listed),
     *  1b. YouTube's InnerTube player response ([InnerTubeClient]) — the
     *     same tracks YouTube's own player renders, as WebVTT,
     *  2. the caption lists of the other enabled instances — Invidious
     *     `/api/v1/captions/:id`, Piped `/streams/:id` — at most
     *     [maxInstanceAttempts] of them,
     *  3. YouTube's own timedtext endpoint (manual, then auto-generated
     *     English), the same public host the channel feed already uses.
     * Returns null when no source had a non-empty track (the clip has no
     * captions, or every source refused).
     */
    suspend fun findCaptions(
        instances: List<OnlineInstance>,
        videoId: String,
        knownTracks: List<OnlineCaptionTrack>,
        knownInstance: OnlineInstance?,
        preferred: String? = null,
        maxInstanceAttempts: Int = 4
    ): CaptionResult? = withContext(Dispatchers.IO) {
        // 1. The playing instance's own tracks.
        tryTracks(knownTracks)?.let { return@withContext it }

        // 1b. YouTube's own InnerTube player response — the exact tracks the
        //     YouTube player shows, fetched as WebVTT (no instance needed).
        if (!InnerTubeClient.isDirect(knownInstance)) {
            val direct = try {
                InnerTubeClient.captionTracks(videoId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            tryTracks(direct)?.let { return@withContext it }
        }

        // 2. Other instances' caption lists.
        val others = orderInstances(
            instances.filter { it.enabled && it.baseUrl != knownInstance?.baseUrl },
            preferred
        )
        var attempts = 0
        for (inst in others) {
            if (attempts >= maxInstanceAttempts) break
            if (hadRecentOutage(inst)) continue
            attempts++
            val tracks = try {
                captionTracksFrom(inst, videoId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isOutage(e)) recentOutages[inst.baseUrl] = System.currentTimeMillis()
                continue
            }
            tryTracks(tracks)?.let { return@withContext it }
        }

        // 3. YouTube timedtext (works for many clips without any instance).
        val timed = timedTextTracks(videoId)
        tryTracks(timed, limit = timed.size)
    }

    /** Downloads the best few of [tracks] in order; the first non-empty one wins. */
    private suspend fun tryTracks(tracks: List<OnlineCaptionTrack>, limit: Int = 3): CaptionResult? {
        if (tracks.isEmpty()) return null
        for (track in orderCaptionTracks(tracks).take(limit)) {
            val entries = try {
                fetchCaptions(track, track.languageCode.ifBlank { "en" })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            if (entries.isNotEmpty()) return CaptionResult(track, entries, tracks)
        }
        return null
    }

    /**
     * Download order for caption tracks: English written by a person, then
     * English auto-captions, then any other manual track, then the rest.
     */
    internal fun orderCaptionTracks(tracks: List<OnlineCaptionTrack>): List<OnlineCaptionTrack> =
        tracks.sortedBy { t ->
            when {
                t.isEnglish && !t.autoGenerated -> 0
                t.isEnglish -> 1
                !t.autoGenerated -> 2
                else -> 3
            }
        }

    /** The caption list one instance offers for [videoId] (no stream lookup needed). */
    private fun captionTracksFrom(inst: OnlineInstance, videoId: String): List<OnlineCaptionTrack> =
        when (inst.kind) {
            OnlineInstanceKind.INVIDIOUS ->
                parseInvidiousCaptions(inst, getJson(inst, "/api/v1/captions/${enc(videoId)}").optJSONArray("captions"))
            OnlineInstanceKind.PIPED ->
                parsePipedCaptions(inst, getJson(inst, "/streams/${enc(videoId)}").optJSONArray("subtitles"))
        }

    /** YouTube's public timedtext URLs for [videoId]: manual English first, then auto-generated. */
    internal fun timedTextTracks(videoId: String): List<OnlineCaptionTrack> {
        if (!Regex("^[A-Za-z0-9_-]{6,20}$").matches(videoId)) return emptyList()
        val base = "$YOUTUBE_TIMEDTEXT?v=${enc(videoId)}&fmt=vtt"
        return listOf(
            OnlineCaptionTrack("English (YouTube)", "en", "$base&lang=en", autoGenerated = false, mimeType = "text/vtt"),
            OnlineCaptionTrack("English (auto-generated, YouTube)", "en", "$base&lang=en&kind=asr", autoGenerated = true, mimeType = "text/vtt")
        )
    }

    /** Invidious caption entries (`label`, `language_code`/`languageCode`, relative `url`). */
    internal fun parseInvidiousCaptions(inst: OnlineInstance, caps: JSONArray?): List<OnlineCaptionTrack> {
        if (caps == null) return emptyList()
        return (0 until caps.length()).mapNotNull { caps.optJSONObject(it) }.mapNotNull { c ->
            val rel = c.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = c.optString("label")
            OnlineCaptionTrack(
                label = label,
                // The videos endpoint spells it language_code; the captions
                // endpoint spells it languageCode. Accept both.
                languageCode = c.optString("language_code").ifBlank { c.optString("languageCode") },
                url = absolutize(inst, rel) ?: return@mapNotNull null,
                autoGenerated = label.contains("auto-generated", ignoreCase = true) || label.contains("auto", ignoreCase = true) && label.contains("generated", ignoreCase = true),
                mimeType = "text/vtt"
            )
        }
    }

    /** Piped subtitle entries (`name`, `code`, absolute `url`, `autoGenerated`, `mimeType`). */
    internal fun parsePipedCaptions(inst: OnlineInstance, subs: JSONArray?): List<OnlineCaptionTrack> {
        if (subs == null) return emptyList()
        return (0 until subs.length()).mapNotNull { subs.optJSONObject(it) }.mapNotNull { c ->
            val url = absolutize(inst, c.optString("url")) ?: return@mapNotNull null
            OnlineCaptionTrack(
                label = c.optString("name").ifBlank { c.optString("code") },
                languageCode = c.optString("code"),
                url = url,
                autoGenerated = c.optBoolean("autoGenerated"),
                mimeType = c.optString("mimeType").takeIf { it.isNotBlank() }
            )
        }
    }

    /** Raw caption text (VTT/TTML as served) — used for "copy" / "export as is". */
    suspend fun fetchCaptionText(track: OnlineCaptionTrack): String =
        withContext(Dispatchers.IO) { getText(track.url) }

    /**
     * Checks the actual playback API and rejects hosts that only expose a
     * landing page, an HTML challenge, an API error, or no playable stream.
     * The UI uses this for its "check all" action and disables failed entries
     * automatically so the next feed/play request does not waste time on them.
     */
    suspend fun probeInstance(instance: OnlineInstance): InstanceProbe = withContext(Dispatchers.IO) {
        val path = when (instance.kind) {
            OnlineInstanceKind.INVIDIOUS -> "/api/v1/videos/$PROBE_VIDEO_ID?local=1"
            OnlineInstanceKind.PIPED -> "/streams/$PROBE_VIDEO_ID"
        }
        val startedAt = System.nanoTime()
        val request = Request.Builder()
            .url(instance.baseUrl + path)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        probeClient.newCall(request).execute().use { response ->
            val body = response.body?.string()?.trim().orEmpty()
            if (!response.isSuccessful) {
                val detail = if (body.startsWith("{")) jsonErrorMessage(body) else null
                throw HttpException(response.code, "HTTP ${response.code}${detail?.let { ": $it" } ?: ""}", detail != null)
            }
            if (body.isEmpty()) throw OnlineException("Empty response")
            if (!body.startsWith("{") && !body.startsWith("[")) {
                throw OnlineException(describeNonJson(body))
            }
            jsonErrorMessage(body)?.let { throw OnlineException(it) }
            val json = JSONObject(body)
            val playable = when (instance.kind) {
                OnlineInstanceKind.INVIDIOUS ->
                    json.optJSONArray("formatStreams").hasUsableStream() ||
                        (
                            json.optJSONArray("adaptiveFormats").hasMimeStream("video/") &&
                                json.optJSONArray("adaptiveFormats").hasMimeStream("audio/")
                            ) ||
                        json.optString("hlsUrl").isHttpUrl() ||
                        json.optString("dashUrl").isHttpUrl()
                OnlineInstanceKind.PIPED ->
                    json.optJSONArray("videoStreams").hasUsableStream(requireMuxed = true) ||
                        (
                            json.optJSONArray("videoStreams").hasVideoOnlyStream() &&
                                json.optJSONArray("audioStreams").hasUsableStream()
                            ) ||
                        json.optString("hls").isHttpUrl() ||
                        json.optString("dash").isHttpUrl()
            }
            if (!playable) throw OnlineException("No playable stream returned")
            InstanceProbe(((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L))
        }
    }

    // ── Failover ──

    /**
     * Runs [block] against the enabled instances in order until one succeeds.
     * With [skipRecentOutages] the instances that were unreachable in the
     * last [OUTAGE_MEMORY_MS] are not tried at all (callers that have another
     * source, such as the channel feed); otherwise they are merely tried last.
     */
    private suspend fun <T> firstSuccess(
        instances: List<OnlineInstance>,
        preferred: String?,
        skipRecentOutages: Boolean = false,
        maxAttempts: Int = Int.MAX_VALUE,
        block: suspend (OnlineInstance) -> T
    ): Served<T> = withContext(Dispatchers.IO) {
        val enabled = instances.filter { it.enabled }
        if (enabled.isEmpty()) throw OnlineException("No instance is enabled")
        val failures = mutableListOf<Failure>()
        var attempts = 0
        for (inst in orderInstances(enabled, preferred)) {
            if (skipRecentOutages && hadRecentOutage(inst)) {
                failures.add(Failure(inst.host, "unreachable a moment ago, skipped"))
                continue
            }
            if (attempts >= maxAttempts) break
            attempts++
            try {
                val value = block(inst)
                recentOutages.remove(inst.baseUrl)
                return@withContext Served(value, inst)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isOutage(e)) recentOutages[inst.baseUrl] = System.currentTimeMillis()
                failures.add(Failure(inst.host, e.message ?: e.javaClass.simpleName))
            }
        }
        throw AllInstancesFailed(failures)
    }

    private fun hadRecentOutage(inst: OnlineInstance): Boolean =
        (recentOutages[inst.baseUrl] ?: 0L) > System.currentTimeMillis() - OUTAGE_MEMORY_MS

    /** Preferred instance first, recently unreachable ones last, the user's order otherwise. */
    internal fun orderInstances(enabled: List<OnlineInstance>, preferred: String?): List<OnlineInstance> =
        enabled.sortedWith(compareBy<OnlineInstance>({ hadRecentOutage(it) }, { it.baseUrl != preferred }))

    /** Forget remembered outages (the user edited the instance list and wants a fresh try). */
    fun forgetOutages() = recentOutages.clear()

    /** True for "the host is down" failures, false for "the host answered but refused". */
    private fun isOutage(e: Exception): Boolean = when (e) {
        is HttpException -> e.code >= 500 && !e.structured
        is OnlineException -> false
        is IOException -> true // UnknownHost, Connect, SocketTimeout, SSL …
        else -> false
    }

    // ── Invidious ──

    private fun invidiousVideo(inst: OnlineInstance, videoId: String): OnlineVideoDetails {
        // local=1 makes the instance proxy the googlevideo URLs, which are
        // otherwise locked to the instance's IP address.
        val json = getJson(inst, "/api/v1/videos/${enc(videoId)}?local=1")
        val video = OnlineVideo(
            id = json.optString("videoId", videoId),
            title = json.optString("title"),
            author = json.optString("author"),
            authorId = json.optString("authorId").takeIf { it.isNotBlank() },
            thumbnailUrl = bestThumb(inst, json.optJSONArray("videoThumbnails")),
            lengthSeconds = json.optLong("lengthSeconds"),
            viewCount = json.optLong("viewCount").takeIf { json.has("viewCount") },
            publishedText = json.optString("publishedText").takeIf { it.isNotBlank() },
            publishedAt = json.optLong("published"),
            isLive = json.optBoolean("liveNow")
        )
        val formats = json.optJSONArray("formatStreams") ?: JSONArray()
        val progressive = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
            .mapNotNull { f ->
                val url = streamUrl(inst, f.optString("url")) ?: return@mapNotNull null
                val type = f.optString("type")
                val label = f.optString("qualityLabel").ifBlank { f.optString("resolution").ifBlank { f.optString("quality") } }
                OnlineStream(
                    url = url,
                    qualityLabel = label,
                    mimeType = type.substringBefore(';').ifBlank { "video/mp4" },
                    height = heightOf(label),
                    isProgressive = true
                )
            }
            .sortedByDescending { it.height }
        val adaptiveFormats = json.optJSONArray("adaptiveFormats") ?: JSONArray()
        val lengthSeconds = json.optLong("lengthSeconds")
        val adaptiveAudio = (0 until adaptiveFormats.length())
            .mapNotNull { adaptiveFormats.optJSONObject(it) }
            .filter { it.optString("type").substringBefore(';').startsWith("audio/") }
            .mapNotNull { invidiousDashTrack(inst, it) }
        val adaptiveVideo = (0 until adaptiveFormats.length())
            .mapNotNull { adaptiveFormats.optJSONObject(it) }
            .filter { it.optString("type").substringBefore(';').startsWith("video/") }
            .mapNotNull { f ->
                val videoTrack = invidiousDashTrack(inst, f) ?: return@mapNotNull null
                val type = videoTrack.mimeType
                val audio = adaptiveAudio
                    .filter { it.mimeType.substringAfter('/') == type.substringAfter('/') }
                    .ifEmpty { adaptiveAudio }
                    .maxByOrNull { it.bitrate }
                    ?: return@mapNotNull null
                val label = f.optString("qualityLabel")
                    .ifBlank { f.optString("resolution").ifBlank { f.optString("quality") } }
                // With byte ranges known, one local DASH manifest keeps video
                // and audio on one clock and lets ExoPlayer fetch ranged
                // chunks (googlevideo throttles or 403s open-ended requests
                // for adaptive files). Without them, the pair is still kept
                // together and merged by the player — never video-only.
                val manifest = buildDashManifest(videoTrack, audio, lengthSeconds)
                OnlineStream(
                    url = manifest ?: videoTrack.url,
                    qualityLabel = label,
                    mimeType = if (manifest != null) "application/dash+xml" else type,
                    audioUrl = if (manifest != null) null else audio.url,
                    height = heightOf(label).takeIf { it > 0 } ?: videoTrack.height,
                    isProgressive = false
                )
            }
        val playableStreams = (progressive + adaptiveVideo)
            .distinctBy { it.url to it.audioUrl }
            .sortedByDescending { it.height }
        val captions = parseInvidiousCaptions(inst, json.optJSONArray("captions"))
        val rel = json.optJSONArray("recommendedVideos") ?: JSONArray()
        val related = (0 until rel.length()).mapNotNull { rel.optJSONObject(it) }.map { invidiousVideoItem(inst, it) }
        return OnlineVideoDetails(
            video = video,
            description = json.optString("description"),
            progressiveStreams = playableStreams,
            // The manifests are served by the instance; local=true makes the
            // segment URLs inside them go through the instance as well.
            hlsUrl = json.optString("hlsUrl").takeIf { it.startsWith("http") }?.let { withLocalParam(it) },
            dashUrl = json.optString("dashUrl").takeIf { it.startsWith("http") }?.let { withLocalParam(it) },
            captions = captions,
            related = related,
            instance = inst
        )
    }

    internal fun withLocalParam(url: String): String = when {
        url.contains("local=") -> url
        url.contains('?') -> "$url&local=true"
        else -> "$url?local=true"
    }

    private fun invidiousVideoItem(inst: OnlineInstance, o: JSONObject): OnlineVideo = OnlineVideo(
        id = o.optString("videoId"),
        title = o.optString("title"),
        author = o.optString("author"),
        authorId = o.optString("authorId").takeIf { it.isNotBlank() },
        thumbnailUrl = bestThumb(inst, o.optJSONArray("videoThumbnails")),
        lengthSeconds = o.optLong("lengthSeconds"),
        viewCount = if (o.has("viewCount")) o.optLong("viewCount") else null,
        publishedText = o.optString("publishedText").takeIf { it.isNotBlank() },
        publishedAt = o.optLong("published"),
        isLive = o.optBoolean("liveNow")
    )

    private fun invidiousChannelVideos(inst: OnlineInstance, channelId: String, continuation: String?): OnlineVideoPage {
        val cont = continuation?.let { "?continuation=${enc(it)}" } ?: ""
        val json = getJson(inst, "/api/v1/channels/${enc(channelId)}/videos$cont")
        val arr = json.optJSONArray("videos") ?: JSONArray()
        val videos = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { invidiousVideoItem(inst, it) }
        return OnlineVideoPage(videos, json.optString("continuation").takeIf { it.isNotBlank() })
    }

    /**
     * First page of a channel from an Invidious instance: the JSON endpoint
     * when it is available, otherwise the instance's Atom feed. Most public
     * instances have `/api/v1` switched off ("Endpoint disabled") but keep
     * serving `/feed/channel/:id`.
     */
    private fun invidiousChannelVideosOrFeed(inst: OnlineInstance, channelId: String): OnlineVideoPage {
        val jsonError = try {
            val page = invidiousChannelVideos(inst, channelId, null)
            if (page.videos.isNotEmpty()) return page
            EmptyPageException()
        } catch (e: OnlineException) {
            e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            OnlineException(e.message ?: e.javaClass.simpleName)
        }
        try {
            return feedPage(inst.baseUrl + "/feed/channel/" + enc(channelId), channelId)
        } catch (e: OnlineException) {
            val message = "${jsonError.message?.take(70)}; feed: ${e.message?.take(70)}"
            // Keep a gateway-style 5xx visible to the outage bookkeeping.
            val code = (e as? HttpException)?.takeIf { !it.structured }?.code
                ?: (jsonError as? HttpException)?.takeIf { !it.structured }?.code
            throw if (code != null && code >= 500) HttpException(code, message) else OnlineException(message)
        }
    }

    private fun invidiousChannelInfo(inst: OnlineInstance, channelId: String): OnlineChannel {
        val json = getJson(inst, "/api/v1/channels/${enc(channelId)}")
        return OnlineChannel(
            id = json.optString("authorId", channelId),
            name = json.optString("author"),
            avatarUrl = bestThumb(inst, json.optJSONArray("authorThumbnails")),
            description = json.optString("description").takeIf { it.isNotBlank() }
        )
    }

    // ── Piped ──

    private fun pipedVideo(inst: OnlineInstance, videoId: String): OnlineVideoDetails {
        val json = getJson(inst, "/streams/${enc(videoId)}")
        val uploaderUrl = json.optString("uploaderUrl")
        val video = OnlineVideo(
            id = videoId,
            title = json.optString("title"),
            author = json.optString("uploader"),
            authorId = uploaderUrl.substringAfter("/channel/", "").takeIf { it.isNotBlank() },
            thumbnailUrl = absolutize(inst, json.optString("thumbnailUrl")),
            lengthSeconds = json.optLong("duration"),
            viewCount = if (json.has("views")) json.optLong("views") else null,
            publishedText = json.optString("uploadDate").takeIf { it.isNotBlank() },
            publishedAt = json.optLong("uploaded") / 1000L,
            isLive = json.optBoolean("livestream")
        )
        val audioStreams = json.optJSONArray("audioStreams") ?: JSONArray()
        val audioCandidates = (0 until audioStreams.length())
            .mapNotNull { audioStreams.optJSONObject(it) }
            .filter { streamUrl(inst, it.optString("url")) != null }
        val vs = json.optJSONArray("videoStreams") ?: JSONArray()
        val progressive = (0 until vs.length()).mapNotNull { vs.optJSONObject(it) }
            .mapNotNull { s ->
                val url = streamUrl(inst, s.optString("url")) ?: return@mapNotNull null
                val label = s.optString("quality")
                val mimeType = s.optString("mimeType").substringBefore(';').ifBlank { "video/mp4" }
                val videoOnly = s.optBoolean("videoOnly", true)
                var pairedAudioUrl: String? = null
                val adaptiveManifest = if (videoOnly) {
                    // Video-only: must be paired with an audio stream, never
                    // played on its own (no sound, or a decoder error).
                    val audio = audioCandidates
                        .filter {
                            it.optString("mimeType").substringBefore(';').substringAfter('/') ==
                                mimeType.substringAfter('/')
                        }
                        .ifEmpty { audioCandidates }
                        .maxByOrNull { it.optLong("bitrate") }
                        ?: return@mapNotNull null
                    val videoTrack = pipedDashTrack(inst, s) ?: return@mapNotNull null
                    val audioTrack = pipedDashTrack(inst, audio) ?: return@mapNotNull null
                    buildDashManifest(videoTrack, audioTrack, json.optLong("duration")).also {
                        if (it == null) pairedAudioUrl = audioTrack.url
                    }
                } else {
                    null
                }
                OnlineStream(
                    url = adaptiveManifest ?: url,
                    qualityLabel = label,
                    mimeType = if (adaptiveManifest != null) "application/dash+xml" else mimeType,
                    audioUrl = pairedAudioUrl,
                    height = s.optInt("height").takeIf { it > 0 } ?: heightOf(label),
                    isProgressive = !videoOnly
                )
            }
            .sortedByDescending { it.height }
        val captions = parsePipedCaptions(inst, json.optJSONArray("subtitles"))
        return OnlineVideoDetails(
            video = video,
            description = json.optString("description"),
            progressiveStreams = progressive,
            hlsUrl = json.optString("hls").takeIf { it.startsWith("http") },
            dashUrl = json.optString("dash").takeIf { it.startsWith("http") },
            captions = captions,
            related = pipedStreamItems(json.optJSONArray("relatedStreams")),
            instance = inst
        )
    }

    /** One adaptive (video-only or audio-only) file plus what a DASH manifest needs to describe it. */
    internal data class DashTrack(
        val url: String,
        val mimeType: String,
        val codec: String?,
        val bitrate: Long,
        val initRange: Pair<Long, Long>?,
        val indexRange: Pair<Long, Long>?,
        val width: Int = 0,
        val height: Int = 0,
        val fps: Int = 0
    )

    private fun pipedDashTrack(inst: OnlineInstance, stream: JSONObject): DashTrack? {
        val url = streamUrl(inst, stream.optString("url")) ?: return null
        fun range(startName: String, endName: String): Pair<Long, Long>? {
            if (!stream.has(startName) || !stream.has(endName)) return null
            val start = stream.optLong(startName, -1L)
            val end = stream.optLong(endName, -1L)
            return if (start >= 0 && end >= start) start to end else null
        }
        return DashTrack(
            url = url,
            mimeType = stream.optString("mimeType").substringBefore(';').ifBlank { "video/mp4" },
            codec = stream.optString("codec").takeIf { it.isNotBlank() },
            bitrate = stream.optLong("bitrate"),
            initRange = range("initStart", "initEnd"),
            indexRange = range("indexStart", "indexEnd"),
            width = stream.optInt("width"),
            height = stream.optInt("height"),
            fps = stream.optInt("fps")
        )
    }

    /** Invidious spells the ranges `"init": "0-740"`, `"index": "741-1200"` and puts the codec in `type`. */
    private fun invidiousDashTrack(inst: OnlineInstance, format: JSONObject): DashTrack? {
        val url = streamUrl(inst, format.optString("url")) ?: return null
        val type = format.optString("type")
        val size = format.optString("size")
        return DashTrack(
            url = url,
            mimeType = type.substringBefore(';').trim().ifBlank { "video/mp4" },
            codec = Regex("codecs=\"([^\"]+)\"").find(type)?.groupValues?.get(1),
            bitrate = format.optString("bitrate").toLongOrNull() ?: format.optLong("bitrate"),
            initRange = parseByteRange(format.optString("init")),
            indexRange = parseByteRange(format.optString("index")),
            width = size.substringBefore('x', "").toIntOrNull() ?: 0,
            height = size.substringAfter('x', "").toIntOrNull() ?: 0,
            fps = format.optInt("fps")
        )
    }

    /** "741-1200" → 741 to 1200; anything else → null. */
    internal fun parseByteRange(value: String?): Pair<Long, Long>? {
        val parts = value?.trim()?.split('-') ?: return null
        if (parts.size != 2) return null
        val start = parts[0].trim().toLongOrNull() ?: return null
        val end = parts[1].trim().toLongOrNull() ?: return null
        return if (start >= 0 && end >= start) start to end else null
    }

    /**
     * Builds the same kind of local DASH manifest LibreTube uses for
     * video-only formats: one video and one audio AdaptationSet on a single
     * timeline, each with its SegmentBase byte ranges. Returned as a `data:`
     * URI that ExoPlayer's DASH module opens directly. Null when a range or
     * the duration is missing — the caller then keeps the explicit
     * video+audio pair instead. Treating these URLs as ordinary progressive
     * files is why playback used to fail on many instances.
     */
    internal fun buildDashManifest(video: DashTrack, audio: DashTrack, durationSeconds: Long): String? {
        val videoInit = video.initRange ?: return null
        val videoIndex = video.indexRange ?: return null
        val audioInit = audio.initRange ?: return null
        val audioIndex = audio.indexRange ?: return null
        if (durationSeconds <= 0L) return null

        fun attrs(track: DashTrack): String = buildString {
            append(" bandwidth=\"").append(track.bitrate.coerceAtLeast(1L)).append('"')
            track.codec?.let { append(" codecs=\"").append(xmlEscape(it)).append('"') }
        }

        val manifest = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:full:2011" minBufferTime="PT1.5S" type="static" mediaPresentationDuration="PT${durationSeconds}S"><Period>""")
            append("""<AdaptationSet mimeType="${xmlEscape(video.mimeType)}" startWithSAP="1" subsegmentAlignment="true" scanType="progressive">""")
            append("""<Representation id="video"${attrs(video)} width="${video.width.coerceAtLeast(1)}" height="${video.height.coerceAtLeast(1)}" frameRate="${video.fps.coerceAtLeast(1)}">""")
            append("""<BaseURL>${xmlEscape(video.url)}</BaseURL><SegmentBase indexRange="${videoIndex.first}-${videoIndex.second}"><Initialization range="${videoInit.first}-${videoInit.second}"/></SegmentBase></Representation></AdaptationSet>""")
            append("""<AdaptationSet mimeType="${xmlEscape(audio.mimeType)}" startWithSAP="1" subsegmentAlignment="true">""")
            append("""<Representation id="audio"${attrs(audio)}><AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>""")
            append("""<BaseURL>${xmlEscape(audio.url)}</BaseURL><SegmentBase indexRange="${audioIndex.first}-${audioIndex.second}"><Initialization range="${audioInit.first}-${audioInit.second}"/></SegmentBase></Representation></AdaptationSet>""")
            append("</Period></MPD>")
        }
        val encoded = Base64.encodeToString(manifest.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "data:application/dash+xml;charset=utf-8;base64,$encoded"
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun pipedStreamItems(arr: JSONArray?): List<OnlineVideo> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("type", "stream") == "stream" }
            .mapNotNull { o ->
                val id = OnlineInstanceStore.extractVideoId(o.optString("url")) ?: return@mapNotNull null
                OnlineVideo(
                    id = id,
                    title = o.optString("title"),
                    author = o.optString("uploaderName"),
                    authorId = o.optString("uploaderUrl").substringAfter("/channel/", "").takeIf { it.isNotBlank() },
                    thumbnailUrl = o.optString("thumbnail").takeIf { it.startsWith("http") },
                    lengthSeconds = o.optLong("duration"),
                    viewCount = if (o.has("views")) o.optLong("views").takeIf { v -> v >= 0 } else null,
                    publishedText = o.optString("uploadedDate").takeIf { it.isNotBlank() },
                    publishedAt = o.optLong("uploaded") / 1000L,
                    isLive = o.optLong("duration") < 0
                )
            }
    }

    private fun pipedChannelVideos(inst: OnlineInstance, channelId: String, continuation: String?): OnlineVideoPage {
        val json = if (continuation == null) {
            getJson(inst, "/channel/${enc(channelId)}")
        } else {
            getJson(inst, "/nextpage/channel/${enc(channelId)}?nextpage=${enc(continuation)}")
        }
        return OnlineVideoPage(
            videos = pipedStreamItems(json.optJSONArray("relatedStreams")),
            continuation = json.optString("nextpage").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun pipedChannelInfo(inst: OnlineInstance, path: String): OnlineChannel {
        val json = getJson(inst, "/${path.trimStart('/')}")
        val id = json.optString("id")
        if (id.isBlank()) throw OnlineException("Channel not found")
        return OnlineChannel(
            id = id,
            name = json.optString("name"),
            avatarUrl = absolutize(inst, json.optString("avatarUrl")),
            description = json.optString("description").takeIf { it.isNotBlank() }
        )
    }

    // ── Atom feeds ──

    /** Parses a channel feed (youtube.com or an Invidious instance) into a page; feeds have no paging. */
    private fun feedPage(url: String, channelId: String): OnlineVideoPage {
        val feed = OnlineFeedParser.parse(getFeedXml(url), channelId)
        return OnlineVideoPage(feed.videos, continuation = null)
    }

    private fun getFeedXml(url: String): String {
        val text = getText(url).trim()
        if (text.isEmpty()) throw OnlineException("Empty response")
        if (!text.contains("<feed") && !text.contains("<entry") && !text.contains("<rss")) {
            throw OnlineException(describeNonJson(text))
        }
        return text
    }

    // ── HTTP / JSON plumbing ──

    private fun getText(url: String): String {
        // youtube.com (timedtext / InnerTube caption URLs) answers browsers
        // more reliably than an unknown client string.
        val userAgent = if (url.startsWith(InnerTubeClient.DIRECT_BASE_URL + "/")) InnerTubeClient.BROWSER_USER_AGENT else USER_AGENT
        val req = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "*/*").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val trimmed = body.trim()
                val structured = trimmed.startsWith("{") && jsonErrorMessage(trimmed) != null
                val detail = when {
                    trimmed.isEmpty() -> ""
                    structured -> jsonErrorMessage(trimmed)
                    trimmed.startsWith("{") -> trimmed.take(120)
                    looksLikeHtml(trimmed) -> htmlTitle(trimmed) ?: "web page"
                    else -> trimmed.take(120).replace('\n', ' ')
                }
                throw HttpException(resp.code, "HTTP ${resp.code}${if (!detail.isNullOrBlank()) ": $detail" else ""}", structured)
            }
            return body
        }
    }

    /**
     * GET + parse a JSON object. Anything that is not a usable object is an
     * [OnlineException] with a short, human-readable reason, so the failover
     * moves on and the UI can show what each instance said: an empty body,
     * an HTML page (Cloudflare / Anubis challenge, "Endpoint disabled"), or
     * Piped's / Invidious' own `{"error": …}` payload.
     */
    private fun getJson(inst: OnlineInstance, path: String): JSONObject {
        val text = getText(inst.baseUrl + path).trim()
        if (text.isEmpty()) throw OnlineException("Empty response")
        if (!text.startsWith("{")) throw OnlineException(describeNonJson(text))
        val obj = try { JSONObject(text) } catch (e: Exception) { throw OnlineException("Malformed JSON") }
        jsonErrorMessage(obj)?.let { throw OnlineException(it) }
        return obj
    }

    private fun getJsonArray(inst: OnlineInstance, path: String): JSONArray {
        val text = getText(inst.baseUrl + path).trim()
        if (text.isEmpty()) throw OnlineException("Empty response")
        if (text.startsWith("{")) {
            throw OnlineException(jsonErrorMessage(text) ?: "Unexpected object response")
        }
        if (!text.startsWith("[")) throw OnlineException(describeNonJson(text))
        return try { JSONArray(text) } catch (e: Exception) { throw OnlineException("Malformed JSON") }
    }

    /**
     * The error text inside an instance's JSON error payload, or null when
     * the object is not an error. Piped sends `{"error": "<stack trace>",
     * "message": "<text>"}`; Invidious sends `{"error": "<text>"}`.
     */
    internal fun jsonErrorMessage(obj: JSONObject): String? {
        val error = obj.optString("error").takeIf { it.isNotBlank() } ?: return null
        val message = obj.optString("message").takeIf { it.isNotBlank() }
            ?: error.lineSequence().first().substringAfterLast("Exception: ").trim()
        return message.take(160)
    }

    private fun jsonErrorMessage(text: String): String? =
        try { jsonErrorMessage(JSONObject(text)) } catch (e: Exception) { null }

    /** Short description of a non-JSON body: the page title for HTML, else the first few characters. */
    internal fun describeNonJson(text: String): String {
        val t = text.trim()
        if (looksLikeHtml(t)) {
            val title = htmlTitle(t)
            return if (title != null) "Instance returned a web page ($title)" else "Instance returned a web page instead of data"
        }
        return "Instance returned no JSON (${t.take(40).replace('\n', ' ')})"
    }

    private fun looksLikeHtml(t: String): Boolean =
        t.startsWith("<!DOCTYPE", ignoreCase = true) || t.startsWith("<html", ignoreCase = true) || t.contains("<body", ignoreCase = true)

    private fun htmlTitle(t: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(t)
            ?.groupValues?.get(1)?.let { OnlineCaptionParser.unescape(it).replace(Regex("\\s+"), " ").trim() }
            ?.takeIf { it.isNotBlank() }?.take(60)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Picks the largest thumbnail from an Invidious thumbnail array, made absolute. */
    private fun bestThumb(inst: OnlineInstance, arr: JSONArray?): String? {
        if (arr == null || arr.length() == 0) return null
        var best: JSONObject? = null
        var bestW = -1
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val w = o.optInt("width")
            // Prefer a medium-sized image: big enough for a list cell, small
            // enough not to burn data on every row.
            val score = if (w in 200..720) 1000 + w else w
            if (score > bestW) { bestW = score; best = o }
        }
        return absolutize(inst, best?.optString("url"))
    }

    internal fun absolutize(inst: OnlineInstance, url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        return when {
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") -> inst.baseUrl + u
            else -> inst.baseUrl + "/" + u
        }
    }

    /** Accept HTTP(S) URLs and instance-relative proxy paths, but not SABR or other custom schemes. */
    private fun streamUrl(inst: OnlineInstance, url: String?): String? {
        val value = url?.trim().orEmpty()
        if (!value.isStreamUrl()) return null
        return absolutize(inst, value)
    }

    internal fun heightOf(label: String): Int =
        Regex("(\\d{3,4})p").find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("\\d+x(\\d+)").find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

    /**
     * Mirrors the stream filtering used by [invidiousVideo] and [pipedVideo].
     * Merely receiving a non-empty array is not enough: adaptive video-only
     * tracks require a matching audio stream, and broken instances sometimes
     * return entries without URLs.
     */
    private fun JSONArray?.hasUsableStream(requireMuxed: Boolean = false): Boolean {
        val streams = this ?: return false
        return (0 until streams.length()).any { index ->
            val stream = streams.optJSONObject(index) ?: return@any false
            (!requireMuxed || !stream.optBoolean("videoOnly", true)) &&
                stream.optString("url").isStreamUrl()
        }
    }

    private fun JSONArray?.hasVideoOnlyStream(): Boolean {
        val streams = this ?: return false
        return (0 until streams.length()).any { index ->
            val stream = streams.optJSONObject(index) ?: return@any false
            stream.optBoolean("videoOnly", true) && stream.optString("url").isStreamUrl()
        }
    }

    private fun JSONArray?.hasMimeStream(prefix: String): Boolean {
        val streams = this ?: return false
        return (0 until streams.length()).any { index ->
            val stream = streams.optJSONObject(index) ?: return@any false
            stream.optString("type").substringBefore(';').startsWith(prefix) &&
                stream.optString("url").isStreamUrl()
        }
    }

    private fun String?.isHttpUrl(): Boolean =
        this?.startsWith("http://") == true || this?.startsWith("https://") == true

    private fun String?.isStreamUrl(): Boolean =
        isHttpUrl() || this?.startsWith("/") == true
}
