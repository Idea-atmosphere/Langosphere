package com.example.logic

import android.content.pm.ActivityInfo
import com.example.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «کپی کامل زیرنویس برای هوش مصنوعی»: the clipboard payload of the Online
 * tab, and the pure decision behind the «چرخش صفحه» button.
 */
class OnlineSubtitlePayloadTest {

    private val cues = listOf(
        SubtitleEntry(1.2, 3.4, "We're no strangers to love", "en"),
        SubtitleEntry(3.5, 5.0, "   ", "en"), // empty → skipped, ids renumbered
        SubtitleEntry(5.0, 7.25, "You know the rules\nand so do I", "en"),
        SubtitleEntry(3725.5, 3727.0, "A line after one hour", "en")
    )

    @Test
    fun `payload is prompt then an anchored SRT block`() {
        val text = OnlineSubtitlePayload.build(
            prompt = "PROMPT LINE",
            entries = cues,
            videoId = "dQw4w9WgXcQ",
            title = "Never | Gonna",
            sourceLanguage = "English",
            targetLanguage = "Persian",
            level = "b1"
        )
        val lines = text.lines()
        assertEquals("PROMPT LINE", lines[0])
        assertEquals("", lines[1])
        assertEquals(
            "@LANGO v3 | subtitles | cues 1-3 | video=dQw4w9WgXcQ | src=English | dst=Persian | B1",
            lines[2]
        )
        // Pipes inside the title must not break the header format.
        assertEquals("TITLE: Never / Gonna", lines[3])
        assertEquals("1", lines[4])
        assertEquals("00:00:01,200 --> 00:00:03,400", lines[5])
        assertEquals("We're no strangers to love", lines[6])
        assertEquals("", lines[7])
        assertEquals("2", lines[8])
        assertEquals("00:00:05,000 --> 00:00:07,250", lines[9])
        // Multi-line cue text collapses to one line.
        assertEquals("You know the rules and so do I", lines[10])
        assertTrue(text.contains("3\n01:02:05,500 --> 01:02:07,000\nA line after one hour\n@END"))
        assertTrue(text.endsWith(OnlineSubtitlePayload.END_MARKER))
        assertEquals(3, OnlineSubtitlePayload.cueCount(cues))
    }

    @Test
    fun `no usable cues means nothing to copy`() {
        assertEquals("", OnlineSubtitlePayload.build("PROMPT", emptyList(), "id", "", "English", "Persian", "B1"))
        assertEquals(
            "",
            OnlineSubtitlePayload.build("PROMPT", listOf(SubtitleEntry(0.0, 1.0, "  ")), "id", "", "English", "Persian", "B1")
        )
        assertEquals(0, OnlineSubtitlePayload.cueCount(emptyList()))
    }

    @Test
    fun `blank prompt leaves only the block and blank languages fall back`() {
        val text = OnlineSubtitlePayload.build("  ", listOf(SubtitleEntry(0.0, 1.0, "Hi")), "", "", "", "", "")
        assertTrue(text.startsWith("@LANGO v3 | subtitles | cues 1-1 | src=English | dst=Persian\n1\n"))
        assertFalse(text.contains("video="))
        assertFalse(text.contains("TITLE:"))
    }

    @Test
    fun `srt timestamps round and never go negative`() {
        assertEquals("00:00:00,000", OnlineSubtitlePayload.srtTime(-3.0))
        assertEquals("00:00:01,000", OnlineSubtitlePayload.srtTime(0.9996))
        assertEquals("10:00:00,000", OnlineSubtitlePayload.srtTime(36000.0))
    }

    @Test
    fun `rotation flips between portrait and landscape`() {
        val portrait = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val landscape = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Fullscreen asks for landscape → the button goes to portrait.
        assertEquals(portrait, OrientationToggle.next(landscape, configIsLandscape = true))
        assertEquals(portrait, OrientationToggle.next(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, false))
        // And back.
        assertEquals(landscape, OrientationToggle.next(portrait, configIsLandscape = false))
        assertEquals(landscape, OrientationToggle.next(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT, true))
        // Nothing requested: the real screen orientation decides.
        assertEquals(portrait, OrientationToggle.next(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, configIsLandscape = true))
        assertEquals(landscape, OrientationToggle.next(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, configIsLandscape = false))
        assertEquals(landscape, OrientationToggle.next(ActivityInfo.SCREEN_ORIENTATION_SENSOR, configIsLandscape = false))
    }
}
