package com.example.mdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure entry-HTML rewriting in MdictEntryHtml: resources
 * become same-origin https://mdict.local/ URLs, plain words become
 * entry:// links, and the sound.mdx case (a speaker icon inside a sound://
 * link) must keep the sound link working instead of opening the image
 * viewer.
 */
class MdictEntryHtmlTest {

    private val base = MdictEntryHtml.RESOURCE_BASE

    @Test
    fun imgInsideSoundLinkIsNotWrapped() {
        val out = MdictEntryHtml.rewriteEntryLinks(
            """<a href="sound://hello.mp3"><img src="speaker.png"></a>"""
        )
        // The sound link survives intact; no image:// link is created.
        assertEquals("""<a href="sound://hello.mp3"><img src="${base}speaker.png"></a>""", out)
        assertFalse(out.contains("image://"))
    }

    @Test
    fun standaloneImgIsWrappedForViewer() {
        val out = MdictEntryHtml.rewriteEntryLinks("""<img src="pic.png">""")
        assertEquals("""<a href="image://pic.png"><img src="${base}pic.png"></a>""", out)
        // Opt-out used when a caller renders its own tap handling.
        assertEquals("""<img src="${base}pic.png">""", MdictEntryHtml.rewriteEntryLinks("""<img src="pic.png">""", wrapImages = false))
    }

    @Test
    fun imgInsideEntryLinkIsNotWrapped() {
        val out = MdictEntryHtml.rewriteEntryLinks("""<a href="hello"><img src="pic.png"></a>""")
        assertTrue(out.contains("entry://hello"))
        assertFalse(out.contains("image://"))
    }

    @Test
    fun plainWordLinkAndAbsoluteImg() {
        val out = MdictEntryHtml.rewriteEntryLinks(
            """<a href="world">world</a> <img src="/images/logo.png">"""
        )
        assertTrue(out.contains("entry://world"))
        assertTrue(out.contains("""<a href="image://images/logo.png"><img src="${base}images/logo.png"></a>"""))
    }

    @Test
    fun stylesheetsFontsAndPassThroughLinks() {
        val out = MdictEntryHtml.rewriteEntryLinks(
            """<link rel="stylesheet" href="oxford.css"><a href="https://example.com/x">web</a>""" +
                """<a href="#sense2">anchor</a><img src="data:image/png;base64,AAAA">""" +
                """<a href="entry://already">e</a><a href='tone.wav'>wav</a>"""
        )
        assertTrue(out.contains("""href="${base}oxford.css""""))
        assertTrue(out.contains("""href="https://example.com/x""""))
        assertTrue(out.contains("""href="#sense2""""))
        assertTrue(out.contains("""src="data:image/png;base64,AAAA""""))
        assertTrue(out.contains("""href="entry://already""""))
        assertTrue(out.contains("""href='${base}tone.wav'"""))
    }

    @Test
    fun resourcePathOfHandlesBothForms() {
        assertEquals("images/logo.png", MdictEntryHtml.resourcePathOf("${base}images/logo.png?v=1#x"))
        assertEquals("a b.png", MdictEntryHtml.resourcePathOf("${base}a%20b.png"))
        assertEquals("tone.spx", MdictEntryHtml.resourcePathOf("dict://tone.spx"))
        assertNull(MdictEntryHtml.resourcePathOf("https://example.com/pic.png"))
        assertNull(MdictEntryHtml.resourcePathOf("entry://hello"))
        assertNull(MdictEntryHtml.resourcePathOf(base))
    }

    @Test
    fun audioPathAndMimeDetection() {
        assertTrue(MdictEntryHtml.isAudioPath("${base}tone.mp3"))
        assertTrue(MdictEntryHtml.isAudioPath("tone.wav"))
        assertTrue(MdictEntryHtml.isAudioPath("a/b.ogg"))
        assertTrue(MdictEntryHtml.isAudioPath("tone.spx"))
        assertFalse(MdictEntryHtml.isAudioPath("pic.png"))
        assertEquals("image/png", MdictEntryHtml.mimeFor("x/pic.PNG?x=1"))
        assertEquals("text/css", MdictEntryHtml.mimeFor("style.css"))
        assertEquals("audio/x-speex", MdictEntryHtml.mimeFor("word.spx"))
        assertEquals("font/woff2", MdictEntryHtml.mimeFor("f.woff2"))
        assertEquals("application/octet-stream", MdictEntryHtml.mimeFor("blob.bin"))
    }
}
