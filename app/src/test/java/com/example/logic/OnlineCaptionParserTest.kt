package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Online tab's caption converter: WebVTT as served by Invidious
 * (including YouTube's rolling auto-generated tracks) and TTML / srv3 XML as
 * sometimes served by Piped.
 */
class OnlineCaptionParserTest {

    private val manualVtt = """
        WEBVTT
        Kind: captions
        Language: en

        00:00:01.000 --> 00:00:03.500
        Hello everyone, welcome back.

        00:00:03.600 --> 00:00:06.000 align:start position:0%
        Today we&#39;re learning <b>phrasal verbs</b>.

        NOTE this is a comment block

        01:02:03.250 --> 01:02:05.000
        Line with an hour.
    """.trimIndent()

    @Test
    fun `manual vtt cues are parsed with times and clean text`() {
        val entries = OnlineCaptionParser.parse(manualVtt, "en")
        assertEquals(3, entries.size)
        assertEquals(1.0, entries[0].start, 0.001)
        assertEquals(3.5, entries[0].end, 0.001)
        assertEquals("Hello everyone, welcome back.", entries[0].text)
        // cue settings are dropped, entities unescaped, tags removed
        assertEquals("Today we're learning phrasal verbs.", entries[1].text)
        assertEquals(3723.25, entries[2].start, 0.001)
        assertTrue(entries.all { it.language == "en" })
    }

    private val rollingVtt = "WEBVTT\nKind: captions\nLanguage: en\n\n" +
        "00:00:00.320 --> 00:00:02.870 align:start position:0%\n \nhello<00:00:00.800><c> everyone</c><00:00:01.040><c> and</c><00:00:01.280><c> welcome</c>\n\n" +
        "00:00:02.870 --> 00:00:02.880 align:start position:0%\nhello everyone and welcome\n \n\n" +
        "00:00:02.880 --> 00:00:05.200 align:start position:0%\nhello everyone and welcome\nto<00:00:03.100><c> this</c><00:00:03.400><c> lesson</c>\n\n" +
        "00:00:05.200 --> 00:00:05.210 align:start position:0%\nto this lesson\n \n\n" +
        "00:00:05.210 --> 00:00:07.000 align:start position:0%\nto this lesson\nabout<00:00:05.500><c> food</c>\n\n"

    @Test
    fun `rolling auto-generated captions collapse into distinct lines`() {
        val entries = OnlineCaptionParser.parse(rollingVtt, "en")
        assertEquals(listOf("hello everyone and welcome", "to this lesson", "about food"), entries.map { it.text })
        // The first cue absorbs its 10 ms bridge cue.
        assertEquals(0.32, entries[0].start, 0.001)
        assertEquals(2.88, entries[0].end, 0.001)
        assertEquals(2.88, entries[1].start, 0.001)
        // Nothing overlaps or runs backwards.
        for (i in 1 until entries.size) assertTrue(entries[i].start >= entries[i - 1].start)
    }

    @Test
    fun `whitespace-only payload lines do not end a cue`() {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n \nactual text\n\n"
        val entries = OnlineCaptionParser.parse(vtt, "en")
        assertEquals(1, entries.size)
        assertEquals("actual text", entries[0].text)
    }

    @Test
    fun `ttml paragraphs are converted`() {
        val ttml = """<?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
            <p begin="00:00:01.000" end="00:00:03.500">First line<br/>second part</p>
            <p begin="00:00:04.000" dur="2s">It&#39;s &quot;fine&quot;</p>
            <p begin="12.5s" end="14s"><span>Nested</span> span</p>
            </div></body></tt>"""
        assertTrue(OnlineCaptionParser.looksLikeXml(ttml))
        val entries = OnlineCaptionParser.parse(ttml, "en")
        assertEquals(3, entries.size)
        assertEquals("First line second part", entries[0].text)
        assertEquals(1.0, entries[0].start, 0.001)
        assertEquals(3.5, entries[0].end, 0.001)
        assertEquals("It's \"fine\"", entries[1].text)
        assertEquals(6.0, entries[1].end, 0.001)
        assertEquals(12.5, entries[2].start, 0.001)
        assertEquals("Nested span", entries[2].text)
    }

    @Test
    fun `youtube srv3 xml is converted`() {
        val srv = """<?xml version="1.0" encoding="utf-8" ?><timedtext format="3"><body>
            <p t="1200" d="2000">Hello there</p><p t="3300" d="1500">General <s>Kenobi</s></p></body></timedtext>"""
        val entries = OnlineCaptionParser.parse(srv, "en")
        assertEquals(2, entries.size)
        assertEquals(1.2, entries[0].start, 0.001)
        assertEquals(3.2, entries[0].end, 0.001)
        assertEquals("General Kenobi", entries[1].text)
    }

    @Test
    fun `ttml time formats`() {
        assertEquals(3723.25, OnlineCaptionParser.ttmlTime("01:02:03.250")!!, 0.001)
        assertEquals(12.5, OnlineCaptionParser.ttmlTime("12.5s")!!, 0.001)
        assertEquals(1.5, OnlineCaptionParser.ttmlTime("1500ms")!!, 0.001)
        assertEquals(90.0, OnlineCaptionParser.ttmlTime("01:30")!!, 0.001)
        assertEquals(null, OnlineCaptionParser.ttmlTime(""))
        assertEquals(null, OnlineCaptionParser.ttmlTime("abc"))
    }

    @Test
    fun `srt and plain text output`() {
        val entries = OnlineCaptionParser.parse(manualVtt, "en")
        val srt = OnlineCaptionParser.toSrt(entries)
        assertTrue(srt.startsWith("1\n00:00:01,000 --> 00:00:03,500\nHello everyone, welcome back.\n\n2\n"))
        assertTrue(srt.contains("01:02:03,250 --> 01:02:05,000"))
        // The SRT we write is readable by the app's own subtitle parser.
        val back = SubtitleParser.parseSubtitleContent(srt, "en")
        assertEquals(entries.map { it.text }, back.map { it.text })
        assertEquals("Hello everyone, welcome back.\nToday we're learning phrasal verbs.\nLine with an hour.", OnlineCaptionParser.toPlainText(entries))
    }

    @Test
    fun `vtt output is a valid webvtt file`() {
        val entries = OnlineCaptionParser.parse(manualVtt, "en")
        val vtt = OnlineCaptionParser.toVtt(entries)
        // Header first, dot decimals (not SRT commas), numbered cues.
        assertTrue(vtt.startsWith("WEBVTT\n\n1\n00:00:01.000 --> 00:00:03.500\nHello everyone, welcome back.\n\n2\n"))
        assertTrue(vtt.contains("01:02:03.250 --> 01:02:05.000"))
        assertFalse(vtt.contains(",000 -->"))
        // Round trip: the app's own parser reads back exactly the same lines.
        val back = SubtitleParser.parseSubtitleContent(vtt, "en")
        assertEquals(entries.map { it.text }, back.map { it.text })
        assertEquals(entries.map { it.start }, back.map { it.start })
        assertEquals(entries.map { it.end }, back.map { it.end })
    }

    @Test
    fun `vtt output of an empty list is just the header`() {
        assertEquals("WEBVTT\n\n", OnlineCaptionParser.toVtt(emptyList()))
    }

    @Test
    fun `empty and garbage input give no entries`() {
        assertTrue(OnlineCaptionParser.parse("", "en").isEmpty())
        assertTrue(OnlineCaptionParser.parse("   \n\n", "en").isEmpty())
        assertTrue(OnlineCaptionParser.parse("<html><body>404</body></html>", "en").isEmpty())
        assertFalse(OnlineCaptionParser.looksLikeXml("WEBVTT\n\n"))
    }
}
