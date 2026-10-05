package com.example.logic

import com.example.logic.ReleaseNotesParser.Block
import com.example.logic.ReleaseNotesParser.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ReleaseNotesParser]: the markdown subset the in-app updater's
 * changelog view understands. The fixtures mirror what GitHub actually
 * writes into a release "body" — auto-generated notes ("What's Changed", a
 * bullet per PR, a "Full Changelog" compare link) and hand-written notes
 * with headings, bold/italic/code and links.
 */
class ReleaseNotesParserTest {

    // ── the document GitHub generates for a tagged release ──

    @Test
    fun `parses github auto-generated release notes`() {
        val notes = """
            ## What's Changed
            * Add the in-app updater by @idea-atmosphere in https://github.com/Idea-atmosphere/Langosphere/pull/12
            * Fix the reader by @idea-atmosphere in https://github.com/Idea-atmosphere/Langosphere/pull/13


            **Full Changelog**: https://github.com/Idea-atmosphere/Langosphere/compare/v0.0.72...v0.0.73
        """.trimIndent()

        val blocks = ReleaseNotesParser.parse(notes)

        assertEquals(4, blocks.size)

        val heading = blocks[0] as Block.Heading
        assertEquals(2, heading.level)
        assertEquals(listOf(Span("What's Changed")), heading.spans)

        val firstItem = blocks[1] as Block.ListItem
        assertTrue(firstItem.bullet)
        assertEquals(
            listOf(
                Span("Add the in-app updater by @idea-atmosphere in "),
                Span(
                    "https://github.com/Idea-atmosphere/Langosphere/pull/12",
                    linkUrl = "https://github.com/Idea-atmosphere/Langosphere/pull/12"
                )
            ),
            firstItem.spans
        )

        val secondItem = blocks[2] as Block.ListItem
        assertTrue(secondItem.bullet)

        val changelog = blocks[3] as Block.Paragraph
        assertEquals(
            listOf(
                Span("Full Changelog", bold = true),
                Span(": "),
                Span(
                    "https://github.com/Idea-atmosphere/Langosphere/compare/v0.0.72...v0.0.73",
                    linkUrl = "https://github.com/Idea-atmosphere/Langosphere/compare/v0.0.72...v0.0.73"
                )
            ),
            changelog.spans
        )
    }

    // ── headings, dividers, code blocks ──

    @Test
    fun `heading levels`() {
        val blocks = ReleaseNotesParser.parse("# Title\n### Small\n###### Tiny")
        assertEquals(3, blocks.size)
        assertEquals(1, (blocks[0] as Block.Heading).level)
        assertEquals(3, (blocks[1] as Block.Heading).level)
        assertEquals(6, (blocks[2] as Block.Heading).level)
    }

    @Test
    fun `horizontal rules`() {
        val blocks = ReleaseNotesParser.parse("before\n---\n***\nafter")
        assertEquals(listOf<Block>(Block.Paragraph(listOf(Span("before"))), Block.Divider, Block.Divider, Block.Paragraph(listOf(Span("after")))), blocks)
    }

    @Test
    fun `fenced code block`() {
        val blocks = ReleaseNotesParser.parse("Intro\n```\nval x = 1\nval y = 2\n```\nOutro")
        assertEquals(3, blocks.size)
        assertEquals(Block.CodeBlock("val x = 1\nval y = 2"), blocks[1])
    }

    @Test
    fun `unclosed code fence still yields a block`() {
        val blocks = ReleaseNotesParser.parse("```\nonly line")
        assertEquals(1, blocks.size)
        assertEquals(Block.CodeBlock("only line"), blocks[0])
    }

    // ── list items ──

    @Test
    fun `bullet markers`() {
        val blocks = ReleaseNotesParser.parse("- dash\n* star\n+ plus")
        assertEquals(3, blocks.size)
        assertTrue((blocks[0] as Block.ListItem).bullet)
        assertTrue((blocks[1] as Block.ListItem).bullet)
        assertTrue((blocks[2] as Block.ListItem).bullet)
        assertEquals(listOf(Span("dash")), (blocks[0] as Block.ListItem).spans)
    }

    @Test
    fun `ordered list items are not bullets`() {
        val blocks = ReleaseNotesParser.parse("1. first\n2) second")
        assertEquals(2, blocks.size)
        assertTrue(!(blocks[0] as Block.ListItem).bullet)
        assertTrue(!(blocks[1] as Block.ListItem).bullet)
        assertEquals(listOf(Span("first")), (blocks[0] as Block.ListItem).spans)
    }

    // ── inline styling ──

    @Test
    fun `bold italic and code spans`() {
        assertEquals(
            listOf(Span("b", bold = true), Span(" "), Span("i", italic = true), Span(" "), Span("c", code = true)),
            ReleaseNotesParser.parseInline("**b** *i* `c`")
        )
    }

    @Test
    fun `underscore italic works at word boundaries`() {
        assertEquals(
            listOf(Span("very "), Span("important", italic = true)),
            ReleaseNotesParser.parseInline("very _important_")
        )
    }

    @Test
    fun `intraword underscores stay literal`() {
        // GFM does not emphasise inside a word, so identifiers survive.
        assertEquals(
            listOf(Span("fix_json_parsing")),
            ReleaseNotesParser.parseInline("fix_json_parsing")
        )
    }

    @Test
    fun `unbalanced markers stay literal`() {
        assertEquals(
            listOf(Span("a * b")),
            ReleaseNotesParser.parseInline("a * b")
        )
        assertEquals(
            listOf(Span("**oops")),
            ReleaseNotesParser.parseInline("**oops")
        )
    }

    @Test
    fun `labelled links`() {
        assertEquals(
            listOf(Span("see "), Span("the docs", linkUrl = "https://example.com/docs")),
            ReleaseNotesParser.parseInline("see [the docs](https://example.com/docs)")
        )
    }

    @Test
    fun `relative links stay literal text`() {
        // The changelog can only open web links, so anything else renders as
        // its raw markdown rather than a broken clickable span.
        val spans = ReleaseNotesParser.parseInline("see [note](notes/x.md)")
        assertEquals(1, spans.size)
        assertTrue(spans[0].linkUrl == null)
    }

    @Test
    fun `bare urls become links`() {
        assertEquals(
            listOf(Span("go to "), Span("https://example.com", linkUrl = "https://example.com")),
            ReleaseNotesParser.parseInline("go to https://example.com")
        )
    }

    @Test
    fun `trailing sentence punctuation is not part of a bare url`() {
        val spans = ReleaseNotesParser.parseInline("read https://example.com/a?b=c.")
        assertEquals("https://example.com/a?b=c", spans[1].linkUrl)
        assertEquals(".", spans[2].text)
        assertTrue(spans[2].linkUrl == null)
    }

    @Test
    fun `plain text is a single span`() {
        assertEquals(listOf(Span("just words")), ReleaseNotesParser.parseInline("just words"))
    }

    // ── edge cases ──

    @Test
    fun `empty notes parse to no blocks`() {
        assertTrue(ReleaseNotesParser.parse("").isEmpty())
        assertTrue(ReleaseNotesParser.parse("   \n\n  ").isEmpty())
    }
}
