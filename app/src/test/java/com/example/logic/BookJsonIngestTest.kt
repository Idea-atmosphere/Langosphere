package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookJsonIngestTest {

    private fun payload(): String = """
        {
          "formatVersion": 1,
          "metadata": {
            "source": "book",
            "language": "English",
            "targetLanguage": "Persian",
            "level": "B1",
            "description": "page 128"
          },
          "subtitles": [
            {
              "id": 1, "start": 0, "end": 1, "location": "p. 128 s. 1",
              "english": "The cat sat on the mat.",
              "translation": "گربه روی قالیچه نشست.",
              "level": "B1", "difficulty": "easy",
              "pronunciation": "/ðə kæt sæt ɒn ðə mæt/", "notes": "",
              "lesson": {
                "explanation": "توضیح",
                "grammar": "Simple past",
                "grammarTranslation": "گذشته ساده",
                "structure": "S + V + PP"
              },
              "words": [
                {
                  "word": "mat", "translation": "قالیچه",
                  "partOfSpeech": "noun", "meaningInContext": "floor covering",
                  "extraExplanation": "", "examples": ["Wipe your feet on the mat."],
                  "pronunciation": "/mæt/"
                }
              ]
            },
            {
              "id": 2, "start": 1, "end": 2, "location": "p. 128 s. 2",
              "english": "Then it slept.",
              "translation": "سپس خوابید.",
              "level": "B1", "difficulty": "easy",
              "pronunciation": "/ðen ıt slept/", "notes": "",
              "lesson": { "explanation": "", "grammar": "", "grammarTranslation": "", "structure": "" },
              "words": []
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `ingests a well formed batch`() {
        val result = BookJsonIngest.ingest(payload())
        assertTrue(result.errorKey.orEmpty(), result.isSuccess)
        assertEquals(2, result.sentences.size)
        assertEquals("The cat sat on the mat.", result.sentences.first().english)
        assertEquals("p. 128 s. 2", result.sentences.last().location)
        assertEquals(1, result.sentences.first().words.size)
        assertEquals("Persian", result.metadata.targetLanguage)
    }

    @Test
    fun `checkpoint reflects the last sentence received`() {
        val result = BookJsonIngest.ingest(payload())
        val checkpoint = result.checkpoint
        assertEquals(2, checkpoint.lastId)
        assertEquals("p. 128 s. 2", checkpoint.lastLocation)
        assertEquals(3, checkpoint.nextId)
        assertFalse(checkpoint.isEmpty)
    }

    @Test
    fun `recovers sentences from a truncated response`() {
        val truncated = payload().substringBefore("\"id\": 2").trimEnd().removeSuffix(",").trimEnd()
        val result = BookJsonIngest.ingest(truncated + "{")
        assertTrue(result.isSuccess)
        assertEquals(1, result.sentences.size)
        assertTrue(result.wasRepaired)
    }

    @Test
    fun `merge discards duplicate ids and keeps the richer entry`() {
        val first = BookJsonIngest.ingest(payload()).sentences
        val second = BookJsonIngest.ingest(payload()).sentences
        val merged = BookJsonIngest.merge(first, second)
        assertEquals(2, merged.size)
        assertEquals(listOf(1, 2), merged.map { it.id })
        assertTrue(merged.first().hasLesson)
    }

    @Test
    fun `merge keeps sentences ordered by id`() {
        val batch = BookJsonIngest.ingest(payload()).sentences
        val merged = BookJsonIngest.merge(batch.reversed(), emptyList())
        assertEquals(listOf(1, 2), merged.map { it.id })
    }

    @Test
    fun `export produces a reingestable master file`() {
        val result = BookJsonIngest.ingest(payload())
        val exported = BookJsonIngest.exportMasterJson(result.sentences, result.metadata)
        val parsed = JsonRepairEngine.parse(exported)
        assertNotNull(parsed)
        assertEquals(1, parsed!!["formatVersion"]?.asInt())

        val reingested = BookJsonIngest.ingest(exported)
        assertTrue(reingested.isSuccess)
        assertEquals(result.sentences.size, reingested.sentences.size)
        assertEquals(
            result.sentences.map { it.english },
            reingested.sentences.map { it.english },
        )
    }

    @Test
    fun `empty input fails with an error key instead of throwing`() {
        val result = BookJsonIngest.ingest("   ")
        assertFalse(result.isSuccess)
        assertNotNull(result.errorKey)
    }
}
