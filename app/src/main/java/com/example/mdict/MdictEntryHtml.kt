package com.example.mdict

import java.net.URLDecoder

/**
 * Pure entry-HTML rewriting for the locked-down dictionary WebView: no
 * Android or Compose dependency, unit-tested on the JVM.
 *
 * Rewrites an entry's links so the WebView can route them:
 * - relative resource paths (png/css/mp3/spx/…) → `https://mdict.local/path`
 *   ([RESOURCE_BASE]), served from the MDD files through
 *   `shouldInterceptRequest`. The page itself is loaded with [BASE_URL] as
 *   its base, so resources are same-origin https: no mixed-content or
 *   custom-scheme restrictions (the same trick androidx's WebViewAssetLoader
 *   uses), and plain CSS `url(...)` references resolve to the same host.
 * - plain-word hrefs → `entry://word` (look another word up)
 * - `sound://`, `entry://`, `data:`, `#anchors` pass through
 * - other http/https URLs pass through and are blocked by the WebViewClient
 * - a standalone `<img>` is wrapped in an `image://` link for the
 *   full-screen viewer; an `<img>` already inside an `<a>` (e.g. a speaker
 *   icon inside a sound:// link) is NOT wrapped, because nested anchors are
 *   invalid HTML and the tap would go to the inner image link instead of the
 *   outer sound link, so the sound would never play.
 */
object MdictEntryHtml {

    const val BASE_HOST = "mdict.local"
    const val BASE_URL = "https://$BASE_HOST/"
    /** Prefix of every rewritten resource URL (same as [BASE_URL]). */
    const val RESOURCE_BASE = BASE_URL

    private val attrRegex = Regex("""(src|href)=(["'])([^"']*)\2""")
    private val anchorOrImg = Regex(
        """<a\b[^>]*>.*?</a>|<img\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val imgSrc = Regex("""src=(["'])([^"']*)\1""")

    /**
     * Rewrites the links of one entry fragment (no `<html>` wrapper is
     * added; the caller embeds the result in its own page).
     */
    fun rewriteEntryLinks(html: String, wrapImages: Boolean = true): String {
        val rewritten = attrRegex.replace(html) { match ->
            val attr = match.groupValues[1]
            val quote = match.groupValues[2]
            val value = match.groupValues[3]
            val lower = value.lowercase()
            val newValue = when {
                lower.startsWith("http://") || lower.startsWith("https://") ||
                    lower.startsWith("dict://") || lower.startsWith("entry://") ||
                    lower.startsWith("sound://") || lower.startsWith("data:") ||
                    lower.startsWith("#") || lower.startsWith("javascript:") ||
                    lower.startsWith("mailto:") || lower.startsWith("tel:") ||
                    lower.startsWith("about:") || lower.startsWith("file://") ||
                    lower.startsWith("content://") -> value
                value.startsWith("/") -> RESOURCE_BASE + value.trimStart('/')
                isResourcePath(value) -> RESOURCE_BASE + value
                attr == "href" && !value.contains(":") && !value.contains("#") ->
                    "entry://" + value
                else -> RESOURCE_BASE + value
            }
            "$attr=$quote$newValue$quote"
        }
        if (!wrapImages) return rewritten
        return anchorOrImg.replace(rewritten) { match ->
            val tag = match.value
            if (!tag.startsWith("<img", ignoreCase = true)) {
                return@replace tag // Whole <a>...</a> block: leave untouched.
            }
            val src = imgSrc.find(tag)?.groupValues?.get(2) ?: return@replace tag
            val path = resourcePathOf(src)
            if (path != null && !isAudioPath(path)) {
                "<a href=\"image://" + path + "\">" + tag + "</a>"
            } else {
                tag
            }
        }
    }

    /** File extensions that are resources (served from MDD), not entry links. */
    fun isResourcePath(value: String): Boolean {
        val path = value.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".css") || path.endsWith(".js") ||
            path.endsWith(".png") || path.endsWith(".jpg") ||
            path.endsWith(".jpeg") || path.endsWith(".gif") ||
            path.endsWith(".webp") || path.endsWith(".svg") ||
            path.endsWith(".bmp") || path.endsWith(".ico") ||
            path.endsWith(".mp3") || path.endsWith(".wav") ||
            path.endsWith(".ogg") || path.endsWith(".oga") ||
            path.endsWith(".flac") || path.endsWith(".m4a") ||
            path.endsWith(".aac") || path.endsWith(".opus") ||
            path.endsWith(".mid") || path.endsWith(".midi") ||
            path.endsWith(".amr") || path.endsWith(".spx") ||
            path.endsWith(".ttf") || path.endsWith(".otf") ||
            path.endsWith(".woff") || path.endsWith(".woff2")
    }

    /** Audio extensions that route to the sound player (spx via the Speex decoder, the rest via MediaPlayer). */
    fun isAudioPath(value: String): Boolean {
        val path = value.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".mp3") || path.endsWith(".wav") ||
            path.endsWith(".ogg") || path.endsWith(".oga") ||
            path.endsWith(".flac") || path.endsWith(".m4a") ||
            path.endsWith(".aac") || path.endsWith(".opus") ||
            path.endsWith(".mid") || path.endsWith(".midi") ||
            path.endsWith(".amr") || path.endsWith(".spx")
    }

    fun mimeFor(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".bmp") -> "image/bmp"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".ogg") || path.endsWith(".oga") -> "audio/ogg"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".opus") -> "audio/opus"
            path.endsWith(".mid") || path.endsWith(".midi") -> "audio/midi"
            path.endsWith(".amr") -> "audio/amr"
            path.endsWith(".spx") -> "audio/x-speex"
            path.endsWith(".ttf") -> "font/ttf"
            path.endsWith(".otf") -> "font/otf"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            else -> "application/octet-stream"
        }
    }

    /**
     * The MDD resource path a WebView request asks for, or null when the URL
     * is not a dictionary resource. Handles `https://mdict.local/path`
     * (rewritten links and CSS `url(...)` references resolved against
     * [BASE_URL]) plus the legacy `dict://path` form.
     */
    fun resourcePathOf(url: String): String? {
        val raw = when {
            url.startsWith("https://$BASE_HOST/") -> url.removePrefix("https://$BASE_HOST/")
            url.startsWith("http://$BASE_HOST/") -> url.removePrefix("http://$BASE_HOST/")
            url.startsWith("dict://") -> url.removePrefix("dict://")
            else -> return null
        }
        val path = decodeUrlPart(raw.substringBefore('?').substringBefore('#'))
        return path.ifEmpty { null }
    }

    fun decodeUrlPart(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            value
        }
    }
}
