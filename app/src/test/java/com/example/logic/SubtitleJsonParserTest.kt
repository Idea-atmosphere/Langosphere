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
 * Tests for the subtitle-learning JSON parser/validator: format detection,
 * tolerant field mapping (including alternate key names), validation
 * errors, and forward compatibility with future JSON versions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleJsonParserTest {

    private val sampleJson = SubtitleJsonParser.buildSampleJsonString()

    // ── detection ──

    @Test
    fun `looksLikeSubtitleJson accepts the sample package`() {
        assertTrue(SubtitleJsonParser.looksLikeSubtitleJson(sampleJson))
    }

    @Test
    fun `looksLikeSubtitleJson rejects plain srt text`() {
        assertFalse(SubtitleJsonParser.looksLikeSubtitleJson("1\n00:00:01,000 --> 00:00:02,000\nHello"))
        assertFalse(SubtitleJsonParser.looksLikeSubtitleJson("just some text"))
        assertFalse(SubtitleJsonParser.looksLikeSubtitleJson(""))
    }

    @Test
    fun `looksLikeSubtitleJson rejects arbitrary json`() {
        assertFalse(SubtitleJsonParser.looksLikeSubtitleJson("""{"hello": "world"}"""))
    }

    // ── parsing the standard structure ──

    @Test
    fun `sample package parses with metadata lesson and words`() {
        val pkg = SubtitleJsonParser.parse(sampleJson)
        assertEquals(1, pkg.formatVersion)
        assertEquals(2, pkg.subtitles.size)
        assertEquals("English", pkg.metadata?.language)
        assertEquals("Persian", pkg.metadata?.targetLanguage)
        assertEquals("B1", pkg.metadata?.level)

        val first = pkg.subtitles[0]
        assertEquals("1", first.id)
        assertEquals(12.4, first.start!!, 0.001)
        assertEquals(15.8, first.end!!, 0.001)
        assertEquals("I have been working here for five years.", first.english)
        assertTrue(first.translation!!.contains("پنج سال"))
        assertEquals("B1", first.level)
        assertEquals("medium", first.difficulty)
        assertEquals("Present Perfect Continuous", first.lesson?.grammar)
        assertEquals("حال کامل استمراری", first.lesson?.grammarTranslation)
        assertTrue(first.lesson?.explanation!!.contains("Present perfect continuous"))
        assertEquals(1, first.words.size)
        assertEquals("working", first.words[0].word)
        assertEquals("verb", first.words[0].partOfSpeech)
        assertEquals("کار کردن", first.words[0].translation)
        assertEquals("/ˈwɜːrkɪŋ/", first.words[0].pronunciation)
        assertEquals(1, first.words[0].examples.size)
    }

    @Test
    fun `minimal subtitle json parses`() {
        val pkg = SubtitleJsonParser.parse(
            """{ "subtitles": [ { "english": "Hello.", "translation": "سلام." } ] }"""
        )
        assertEquals(1, pkg.subtitles.size)
        assertEquals("Hello.", pkg.subtitles[0].english)
        assertEquals("سلام.", pkg.subtitles[0].translation)
    }

    @Test
    fun `bare array root is accepted`() {
        val pkg = SubtitleJsonParser.parse(
            """[ { "english": "One.", "translation": "یک." }, { "english": "Two." } ]"""
        )
        assertEquals(2, pkg.subtitles.size)
        assertEquals("Two.", pkg.subtitles[1].english)
    }

    @Test
    fun `data wrapper is accepted`() {
        val pkg = SubtitleJsonParser.parse(
            """{ "data": { "subtitles": [ { "english": "Hi", "translation": "سلام" } ] } }"""
        )
        assertEquals(1, pkg.subtitles.size)
    }

    // ── alternate key names (tolerant mapping) ──

    @Test
    fun `alternate field names are mapped`() {
        val json = """
            {
              "formatVersion": 2,
              "metadata": { "lang": "en", "targetLang": "fa", "cefrLevel": "A2", "desc": "x" },
              "subtitles": [
                {
                  "index": 7,
                  "from": 3.5,
                  "to": 6.0,
                  "text": "She runs fast.",
                  "target": "او سریع می‌دود.",
                  "cefrLevel": "A2",
                  "difficultyLevel": "easy",
                  "ipa": "/ʃiː rʌnz fæst/",
                  "learningNotes": "note here",
                  "lesson": {
                    "grammarTopic": "Simple Present",
                    "grammarFa": "حال ساده",
                    "sentenceStructure": "S + V + Adv"
                  },
                  "words": [
                    { "term": "runs", "meaning": "می‌دود", "type": "verb", "contextMeaning": "moves fast", "detail": "third person singular" }
                  ]
                }
              ]
            }
        """.trimIndent()
        val pkg = SubtitleJsonParser.parse(json)
        assertEquals(2, pkg.formatVersion) // future versions stay parseable
        assertEquals("en", pkg.metadata?.language)
        assertEquals("fa", pkg.metadata?.targetLanguage)
        assertEquals("A2", pkg.metadata?.level)

        val sub = pkg.subtitles[0]
        assertEquals("7", sub.id)
        assertEquals(3.5, sub.start!!, 0.001)
        assertEquals(6.0, sub.end!!, 0.001)
        assertEquals("She runs fast.", sub.english)
        assertEquals("او سریع می‌دود.", sub.translation)
        assertEquals("A2", sub.level)
        assertEquals("easy", sub.difficulty)
        assertEquals("/ʃiː rʌnz fæst/", sub.pronunciation)
        assertEquals("note here", sub.notes)
        assertEquals("Simple Present", sub.lesson?.grammar)
        assertEquals("حال ساده", sub.lesson?.grammarTranslation)
        assertEquals("S + V + Adv", sub.lesson?.structure)
        assertEquals("runs", sub.words[0].word)
        assertEquals("verb", sub.words[0].partOfSpeech)
        assertEquals("moves fast", sub.words[0].meaningInContext)
        assertEquals("third person singular", sub.words[0].extraExplanation)
    }

    @Test
    fun `words as keyed object and bare strings are accepted`() {
        val json = """
            {
              "subtitles": [
                {
                  "english": "I love apples and bananas.",
                  "words": {
                    "love": { "translation": "دوست داشتن", "pos": "verb" },
                    "apples": "سیب‌ها"
                  }
                },
                { "english": "Quick test.", "words": ["quick", "test"] }
              ]
            }
        """.trimIndent()
        val pkg = SubtitleJsonParser.parse(json)
        assertEquals(2, pkg.subtitles[0].words.size)
        assertEquals("love", pkg.subtitles[0].words[0].word)
        assertEquals("verb", pkg.subtitles[0].words[0].partOfSpeech)
        assertEquals("apples", pkg.subtitles[0].words[1].word)
        assertEquals("سیب‌ها", pkg.subtitles[0].words[1].translation)
        assertEquals(2, pkg.subtitles[1].words.size)
        assertEquals("quick", pkg.subtitles[1].words[0].word)
    }

    @Test
    fun `examples as string are split`() {
        val pkg = SubtitleJsonParser.parse(
            """{ "subtitles": [ { "english": "Go on.", "words": [ { "word": "go", "examples": "Let's go.|Go away!" } ] } ] }"""
        )
        assertEquals(2, pkg.subtitles[0].words[0].examples.size)
    }

    // ── validation errors ──

    @Test
    fun `invalid json syntax raises friendly error`() {
        try {
            SubtitleJsonParser.parse("{ not json !!!")
            throw AssertionError("expected SubtitleJsonParseException")
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            assertTrue(e.message!!.contains("syntax", ignoreCase = true))
        }
    }

    @Test
    fun `missing subtitles array raises friendly error`() {
        try {
            SubtitleJsonParser.parse("""{ "metadata": { "language": "English" } }""")
            throw AssertionError("expected SubtitleJsonParseException")
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            assertTrue(e.message!!.contains("subtitles"))
        }
    }

    @Test
    fun `subtitles without english text raise friendly error`() {
        try {
            SubtitleJsonParser.parse("""{ "subtitles": [ { "id": 1 } ] }""")
            throw AssertionError("expected SubtitleJsonParseException")
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            assertTrue(e.message!!.contains("english", ignoreCase = true))
        }
    }

    @Test
    fun `empty input raises friendly error`() {
        try {
            SubtitleJsonParser.parse("   ")
            throw AssertionError("expected SubtitleJsonParseException")
        } catch (e: SubtitleJsonParser.SubtitleJsonParseException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun `isValidSubtitleJson mirrors parse`() {
        assertTrue(SubtitleJsonParser.isValidSubtitleJson(sampleJson))
        assertFalse(SubtitleJsonParser.isValidSubtitleJson("{ bad json"))
        assertFalse(SubtitleJsonParser.isValidSubtitleJson("""{ "subtitles": [] }"""))
    }

    // ── forward compatibility ──

    @Test
    fun `unknown fields and future format versions are ignored`() {
        val json = """
            {
              "formatVersion": 99,
              "someFutureField": { "whatever": true },
              "subtitles": [
                { "english": "Future-proof.", "translation": "آینده‌نگهدار.", "unknownExtra": 42 }
              ]
            }
        """.trimIndent()
        val pkg = SubtitleJsonParser.parse(json)
        assertEquals(99, pkg.formatVersion)
        assertEquals(1, pkg.subtitles.size)
        assertEquals("Future-proof.", pkg.subtitles[0].english)
    }

    @Test
    fun `sample package has expected metadata`() {
        val pkg = SubtitleJsonParser.buildSamplePackage(level = "C1")
        assertEquals("C1", pkg.metadata?.level)
        assertTrue(pkg.subtitles.isNotEmpty())
        assertNotNull(pkg.subtitles[0].lesson)
        assertTrue(pkg.subtitles.any { it.start != null && it.end != null })
        assertNull(pkg.subtitles[0].translation?.let { if (it.isEmpty()) it else null })
    }

    // ── serialization (used to persist JSON time shifts) ──

    @Test
    fun `serialize round-trips through parse`() {
        val pkg = SubtitleJsonParser.parse(sampleJson)
        val reParsed = SubtitleJsonParser.parse(SubtitleJsonParser.serialize(pkg))
        assertEquals(pkg.formatVersion, reParsed.formatVersion)
        assertEquals(pkg.subtitles.size, reParsed.subtitles.size)
        pkg.subtitles.zip(reParsed.subtitles).forEach { (original, re) ->
            assertEquals(original.id, re.id)
            assertEquals(original.english, re.english)
            assertEquals(original.translation, re.translation)
            assertEquals(original.start, re.start)
            assertEquals(original.end, re.end)
            assertEquals(original.level, re.level)
            assertEquals(original.difficulty, re.difficulty)
        }
    }

    @Test
    fun `serialize preserves shifted timestamps`() {
        val pkg = SubtitleJsonParser.parse(sampleJson)
        val shifted = pkg.copy(
            subtitles = pkg.subtitles.map { s ->
                s.copy(start = s.start?.plus(0.5), end = s.end?.plus(0.5))
            }
        )
        val reParsed = SubtitleJsonParser.parse(SubtitleJsonParser.serialize(shifted))
        assertEquals(12.9, reParsed.subtitles[0].start!!, 0.001)
        assertEquals(16.3, reParsed.subtitles[0].end!!, 0.001)
        assertEquals(16.6, reParsed.subtitles[1].start!!, 0.001)
    }
}
