package com.example.logic

import com.example.model.OnlineVideo
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Parses a channel's Atom feed into [OnlineVideo]s.
 *
 * YouTube publishes one for every channel at
 * `https://www.youtube.com/feeds/videos.xml?channel_id=UC…` (no key, no
 * login, ~15 newest uploads) and Invidious instances mirror it at
 * `/feed/channel/UC…` — often even when their JSON API is switched off.
 * Both use the same `yt:` / `media:` extensions:
 *
 * ```xml
 * <entry>
 *   <yt:videoId>ID</yt:videoId>
 *   <yt:channelId>UC…</yt:channelId>
 *   <title>…</title>
 *   <author><name>…</name></author>
 *   <published>2026-09-11T08:35:14+00:00</published>
 *   <media:group>
 *     <media:thumbnail url="https://i.ytimg.com/vi/ID/hqdefault.jpg"/>
 *     <media:community><media:statistics views="1234"/></media:community>
 *   </media:group>
 * </entry>
 * ```
 *
 * The feed carries no duration, so [OnlineVideo.lengthSeconds] is 0.
 * Deliberately regex-based and free of Android types so it runs in plain
 * JVM unit tests; the input is small and machine-generated.
 */
object OnlineFeedParser {

    data class Feed(
        val channelId: String?,
        val channelTitle: String?,
        val videos: List<OnlineVideo>
    )

    private val entryRegex = Regex("<entry\\b[^>]*>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val thumbRegex = Regex("<media:thumbnail\\b[^>]*?\\burl=\"([^\"]+)\"")
    private val viewsRegex = Regex("<media:statistics\\b[^>]*?\\bviews=\"(\\d+)\"")
    private val videoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")
    private val isoRegex = Regex("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.\\d+)?\\s*(Z|[+-]\\d{2}:?\\d{2})?")

    /**
     * @param fallbackChannelId used as the uploader id when an entry lacks `yt:channelId`.
     * @param fallbackAuthor used as the uploader name when an entry lacks `author/name`.
     */
    fun parse(xml: String, fallbackChannelId: String? = null, fallbackAuthor: String? = null): Feed {
        val firstEntry = xml.indexOf("<entry").let { if (it < 0) xml.length else it }
        val head = xml.substring(0, firstEntry)
        // YouTube's feed header spells the id without its UC prefix
        // (`<yt:channelId>HaHD477h-…</yt:channelId>`); the entries have it.
        val feedChannelId = fallbackChannelId
            ?: tag(head, "yt:channelId")?.takeIf { it.isNotBlank() }?.let { normalizeChannelId(it) }
        val feedTitle = tag(head, "title")?.takeIf { it.isNotBlank() }

        val videos = entryRegex.findAll(xml).mapNotNull { m ->
            val body = m.groupValues[1]
            val id = (tag(body, "yt:videoId") ?: idFromAtomId(tag(body, "id")))
                ?.trim()?.takeIf { videoIdRegex.matches(it) } ?: return@mapNotNull null
            val author = tag(tag(body, "author") ?: "", "name")?.takeIf { it.isNotBlank() }
                ?: fallbackAuthor ?: feedTitle ?: ""
            val published = tag(body, "published") ?: tag(body, "updated") ?: ""
            OnlineVideo(
                id = id,
                title = tag(body, "title") ?: tag(body, "media:title") ?: "",
                author = author,
                authorId = tag(body, "yt:channelId")?.takeIf { it.isNotBlank() }?.let { normalizeChannelId(it) } ?: feedChannelId,
                thumbnailUrl = thumbRegex.find(body)?.groupValues?.get(1)?.let { unescape(it) }
                    ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                lengthSeconds = 0L,
                viewCount = viewsRegex.find(body)?.groupValues?.get(1)?.toLongOrNull(),
                // Feeds carry no "3 days ago" text; the plain date is language-neutral.
                publishedText = published.trim().take(10).takeIf { it.length == 10 && it[4] == '-' },
                publishedAt = parseIsoSeconds(published)
            )
        }.toList().distinctBy { it.id }

        return Feed(feedChannelId, feedTitle, videos)
    }

    /** Text of the first `<name>` element (attributes allowed), entity-decoded; null when absent. */
    internal fun tag(xml: String, name: String): String? {
        val open = Regex("<" + Regex.escape(name) + "(?:\\s[^>]*)?>(.*?)</" + Regex.escape(name) + "\\s*>", RegexOption.DOT_MATCHES_ALL)
        val raw = open.find(xml)?.groupValues?.get(1) ?: return null
        return unescape(stripCdata(raw)).trim()
    }

    private fun normalizeChannelId(raw: String): String {
        val id = raw.trim()
        return if (id.startsWith("UC")) id else if (id.length == 22) "UC$id" else id
    }

    /** `yt:video:ID` (YouTube / Invidious Atom ids) → `ID`. */
    private fun idFromAtomId(atomId: String?): String? =
        atomId?.trim()?.substringAfterLast(':')?.takeIf { it.isNotBlank() }

    private fun stripCdata(s: String): String {
        val t = s.trim()
        return if (t.startsWith("<![CDATA[") && t.endsWith("]]>")) t.substring(9, t.length - 3) else t
    }

    internal fun unescape(s: String): String =
        OnlineCaptionParser.unescape(s)
            .replace(Regex("&#[xX]([0-9A-Fa-f]+);")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            }

    /**
     * ISO-8601 timestamp (`2026-09-11T08:35:14+00:00`, `…Z`, `…-07:00`) →
     * epoch seconds; 0 when unparseable. Done by hand because java.time is
     * not available on every supported Android version without desugaring.
     */
    fun parseIsoSeconds(text: String): Long {
        val m = isoRegex.find(text.trim()) ?: return 0L
        val g = m.groupValues
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        var epoch = cal.timeInMillis / 1000L
        val tz = g[7]
        if (tz.isNotEmpty() && tz != "Z") {
            val sign = if (tz[0] == '-') -1 else 1
            val digits = tz.substring(1).replace(":", "")
            val hours = digits.substring(0, 2).toIntOrNull() ?: 0
            val minutes = digits.substring(2).toIntOrNull() ?: 0
            // 08:35+02:00 is 06:35 UTC: subtract the offset.
            epoch -= sign * (hours * 3600L + minutes * 60L)
        }
        return epoch
    }
}
