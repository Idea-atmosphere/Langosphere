package com.example.logic

import com.example.model.OnlineCaptionTrack
import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import com.example.model.OnlineStream
import com.example.model.OnlineVideo
import com.example.model.OnlineVideoDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct YouTube extraction through the InnerTube `player` endpoint — the
 * same API YouTube's own apps, yt-dlp and NewPipe use — with no JavaScript,
 * no WebView and no third-party instance in between.
 *
 * What it gives the Online tab:
 *  * **Clean playback.** The direct googlevideo MP4 / DASH / HLS URLs go
 *    straight into Media3, so the picture has no YouTube title bar, channel
 *    avatar, watermark or end-screen cards — only the app's own chrome.
 *  * **Captions in app memory.** `captions.playerCaptionsTracklistRenderer
 *    .captionTracks[].baseUrl` is fetched as WebVTT and parsed into the
 *    transcript list, so the interactive transcript and «کپی کامل زیرنویس
 *    برای هوش مصنوعی» have real cues even when the clip ends up in the
 *    fallback embed.
 *
 * Client choice (September 2026)
 * ------------------------------
 * The classic `ANDROID` / `IOS` clients now require a Proof-of-Origin token
 * for every stream (HTTP 403 without it) and the `WEB` / `TV` clients need
 * the player JavaScript to decipher signatures. yt-dlp 2026.08.19 therefore
 * uses **VISIONOS** as its default JavaScript-less client — direct URLs, no
 * PO token — and so does this class. An older `ANDROID_VR` build is tried
 * second. The profiles live in [CLIENTS], so a future YouTube change is a
 * one-line update. Formats that would need JavaScript (a `signatureCipher`
 * instead of a URL, or an `n` throttling parameter) and caption URLs that
 * YouTube marks as PO-token-only (`exp=xpe` / `exp=xpv`) are skipped rather
 * than handed to the player to fail.
 *
 * Everything is plain HTTPS through OkHttp: no Google Play Services, no
 * proprietary library, nothing that breaks the F-Droid build.
 */
object InnerTubeClient {

    /** Pseudo "instance" that marks details resolved directly from YouTube. */
    const val DIRECT_BASE_URL = "https://www.youtube.com"
    val DIRECT_SOURCE = OnlineInstance(DIRECT_BASE_URL, OnlineInstanceKind.INVIDIOUS)

    fun isDirect(instance: OnlineInstance?): Boolean = instance?.baseUrl == DIRECT_BASE_URL

    private const val API_BASE = "https://www.youtube.com/youtubei/v1"

    /** Browser identity for plain youtube.com GETs (timedtext captions). */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    /** Highest adaptive resolution offered (keeps decoding smooth on phones). */
    private const val MAX_ADAPTIVE_HEIGHT = 1080

    internal data class ClientProfile(
        val key: String,
        val clientName: String,
        val clientNameId: Int,
        val clientVersion: String,
        val userAgent: String,
        val extra: Map<String, Any> = emptyMap()
    )

    /** Tried in order; mirrors yt-dlp's current JavaScript-less clients. */
    internal val CLIENTS = listOf(
        ClientProfile(
            key = "visionos",
            clientName = "VISIONOS",
            clientNameId = 101,
            clientVersion = "1.02",
            userAgent = BROWSER_USER_AGENT,
            extra = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS",
                "osVersion" to "26.5.23O471"
            )
        ),
        ClientProfile(
            key = "android_vr",
            clientName = "ANDROID_VR",
            clientNameId = 28,
            clientVersion = "1.60.19",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extra = mapOf(
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12L"
            )
        )
    )

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{6,20}$")

    /** What one player response offered. [details] is null when it had no playable URL. */
    data class Parsed(
        val video: OnlineVideo,
        val details: OnlineVideoDetails?,
        val captions: List<OnlineCaptionTrack>
    )

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // visitorData identifies an anonymous session; requests without it are
    // more likely to get the "confirm you're not a bot" answer.
    @Volatile private var visitorData: String? = null
    @Volatile private var visitorDataAt = 0L
    private const val VISITOR_DATA_TTL_MS = 30 * 60 * 1000L

    // ── Public API ──

    /**
     * Stream + caption details straight from YouTube. Throws when no client
     * returned a playable, JavaScript-free URL (the caller then asks the
     * Invidious / Piped instances).
     */
    suspend fun videoDetails(videoId: String): OnlineVideoDetails =
        player(videoId, requireStreams = true).details
            ?: throw OnlineVideoRepository.OnlineException("YouTube returned no direct stream")

    /**
     * Caption tracks of [videoId] (empty when the clip has none). Works for
     * clips whose streams cannot be used directly, too.
     */
    suspend fun captionTracks(videoId: String): List<OnlineCaptionTrack> =
        player(videoId, requireStreams = false).captions

    private suspend fun player(videoId: String, requireStreams: Boolean): Parsed = withContext(Dispatchers.IO) {
        if (!VIDEO_ID.matches(videoId)) throw OnlineVideoRepository.OnlineException("Invalid video id")
        val failures = mutableListOf<String>()
        var okWithoutCaptions: Parsed? = null
        val visitor = currentVisitorData()
        for (profile in CLIENTS) {
            try {
                val parsed = parsePlayerResponse(postPlayer(profile, videoId, visitor), videoId)
                if (requireStreams) {
                    if (parsed.details != null) return@withContext parsed
                    failures.add("${profile.key}: no JavaScript-free stream")
                } else {
                    if (parsed.captions.isNotEmpty()) return@withContext parsed
                    if (okWithoutCaptions == null) okWithoutCaptions = parsed
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.add("${profile.key}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        okWithoutCaptions ?: throw OnlineVideoRepository.OnlineException(
            "YouTube (direct): " + failures.joinToString("; ").take(300)
        )
    }

    // ── HTTP ──

    private fun postPlayer(profile: ClientProfile, videoId: String, visitor: String?): JSONObject {
        val body = buildPlayerBody(profile, videoId, visitor).toString()
        val text = post("$API_BASE/player?prettyPrint=false", profile, visitor, body)
        if (!text.trimStart().startsWith("{")) throw OnlineVideoRepository.OnlineException("Unexpected player response")
        return JSONObject(text)
    }

    private fun currentVisitorData(): String? {
        val cached = visitorData
        if (cached != null && System.currentTimeMillis() - visitorDataAt < VISITOR_DATA_TTL_MS) return cached
        return try {
            val profile = CLIENTS.first()
            val body = JSONObject().put("context", buildContext(profile, null)).toString()
            val text = post("$API_BASE/visitor_id?prettyPrint=false", profile, null, body)
            JSONObject(text).optJSONObject("responseContext")?.optString("visitorData")
                ?.takeIf { it.isNotBlank() }
                ?.also { visitorData = it; visitorDataAt = System.currentTimeMillis() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Not fatal: the player request is still sent without it.
        }
    }

    private fun post(url: String, profile: ClientProfile, visitor: String?, json: String): String {
        val builder = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", profile.userAgent)
            .header("X-YouTube-Client-Name", profile.clientNameId.toString())
            .header("X-YouTube-Client-Version", profile.clientVersion)
            .header("Origin", "https://www.youtube.com")
            .header("Accept-Language", "en-US,en;q=0.9")
        if (!visitor.isNullOrBlank()) builder.header("X-Goog-Visitor-Id", visitor)
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw OnlineVideoRepository.HttpException(resp.code, "HTTP ${resp.code}")
            return text
        }
    }

    // ── Request / response (pure, unit tested) ──

    internal fun buildContext(profile: ClientProfile, visitor: String?): JSONObject {
        val client = JSONObject()
            .put("clientName", profile.clientName)
            .put("clientVersion", profile.clientVersion)
            .put("hl", "en")
            .put("gl", "US")
            .put("userAgent", profile.userAgent)
        profile.extra.forEach { (k, v) -> client.put(k, v) }
        if (!visitor.isNullOrBlank()) client.put("visitorData", visitor)
        return JSONObject().put("client", client)
    }

    internal fun buildPlayerBody(profile: ClientProfile, videoId: String, visitor: String?): JSONObject =
        JSONObject()
            .put("context", buildContext(profile, visitor))
            .put("videoId", videoId)
            .put(
                "playbackContext",
                JSONObject().put("contentPlaybackContext", JSONObject().put("html5Preference", "HTML5_PREF_WANTS"))
            )
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)

    /** Throws when YouTube refused the clip (`playabilityStatus.status != OK`). */
    internal fun parsePlayerResponse(json: JSONObject, videoId: String): Parsed {
        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (status != "OK") {
            val reason = playability?.optString("reason")?.takeIf { it.isNotBlank() }
                ?: playability?.optJSONObject("errorScreen")?.toString()?.take(80)
                ?: "no playability status"
            throw OnlineVideoRepository.OnlineException("${status.ifBlank { "ERROR" }}: $reason")
        }
        val vd = json.optJSONObject("videoDetails") ?: JSONObject()
        val lengthSeconds = vd.optString("lengthSeconds").toLongOrNull() ?: 0L
        val thumbs = vd.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val video = OnlineVideo(
            id = vd.optString("videoId").ifBlank { videoId },
            title = vd.optString("title"),
            author = vd.optString("author"),
            authorId = vd.optString("channelId").takeIf { it.isNotBlank() },
            thumbnailUrl = thumbs?.let { arr -> arr.optJSONObject(arr.length() - 1)?.optString("url") }?.takeIf { it.startsWith("http") },
            lengthSeconds = lengthSeconds,
            viewCount = vd.optString("viewCount").toLongOrNull(),
            isLive = vd.optBoolean("isLive") || vd.optBoolean("isLiveContent") && lengthSeconds == 0L
        )
        val captions = parseCaptionTracks(json)

        val sd = json.optJSONObject("streamingData")
        val muxed = objects(sd?.optJSONArray("formats")).mapNotNull { f ->
            val url = usableUrl(f) ?: return@mapNotNull null
            val label = f.optString("qualityLabel").ifBlank { f.optString("quality") }
            OnlineStream(
                url = url,
                qualityLabel = label,
                mimeType = f.optString("mimeType").substringBefore(';').trim().ifBlank { "video/mp4" },
                height = f.optInt("height").takeIf { it > 0 } ?: OnlineVideoRepository.heightOf(label),
                isProgressive = true
            )
        }
        val adaptive = objects(sd?.optJSONArray("adaptiveFormats"))
        val audio = adaptive
            .filter { it.optString("mimeType").startsWith("audio/") && isDefaultAudio(it) }
            .mapNotNull { f -> usableUrl(f)?.let { dashTrack(f, it) to f.optBoolean("isDrc") } }
            .let { list -> list.filter { !it.second }.ifEmpty { list } }
            .map { it.first }
        val videoTracks = adaptive
            .filter { it.optString("mimeType").startsWith("video/") }
            .mapNotNull { f -> usableUrl(f)?.let { url -> dashTrack(f, url) to f } }
            .filter { (t, _) -> t.height in 1..MAX_ADAPTIVE_HEIGHT }
            // One rendition per height and container (the highest bitrate).
            .groupBy { (t, _) -> t.height to t.mimeType }
            .map { (_, group) -> group.maxByOrNull { it.first.bitrate }!! }
            // MP4/H.264 before WebM/VP9 (hardware decoding everywhere); the
            // height sort below is stable, so this order survives it.
            .sortedBy { (t, _) -> if (t.mimeType.contains("webm")) 1 else 0 }
        val adaptiveStreams = if (audio.isEmpty()) emptyList() else videoTracks.map { (vt, f) ->
            val at = audio.filter { it.mimeType.substringAfter('/') == vt.mimeType.substringAfter('/') }
                .ifEmpty { audio }
                .maxByOrNull { it.bitrate }!!
            val label = f.optString("qualityLabel").ifBlank { "${vt.height}p" }
            val manifest = OnlineVideoRepository.buildDashManifest(vt, at, lengthSeconds)
            OnlineStream(
                url = manifest ?: vt.url,
                qualityLabel = label,
                mimeType = if (manifest != null) "application/dash+xml" else vt.mimeType,
                audioUrl = if (manifest != null) null else at.url,
                height = vt.height,
                isProgressive = false
            )
        }
        val streams = (muxed + adaptiveStreams)
            .distinctBy { it.url to it.audioUrl }
            // Highest first (stable: MP4 stays ahead of WebM at the same height).
            .sortedByDescending { it.height }
        val hls = sd?.optString("hlsManifestUrl")?.takeIf { it.startsWith("https://") }
        val details = if (streams.isEmpty() && hls == null) null else OnlineVideoDetails(
            video = video,
            description = vd.optString("shortDescription"),
            progressiveStreams = streams,
            hlsUrl = hls,
            captions = captions,
            instance = DIRECT_SOURCE
        )
        return Parsed(video, details, captions)
    }

    /** `captions.playerCaptionsTracklistRenderer.captionTracks[]` → VTT caption tracks. */
    internal fun parseCaptionTracks(json: JSONObject): List<OnlineCaptionTrack> {
        val arr = json.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")
        return objects(arr).mapNotNull { t ->
            val raw = t.optString("baseUrl").trim()
            if (raw.isEmpty()) return@mapNotNull null
            val base = when {
                raw.startsWith("https://") -> raw
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("/") -> DIRECT_BASE_URL + raw
                else -> return@mapNotNull null
            }
            if (captionNeedsPoToken(base)) return@mapNotNull null
            val name = t.optJSONObject("name")?.let { n ->
                n.optString("simpleText").ifBlank { n.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty() }
            }.orEmpty()
            val lang = t.optString("languageCode")
            OnlineCaptionTrack(
                label = name.ifBlank { lang },
                languageCode = lang,
                url = vttCaptionUrl(base),
                autoGenerated = t.optString("kind") == "asr",
                mimeType = "text/vtt"
            )
        }
    }

    /** YouTube marks caption URLs that only work with a PO token with `exp=xpe` / `exp=xpv`. */
    internal fun captionNeedsPoToken(url: String): Boolean =
        queryParams(url).any { (k, v) -> k == "exp" && (v == "xpe" || v == "xpv") }

    /** The caption URL asking for WebVTT (and without `xosf`, which adds position noise). */
    internal fun vttCaptionUrl(baseUrl: String): String {
        val path = baseUrl.substringBefore('?')
        val kept = baseUrl.substringAfter('?', "").split('&')
            .filter { it.isNotEmpty() && !it.startsWith("fmt=") && !it.startsWith("xosf=") }
        return path + "?" + (kept + "fmt=vtt").joinToString("&")
    }

    // ── helpers ──

    private fun objects(arr: JSONArray?): List<JSONObject> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

    private fun queryParams(url: String): List<Pair<String, String>> =
        url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.map {
            it.substringBefore('=') to it.substringAfter('=', "")
        }

    /**
     * The direct URL of a format, or null when it cannot be played without
     * YouTube's JavaScript (signatureCipher, `n` challenge) or is DRM'd.
     */
    internal fun usableUrl(format: JSONObject): String? {
        val url = format.optString("url")
        if (!url.startsWith("https://")) return null
        if (format.has("drmFamilies")) return null
        if (queryParams(url).any { it.first == "n" }) return null
        return url
    }

    /** Multi-language uploads: keep the original/default audio track only. */
    private fun isDefaultAudio(format: JSONObject): Boolean {
        val track = format.optJSONObject("audioTrack") ?: return true
        return !track.has("audioIsDefault") || track.optBoolean("audioIsDefault")
    }

    private fun dashTrack(f: JSONObject, url: String): OnlineVideoRepository.DashTrack {
        val mime = f.optString("mimeType")
        fun range(name: String): Pair<Long, Long>? {
            val o = f.optJSONObject(name) ?: return null
            val start = o.optString("start").toLongOrNull() ?: return null
            val end = o.optString("end").toLongOrNull() ?: return null
            return if (start >= 0 && end >= start) start to end else null
        }
        return OnlineVideoRepository.DashTrack(
            url = url,
            mimeType = mime.substringBefore(';').trim().ifBlank { "video/mp4" },
            codec = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1),
            bitrate = f.optLong("bitrate"),
            initRange = range("initRange"),
            indexRange = range("indexRange"),
            width = f.optInt("width"),
            height = f.optInt("height"),
            fps = f.optInt("fps")
        )
    }
}
