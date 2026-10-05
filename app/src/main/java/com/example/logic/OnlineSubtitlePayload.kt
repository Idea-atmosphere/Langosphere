package com.example.logic

import com.example.model.SubtitleEntry
import java.util.Locale

/**
 * «کپی کامل زیرنویس برای هوش مصنوعی» — the text the Online tab puts on the
 * clipboard so a learner can paste ONE message into any AI chat and get back
 * a JSON learning package that imports straight into the online clip.
 *
 * Layout
 * ------
 * ```
 * <subtitle prompt from AiPromptTemplates (TRANSLATION_LEARNING)>
 *
 * @LANGO v3 | subtitles | cues 1-3 | video=dQw4w9WgXcQ | src=English | dst=Persian | B1
 * TITLE: Never Gonna Give You Up
 * 1
 * 00:00:01,200 --> 00:00:03,400
 * We're no strangers to love
 *
 * 2
 * ...
 * @END
 * ```
 *
 * The prompt is the same one Settings ▸ Prompts generates (it promises the
 * model "a subtitle file (SRT or VTT)" and describes the JSON contract with
 * `id` / `start` / `end` in seconds), so the cue block is plain SRT: every
 * model reads it, and the numbers it echoes back are the SRT indexes. The
 * `@LANGO` header / `@END` footer pin the block to one video and one language
 * pair and make it obvious where the file ends, the way the book payload
 * ([BookSlicePayload]) does for reader slices.
 *
 * Only the captions of the clip that is open go in here: the caller passes
 * the online caption list, never the Video tab's imported files.
 *
 * Pure Kotlin (no Android types) so it is unit tested on the JVM.
 */
object OnlineSubtitlePayload {

    const val VERSION = 3
    const val END_MARKER = "@END"

    /**
     * The header line. [firstId]..[lastId] are the SRT indexes in the block;
     * the video id makes a pasted answer traceable to its clip.
     */
    fun header(
        firstId: Int,
        lastId: Int,
        videoId: String,
        sourceLanguage: String,
        targetLanguage: String,
        level: String
    ): String = buildString {
        append("@LANGO v").append(VERSION)
        append(" | subtitles")
        append(" | cues ").append(firstId).append('-').append(lastId)
        if (videoId.isNotBlank()) append(" | video=").append(videoId.trim())
        append(" | src=").append(clean(sourceLanguage).ifBlank { "English" })
        append(" | dst=").append(clean(targetLanguage).ifBlank { "Persian" })
        val lvl = clean(level).uppercase(Locale.US)
        if (lvl.isNotBlank()) append(" | ").append(lvl)
    }

    /**
     * Only the anchored subtitle block (header, SRT cues, `@END`). Empty
     * cues are skipped and the survivors renumbered from 1, so the ids the
     * model returns always match what it was given. Returns "" when there is
     * nothing to send.
     */
    fun subtitleBlock(
        entries: List<SubtitleEntry>,
        videoId: String,
        title: String,
        sourceLanguage: String,
        targetLanguage: String,
        level: String
    ): String {
        val cues = usableCues(entries)
        if (cues.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append(header(1, cues.size, videoId, sourceLanguage, targetLanguage, level)).append('\n')
        val cleanTitle = clean(title)
        if (cleanTitle.isNotBlank()) sb.append("TITLE: ").append(cleanTitle).append('\n')
        cues.forEachIndexed { index, cue ->
            if (index > 0) sb.append('\n')
            sb.append(index + 1).append('\n')
            sb.append(srtTime(cue.start)).append(" --> ").append(srtTime(cue.end)).append('\n')
            sb.append(cue.text).append('\n')
        }
        sb.append(END_MARKER)
        return sb.toString()
    }

    /**
     * The whole clipboard text: [prompt] (may be blank), an empty line, then
     * [subtitleBlock]. Returns "" when there are no usable cues, so the caller
     * can tell the learner the captions are still loading instead of copying
     * a prompt with nothing to work on.
     */
    fun build(
        prompt: String,
        entries: List<SubtitleEntry>,
        videoId: String,
        title: String,
        sourceLanguage: String,
        targetLanguage: String,
        level: String
    ): String {
        val block = subtitleBlock(entries, videoId, title, sourceLanguage, targetLanguage, level)
        if (block.isEmpty()) return ""
        val p = prompt.trim()
        return if (p.isEmpty()) block else "$p\n\n$block"
    }

    /** Number of cues [build] would send (after dropping empty lines). */
    fun cueCount(entries: List<SubtitleEntry>): Int = usableCues(entries).size

    // ── helpers ──

    private data class Cue(val start: Double, val end: Double, val text: String)

    private fun usableCues(entries: List<SubtitleEntry>): List<Cue> =
        entries.mapNotNull { e ->
            val text = e.text.replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) null
            else {
                val start = e.start.coerceAtLeast(0.0)
                Cue(start, maxOf(e.end, start), text)
            }
        }

    /** One line, no pipes (they separate header fields). */
    private fun clean(value: String): String =
        value.replace(Regex("[\\r\\n]+"), " ").replace('|', '/').trim()

    internal fun srtTime(seconds: Double): String {
        val totalMs = Math.round(seconds.coerceAtLeast(0.0) * 1000.0)
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hour = totalSec / 3600
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hour, min, sec, ms)
    }
}
