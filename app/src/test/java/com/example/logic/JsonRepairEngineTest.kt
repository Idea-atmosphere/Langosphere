package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRepairEngineTest {

    @Test
    fun `parses well formed json`() {
        val parsed = JsonRepairEngine.parse("""{"a":1,"b":[true,null,"x"]}""")
        assertNotNull(parsed)
        assertEquals(1, parsed!!["a"]?.asInt())
        assertEquals(3, parsed["b"]?.asList()?.size)
    }

    @Test
    fun `keys are matched case insensitively`() {
        val parsed = JsonRepairEngine.parse("""{"TargetLanguage":"Persian"}""")
        assertEquals("Persian", parsed?.stringOrNull("targetlanguage"))
    }

    @Test
    fun `unwraps fenced code blocks and prose`() {
        val raw = """
            Sure, here is the JSON you asked for:
            ```json
            {"formatVersion":1}
            ```
        """.trimIndent()
        val unwrapped = JsonRepairEngine.unwrap(raw)
        assertEquals("""{"formatVersion":1}""", unwrapped)
        val parsed = JsonRepairEngine.parse(unwrapped)
        assertEquals(1, parsed?.get("formatVersion")?.asInt())
    }

    @Test
    fun `closes brackets left open by token truncation`() {
        val truncated = """{"subtitles":[{"id":1,"english":"First."},{"id":2,"english":"Second."""
        val result = JsonRepairEngine.repair(truncated)
        assertTrue(result.wasRepaired)
        assertTrue(result.truncated)
        val parsed = JsonRepairEngine.parse(result.json)
        assertNotNull(parsed)
        assertTrue((parsed!!["subtitles"]?.asList()?.size ?: 0) >= 1)
    }

    @Test
    fun `closes a string cut off mid value`() {
        val truncated = """{"subtitles":[{"id":1,"english":"An unfinished sente"""
        val (parsed, _) = JsonRepairEngine.repairAndParse(truncated)
        assertNotNull(parsed)
    }

    @Test
    fun `escapes an inner quote followed by Persian punctuation`() {
        // The old rescue only fired when the follower was a letter or digit,
        // so a quote before `،` ended the string early and shifted the rest.
        val raw = """{"translation":"او گفت "سلام"، و رفت","level":"A2"}"""
        val (parsed, repair) = JsonRepairEngine.repairAndParse(raw)
        assertNotNull(parsed)
        assertEquals("او گفت \"سلام\"، و رفت", parsed?.stringOrNull("translation"))
        assertEquals("A2", parsed?.stringOrNull("level"))
        assertTrue(repair.repairs.any { it.contains("inner unescaped quote") })
    }

    @Test
    fun `escapes an inner quote followed by a full stop`() {
        val raw = """{"english":"He whispered "stop".","id":2}"""
        val (parsed, _) = JsonRepairEngine.repairAndParse(raw)
        assertNotNull(parsed)
        assertEquals("He whispered \"stop\".", parsed?.stringOrNull("english"))
        assertEquals(2, parsed?.get("id")?.asInt())
    }

    @Test
    fun `still finds the closer past a thousand character value`() {
        // The structural-quote lookahead must clear book-sized values: the
        // inner quote sits near the start and the real closer ~1000 chars
        // later, past the old 800-char window.
        // No trailing space: stringOrNull trims, so the tail ends mid-word
        // and the comparison below is exact on both sides.
        val longTail = "word ".repeat(200).trimEnd()
        val raw = """{"english":"He said "hi"، then $longTail","level":"B1"}"""
        val (parsed, _) = JsonRepairEngine.repairAndParse(raw)
        assertNotNull(parsed)
        assertEquals("He said \"hi\"، then $longTail", parsed?.stringOrNull("english"))
        assertEquals("B1", parsed?.stringOrNull("level"))
    }

    @Test
    fun `drops trailing commas`() {
        val (parsed, _) = JsonRepairEngine.repairAndParse("""{"a":[1,2,],}""")
        assertNotNull(parsed)
        assertEquals(2, parsed!!["a"]?.asList()?.size)
    }

    @Test
    fun `garbage input fails instead of inventing data`() {
        assertNull(JsonRepairEngine.parse("this is not json at all"))
    }

    @Test
    fun `escape round trips through the parser`() {
        val original = """He said "under-\nstand" — twice"""
        val json = """{"v":"${JsonRepairEngine.escape(original)}"}"""
        assertEquals(original, JsonRepairEngine.parse(json)?.stringOrNull("v"))
    }
}
