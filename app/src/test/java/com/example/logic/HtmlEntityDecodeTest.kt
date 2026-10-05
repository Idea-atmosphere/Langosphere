package com.example.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HTML escapes in imported book text.
 *
 * The reported sample answered a line that prints `H&H` with `H&amp;amp;H`.
 * The reader binds a translation to a line by comparing the reply's `english`
 * with the document's own text, so an entity that is never decoded is a line
 * that never binds - and the user sees it as "the app cannot read my answer".
 * This is the one place that decision lives, so it is asserted here.
 */
class HtmlEntityDecodeTest {

    @Test
    fun `named entities become their characters`() {
        assertEquals("H&H", HtmlTextUtils.decodeEntities("H&amp;H"))
        assertEquals("He said \"yes\".", HtmlTextUtils.decodeEntities("He said &quot;yes&quot;."))
        assertEquals("a < b > c", HtmlTextUtils.decodeEntities("a &lt; b &gt; c"))
        assertEquals("a b", HtmlTextUtils.decodeEntities("a&nbsp;b"))
        assertEquals("don\u2019t", HtmlTextUtils.decodeEntities("don&rsquo;t"))
        assertEquals("5 \u00D7 3", HtmlTextUtils.decodeEntities("5 &times; 3"))
        assertEquals("A \u2014 B", HtmlTextUtils.decodeEntities("A &mdash; B"))
    }

    @Test
    fun `numeric entities work in both bases`() {
        assertEquals("H&H", HtmlTextUtils.decodeEntities("H&#38;H"))
        assertEquals("H&H", HtmlTextUtils.decodeEntities("H&#x26;H"))
        assertEquals("\u2014", HtmlTextUtils.decodeEntities("&#8212;"))
        assertEquals("\u2014", HtmlTextUtils.decodeEntities("&#x2014;"))
    }

    @Test
    fun `a doubly escaped entity is decoded all the way down`() {
        // What the sample actually returned for sentence 26.
        assertEquals("I tip my hat to H&H!", HtmlTextUtils.decodeEntities("I tip my hat to H&amp;amp;H!"))
        // Three levels, which is as deep as the pass limit allows by design.
        assertEquals("&", HtmlTextUtils.decodeEntities("&amp;amp;amp;"))
        // A code point in the surrogate range would throw; it is left alone.
        assertEquals("&#xD800;", HtmlTextUtils.decodeEntities("&#xD800;"))
    }

    @Test
    fun `unknown escapes and plain text are left exactly as they arrived`() {
        assertEquals("&notanentity;", HtmlTextUtils.decodeEntities("&notanentity;"))
        assertEquals("AT&T", HtmlTextUtils.decodeEntities("AT&T"))
        assertEquals("&", HtmlTextUtils.decodeEntities("&"))
        assertEquals("", HtmlTextUtils.decodeEntities(""))
        assertEquals(
            "The Art of Electronics, 3rd ed.",
            HtmlTextUtils.decodeEntities("The Art of Electronics, 3rd ed."),
        )
    }

    @Test
    fun `decoding is what makes a line match its page exactly`() {
        // The point of the whole exercise: the entity must stop standing
        // between the reply and the printed line.
        val fromThePage = "I tip my hat to H&H!"
        val fromTheModel = HtmlTextUtils.decodeEntities("I tip my hat to H&amp;amp;H!")
        assertEquals(fromThePage, fromTheModel)
        assertEquals(1.0, FuzzyTextAligner.similarity(fromThePage, fromTheModel), 0.0001)

        // An import made before this existed still binds - the entity name
        // reads as one stray word and the score stays above the reader's 0.60
        // acceptance threshold - but it is not the page's own text, and that
        // is exactly why the importer decodes instead of relying on the
        // matcher's tolerance.
        val undecoded = FuzzyTextAligner.similarity(fromThePage, "I tip my hat to H&amp;amp;H!")
        assertTrue("similarity was $undecoded", undecoded > 0.60)
        assertTrue("similarity was $undecoded", undecoded < 1.0)
    }
}
