package com.example.logic

import com.example.model.SubtitleEntry

/**
 * Turns the caption formats served by Invidious / Piped into the app's
 * [SubtitleEntry] list.
 *
 *  * WebVTT (`WEBVTT` header, `HH:MM:SS.mmm --> HH:MM:SS.mmm`) is what
 *    Invidious returns and what Piped returns for most tracks. The regular
 *    [SubtitleParser] handles the cue syntax; this class additionally
 *    removes VTT-only noise (cue settings after the timestamps, `<c>`
 *    styling and `<00:00:01.000>` karaoke timestamps in auto-generated
 *    tracks) and collapses the duplicated rolling lines YouTube's
 *    auto-captions produce.
 *  * TTML / YouTube "srv3" XML (`<p begin="..." end="...">` or
 *    `<p t="ms" d="ms">`) is what Piped hands out when YouTube gives it
 *    that; it is converted directly.
 *
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object OnlineCaptionParser {

    fun parse(raw: String, lang: String): List<SubtitleEntry> {
        val text = raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (text.isEmpty()) return emptyList()
        return when {
            looksLikeXml(text) -> parseTtml(text, lang)
            else -> parseVtt(text, lang)
        }
    }

    fun looksLikeXml(text: String): Boolean {
        val head = text.take(200).trimStart()
        return head.startsWith("<?xml") || head.startsWith("<tt") || head.startsWith("<timedtext") || head.startsWith("<transcript")
    }

    // ── WebVTT ──

    private val inlineTimestampRegex = Regex("<\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{3}>")
    private val tagRegex = Regex("</?[^>]+>")
    private val timingRegex = Regex("(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[.,]\\d{1,3})")

    /**
     * A small WebVTT/SRT block parser of its own (instead of the generic
     * [SubtitleParser]) because YouTube's auto-generated tracks contain
     * whitespace-only payload lines — a line holding a single space — which
     * must NOT end the cue (only a truly empty line does, per the WebVTT
     * spec), and because the karaoke `<00:00:01.000><c> word</c>` markup
     * tells us which line of a rolling cue is actually new.
     */
    fun parseVtt(text: String, lang: String): List<SubtitleEntry> {
        val blocks = text.replace("\r", "").split(Regex("\n\n+"))
        val rolling = inlineTimestampRegex.containsMatchIn(text)
        val entries = mutableListOf<SubtitleEntry>()
        for (block in blocks) {
            val lines = block.split('\n')
            val timingIdx = lines.indexOfFirst { timingRegex.containsMatchIn(it) }
            if (timingIdx < 0) continue // header, NOTE, STYLE, REGION …
            val timing = timingRegex.find(lines[timingIdx]) ?: continue
            val start = vttTime(timing.groupValues[1]) ?: continue
            val end = vttTime(timing.groupValues[2]) ?: continue
            val payload = lines.drop(timingIdx + 1).filter { it.isNotBlank() }
            if (payload.isEmpty()) continue
            // In a rolling (auto-generated) track only the karaoke line is new
            // content; the plain line above it repeats the previous cue.
            val chosen = if (rolling) {
                payload.filter { inlineTimestampRegex.containsMatchIn(it) }.ifEmpty { payload }
            } else payload
            val cleaned = chosen.joinToString(" ") { line ->
                unescape(line.replace(inlineTimestampRegex, "").replace(tagRegex, ""))
            }.replace(Regex("\\s+"), " ").trim()
            if (cleaned.isEmpty()) continue
            entries.add(SubtitleEntry(start, maxOf(end, start), cleaned, lang))
        }
        return collapseRollingCaptions(entries)
    }

    /** `HH:MM:SS.mmm` or `MM:SS.mmm` (comma accepted) → seconds. */
    fun vttTime(raw: String): Double? {
        val parts = raw.trim().replace(',', '.').split(':')
        return when (parts.size) {
            3 -> (parts[0].toDoubleOrNull() ?: return null) * 3600 + (parts[1].toDoubleOrNull() ?: return null) * 60 + (parts[2].toDoubleOrNull() ?: return null)
            2 -> (parts[0].toDoubleOrNull() ?: return null) * 60 + (parts[1].toDoubleOrNull() ?: return null)
            else -> null
        }
    }

    /**
     * YouTube auto-captions roll: each cue repeats the previous line and adds
     * the next one, sometimes with 10ms "bridge" cues in between. Drop cues
     * whose text is fully contained in the previous cue (extending its end
     * time) so the list reads as sentences instead of stutters.
     */
    fun collapseRollingCaptions(entries: List<SubtitleEntry>): List<SubtitleEntry> {
        if (entries.size < 2) return entries
        val out = ArrayList<SubtitleEntry>(entries.size)
        for (e in entries) {
            val prev = out.lastOrNull()
            if (prev != null) {
                val pt = prev.text.trim()
                val et = e.text.trim()
                when {
                    // Same text again → just extend.
                    et == pt -> { out[out.size - 1] = prev.copy(end = maxOf(prev.end, e.end)); continue }
                    // A very short bridge cue that only repeats the tail of the previous cue.
                    (e.end - e.start) < 0.2 && pt.endsWith(et) -> { out[out.size - 1] = prev.copy(end = maxOf(prev.end, e.end)); continue }
                    // The new cue starts with the whole previous text → previous cue was a partial.
                    et.startsWith(pt) && (e.start - prev.start) < 0.3 -> { out[out.size - 1] = e.copy(start = prev.start); continue }
                }
            }
            out.add(e)
        }
        return out
    }

    // ── TTML / srv3 ──

    private val pRegex = Regex("<p\\b([^>]*)>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val textRegex = Regex("<text\\b([^>]*)>(.*?)</text>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val attrRegex = Regex("([a-zA-Z:]+)\\s*=\\s*\"([^\"]*)\"")

    fun parseTtml(text: String, lang: String): List<SubtitleEntry> {
        val out = mutableListOf<SubtitleEntry>()
        val matches = pRegex.findAll(text).toList().ifEmpty { textRegex.findAll(text).toList() }
        for (m in matches) {
            val attrs = attrRegex.findAll(m.groupValues[1]).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val body = m.groupValues[2]
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
                .replace(tagRegex, "")
                .let { unescape(it) }
                .replace(Regex("\\s+"), " ")
                .trim()
            if (body.isEmpty()) continue
            val start: Double
            val end: Double
            when {
                attrs.containsKey("begin") -> {
                    start = ttmlTime(attrs["begin"]) ?: continue
                    end = ttmlTime(attrs["end"]) ?: (start + (ttmlTime(attrs["dur"]) ?: 4.0))
                }
                attrs.containsKey("t") -> {
                    start = (attrs["t"]?.toDoubleOrNull() ?: continue) / 1000.0
                    end = start + ((attrs["d"]?.toDoubleOrNull() ?: 4000.0) / 1000.0)
                }
                attrs.containsKey("start") -> {
                    start = attrs["start"]?.toDoubleOrNull() ?: continue
                    end = start + (attrs["dur"]?.toDoubleOrNull() ?: 4.0)
                }
                else -> continue
            }
            out.add(SubtitleEntry(start, maxOf(end, start + 0.2), body, lang))
        }
        return collapseRollingCaptions(out.sortedBy { it.start })
    }

    /** `HH:MM:SS.mmm`, `MM:SS.mmm`, `12.5s`, `1500ms`, `HH:MM:SS:FF` → seconds. */
    fun ttmlTime(raw: String?): Double? {
        val s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        if (s.endsWith("ms")) return s.dropLast(2).toDoubleOrNull()?.div(1000.0)
        if (s.endsWith("s")) return s.dropLast(1).toDoubleOrNull()
        if (s.endsWith("m")) return s.dropLast(1).toDoubleOrNull()?.times(60.0)
        if (s.endsWith("h")) return s.dropLast(1).toDoubleOrNull()?.times(3600.0)
        val parts = s.split(':')
        return when (parts.size) {
            3 -> (parts[0].toDoubleOrNull() ?: return null) * 3600 + (parts[1].toDoubleOrNull() ?: return null) * 60 + (parts[2].replace(',', '.').toDoubleOrNull() ?: return null)
            4 -> (parts[0].toDoubleOrNull() ?: return null) * 3600 + (parts[1].toDoubleOrNull() ?: return null) * 60 + (parts[2].toDoubleOrNull() ?: return null) + (parts[3].toDoubleOrNull() ?: 0.0) / 30.0
            2 -> (parts[0].toDoubleOrNull() ?: return null) * 60 + (parts[1].replace(',', '.').toDoubleOrNull() ?: return null)
            1 -> s.toDoubleOrNull()
            else -> null
        }
    }

    // ── Output helpers ──

    /** Serialises entries as SRT text (the format the rest of the app exports). */
    fun toSrt(entries: List<SubtitleEntry>): String {
        val sb = StringBuilder()
        entries.forEachIndexed { i, e ->
            sb.append(i + 1).append('\n')
            sb.append(srtTime(e.start)).append(" --> ").append(srtTime(e.end)).append('\n')
            sb.append(e.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    /**
     * Serialises entries as WebVTT text — the native format of the online
     * caption tracks, so the raw copy of an active online track is a valid
     * `.vtt` file rather than a re-tagged SRT.
     */
    fun toVtt(entries: List<SubtitleEntry>): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        entries.forEachIndexed { i, e ->
            sb.append(i + 1).append('\n')
            sb.append(vttTime(e.start)).append(" --> ").append(vttTime(e.end)).append('\n')
            sb.append(e.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    /** Plain transcript: one line per cue, no timestamps (nice for pasting into an AI chat). */
    fun toPlainText(entries: List<SubtitleEntry>): String =
        entries.joinToString("\n") { it.text.trim() }

    private fun srtTime(seconds: Double): String {
        val totalMs = (seconds.coerceAtLeast(0.0) * 1000).toLong()
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hour = totalSec / 3600
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hour, min, sec, ms)
    }

    private fun vttTime(seconds: Double): String {
        val totalMs = (seconds.coerceAtLeast(0.0) * 1000).toLong()
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hour = totalSec / 3600
        return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", hour, min, sec, ms)
    }

    fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
}
