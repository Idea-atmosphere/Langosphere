package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the "AI answer" side of the subtitle JSON importer: the `CHUNK 1-50`
 * marker the app's own prompt templates ask the model to write, the markdown
 * fences and commentary models add around their JSON, and the merging of
 * several chunks of one film into a single package.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleJsonParserChunkTest {

    /** A minimal but complete package covering cue ids [idFrom]..[idTo]. */
    private fun chunkJson(idFrom: Int, idTo: Int): String = buildString {
        append("{\n")
        append("  \"formatVersion\": 1,\n")
        append("  \"metadata\": { \"language\": \"English\", \"targetLanguage\": \"Persian\", \"level\": \"B1\" },\n")
        append("  \"subtitles\": [\n")
        for (id in idFrom..idTo) {
            if (id > idFrom) append(",\n")
            append("    {")
            append("\"id\": $id, \"start\": ${id * 2}.0, \"end\": ${id * 2 + 1}.5, ")
            append("\"english\": \"Line number $id.\", \"translation\": \"خط شماره $id.\", ")
            append("\"words\": [{ \"word\": \"word$id\", \"translation\": \"واژه$id\" }]")
            append("}")
        }
        append("\n  ]\n}")
    }

    // ── detection ──

    @Test
    fun `a chunk marker before the json is detected`() {
        val marker = SubtitleJsonParser.detectChunkMarker("CHUNK 1-50\n" + chunkJson(1, 2))
        assertNotNull(marker)
        assertEquals(1, marker!!.start)
        assertEquals(50, marker.end)
        assertEquals("CHUNK 1-50", marker.shortLabel)
    }

    @Test
    fun `chunk markers of any range and spelling are detected`() {
        assertEquals(51, SubtitleJsonParser.detectChunkMarker("CHUNK 51-100\n{}")!!.start)
        assertEquals(100, SubtitleJsonParser.detectChunkMarker("chunk: 51-100\n{}")!!.end)
        assertEquals(3, SubtitleJsonParser.detectChunkMarker("Chunk 3\n{}")!!.part)
        assertEquals("CHUNK 3", SubtitleJsonParser.detectChunkMarker("Chunk 3\n{}")!!.shortLabel)
        // Persian marker with Persian digits.
        val persian = SubtitleJsonParser.detectChunkMarker("بخش ۵۱-۱۰۰\n{}")
        assertNotNull(persian)
        assertEquals(51, persian!!.start)
        assertEquals(100, persian.end)
    }

    @Test
    fun `text without a marker has no chunk`() {
        assertNull(SubtitleJsonParser.detectChunkMarker(chunkJson(1, 2)))
        assertNull(SubtitleJsonParser.detectChunkMarker(""))
        assertFalse(SubtitleJsonParser.hasChunkMarker("just some prose"))
    }

    @Test
    fun `a chunked ai answer is recognised as subtitle json`() {
        val answer = "CHUNK 1-50\n```json\n" + chunkJson(1, 2) + "\n```\nSay continue for the next chunk."
        assertTrue(SubtitleJsonParser.looksLikeSubtitleJson(answer))
        assertTrue(SubtitleJsonParser.isValidSubtitleJson(answer))
    }

    // ── import: the marker and the prose must not break parsing ──

    @Test
    fun `parsing an ai answer strips the marker fences and commentary`() {
        val answer = """
            Here are the first cues.
            CHUNK 1-2
            ```json
            ${chunkJson(1, 2)}
            ```
            Say "continue" and I will send CHUNK 3-4.
        """.trimIndent()

        val pkg = SubtitleJsonParser.parse(answer)
        assertEquals(2, pkg.subtitles.size)
        assertEquals("Line number 1.", pkg.subtitles[0].english)
        assertEquals("خط شماره 2.", pkg.subtitles[1].translation)
        assertEquals(1, pkg.chunks.size)
        assertEquals(1, pkg.chunks[0].start)
        assertEquals(2, pkg.chunks[0].end)
        // The chunk remembers which lines it contributed, so it can be removed again.
        assertEquals(2, pkg.chunks[0].subtitleKeys.size)
    }

    @Test
    fun `a marker written after the json is detected too`() {
        val answer = chunkJson(7, 8) + "\nCHUNK 7-8 done."
        val pkg = SubtitleJsonParser.parse(answer)
        assertEquals(2, pkg.subtitles.size)
        assertEquals(7, pkg.chunks[0].start)
        assertEquals(8, pkg.chunks[0].end)
    }

    @Test
    fun `several chunks in one paste are merged in cue order`() {
        val answer = "CHUNK 1-2\n" + chunkJson(1, 2) + "\nCHUNK 3-4\n" + chunkJson(3, 4)
        val pkg = SubtitleJsonParser.parse(answer)
        assertEquals(4, pkg.subtitles.size)
        assertEquals(listOf("1", "2", "3", "4"), pkg.subtitles.map { it.id })
        assertEquals(2, pkg.chunks.size)
        assertTrue(pkg.isMerged)
    }

    @Test
    fun `a truncated answer after a marker still reports a syntax error`() {
        val answer = "CHUNK 1-50\n{ \"subtitles\": [ { \"id\": 1, \"english\": \"cut off"
        try {
            SubtitleJsonParser.parse(answer)
            throw AssertionError("expected SubtitleJsonParseException")
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            assertTrue(e.message!!.contains("syntax", ignoreCase = true))
        }
    }

    // ── merging ──

    @Test
    fun `merging two chunks joins them in cue order even when imported backwards`() {
        val first = SubtitleJsonParser.parse("CHUNK 1-2\n" + chunkJson(1, 2))
        val second = SubtitleJsonParser.parse("CHUNK 3-4\n" + chunkJson(3, 4))

        val forward = SubtitleJsonParser.mergePackages(first, second)
        assertEquals(listOf("1", "2", "3", "4"), forward.subtitles.map { it.id })

        val backward = SubtitleJsonParser.mergePackages(second, first)
        assertEquals(listOf("1", "2", "3", "4"), backward.subtitles.map { it.id })
        assertEquals(2, backward.chunks.size)
        assertEquals(1, backward.chunks[0].start)
        assertEquals(3, backward.chunks[1].start)
    }

    @Test
    fun `re-importing an overlapping chunk replaces those cues instead of duplicating them`() {
        val original = SubtitleJsonParser.parse("CHUNK 1-2\n" + chunkJson(1, 2))
        val corrected = SubtitleJsonParser.parse(
            "CHUNK 1-2\n" + chunkJson(1, 2).replace("Line number 2.", "Line number two, corrected.")
        )
        val merged = SubtitleJsonParser.mergePackages(original, corrected)

        assertEquals(2, merged.subtitles.size)
        assertEquals("Line number two, corrected.", merged.subtitles[1].english)
        // The same chunk is not listed twice.
        assertEquals(1, merged.chunks.size)
    }

    @Test
    fun `merged metadata keeps the first non-blank value`() {
        val withMeta = SubtitleJsonParser.parse("CHUNK 1-2\n" + chunkJson(1, 2))
        val withoutMeta = SubtitleJsonParser.parse(
            "CHUNK 3-4\n" + chunkJson(3, 4).replace(
                "\"metadata\": { \"language\": \"English\", \"targetLanguage\": \"Persian\", \"level\": \"B1\" },\n  ",
                ""
            )
        )
        val merged = SubtitleJsonParser.mergePackages(withoutMeta, withMeta)
        assertEquals("English", merged.metadata?.language)
        assertEquals("Persian", merged.metadata?.targetLanguage)
    }

    // ── undoing one chunk ──

    @Test
    fun `removing a chunk drops only its own lines`() {
        val merged = SubtitleJsonParser.parse(
            "CHUNK 1-2\n" + chunkJson(1, 2) + "\nCHUNK 3-4\n" + chunkJson(3, 4)
        )
        val firstChunk = merged.chunks.first { it.start == 1 }

        val remaining = SubtitleJsonParser.removeChunk(merged, firstChunk)
        assertNotNull(remaining)
        assertEquals(listOf("3", "4"), remaining!!.subtitles.map { it.id })
        assertEquals(1, remaining.chunks.size)
        assertEquals(3, remaining.chunks[0].start)
    }

    @Test
    fun `removing the only chunk clears the package`() {
        val pkg = SubtitleJsonParser.parse("CHUNK 1-2\n" + chunkJson(1, 2))
        assertNull(SubtitleJsonParser.removeChunk(pkg, pkg.chunks.first()))
    }

    // ── persistence ──

    @Test
    fun `serialize keeps the chunk bookkeeping and round-trips`() {
        val merged = SubtitleJsonParser.parse(
            "CHUNK 1-2\n" + chunkJson(1, 2) + "\nCHUNK 3-4\n" + chunkJson(3, 4)
        )
        val saved = SubtitleJsonParser.serialize(merged)
        assertTrue(saved.contains("\"chunks\""))

        val restored = SubtitleJsonParser.parse(saved)
        assertEquals(merged.subtitles.size, restored.subtitles.size)
        assertEquals(merged.chunks.size, restored.chunks.size)
        assertEquals(merged.chunks.map { it.shortLabel }, restored.chunks.map { it.shortLabel })
        assertEquals(
            merged.chunks.map { it.subtitleKeys.size },
            restored.chunks.map { it.subtitleKeys.size }
        )
        // The restored package can still lose one chunk.
        val remaining = SubtitleJsonParser.removeChunk(restored, restored.chunks.last())
        assertNotNull(remaining)
        assertEquals(2, remaining!!.subtitles.size)
    }

    @Test
    fun `a package without chunks stays chunk-free`() {
        val pkg = SubtitleJsonParser.parse(chunkJson(1, 2))
        assertTrue(pkg.chunks.isEmpty())
        assertFalse(pkg.isMerged)
        assertFalse(SubtitleJsonParser.serialize(pkg).contains("\"chunks\""))
    }
}
