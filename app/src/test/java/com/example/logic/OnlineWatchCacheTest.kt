package com.example.logic

import com.example.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the subtitle half of [OnlineWatchCache] («ذخیره هنگام پخش»):
 * the round-trip of a watched caption track through its JSON file, and the
 * guarantees the player relies on — a missing or corrupt file reads as
 * "nothing cached", never as a crash. Robolectric supplies org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlineWatchCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sample = listOf(
        SubtitleEntry(start = 0.0, end = 1.5, text = "Hello", language = "en"),
        SubtitleEntry(start = 1.5, end = 3.25, text = "world", language = "en")
    )

    @Test
    fun `a saved main track round-trips with its language and label`() {
        val dir = tmp.newFolder("d1")
        OnlineWatchCache.saveCaptionsToDir(
            dir, OnlineWatchCache.SLOT_MAIN, "en", "English (auto)", sample
        )
        val loaded = OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_MAIN)
        assertNotNull(loaded)
        assertEquals("en", loaded!!.languageCode)
        assertEquals("English (auto)", loaded.label)
        assertEquals(sample, loaded.entries)
    }

    @Test
    fun `the translation slot is stored separately from the main slot`() {
        val dir = tmp.newFolder("d2")
        OnlineWatchCache.saveCaptionsToDir(
            dir, OnlineWatchCache.SLOT_MAIN, "en", "English", sample
        )
        val fa = listOf(SubtitleEntry(start = 0.0, end = 1.5, text = "سلام", language = "fa"))
        OnlineWatchCache.saveCaptionsToDir(
            dir, OnlineWatchCache.SLOT_TRANSLATION, "fa", "Persian", fa
        )
        assertEquals("fa", OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_TRANSLATION)!!.languageCode)
        assertEquals("en", OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_MAIN)!!.languageCode)
        assertEquals(fa, OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_TRANSLATION)!!.entries)
    }

    @Test
    fun `nothing saved reads as null, not as a crash`() {
        val dir = tmp.newFolder("d3")
        assertNull(OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_MAIN))
    }

    @Test
    fun `a corrupt or half-written file reads as null`() {
        val dir = tmp.newFolder("d4")
        dir.resolve("${OnlineWatchCache.SLOT_MAIN}.json").writeText("{ not json ]]")
        assertNull(OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_MAIN))
    }

    @Test
    fun `entries without text are dropped instead of kept as blank cues`() {
        val dir = tmp.newFolder("d5")
        val withBlank = sample + SubtitleEntry(start = 4.0, end = 5.0, text = "   ", language = "en")
        OnlineWatchCache.saveCaptionsToDir(dir, OnlineWatchCache.SLOT_MAIN, "en", "English", withBlank)
        val loaded = OnlineWatchCache.loadCaptionsFromDir(dir, OnlineWatchCache.SLOT_MAIN)
        assertEquals(sample, loaded!!.entries)
    }

    @Test
    fun `the default-quality pick honours the chosen height`() {
        // Sorted by height descending, like the repository returns them:
        // 1080p dash, 720p muxed mp4, 480p muxed, 360p muxed.
        val streams = listOf(
            stream("1080p", "video/webm", 1080, progressive = false),
            stream("720p", "video/mp4", 720),
            stream("480p", "video/mp4", 480),
            stream("360p", "video/mp4", 360)
        )
        // AUTO keeps the selector's muxed-first policy (720p here).
        assertEquals(1, OnlineWatchCache.pickDefaultStreamIndex(streams, OnlineWatchCache.QUALITY_AUTO))
        // HIGHEST takes the sharpest rendition.
        assertEquals(0, OnlineWatchCache.pickDefaultStreamIndex(streams, OnlineWatchCache.QUALITY_HIGHEST))
        // A concrete target takes the best rendition at or below it.
        assertEquals(2, OnlineWatchCache.pickDefaultStreamIndex(streams, 480))
        assertEquals(1, OnlineWatchCache.pickDefaultStreamIndex(streams, 720))
        // Nothing at or below a tiny target: the smallest one, never -1.
        assertEquals(3, OnlineWatchCache.pickDefaultStreamIndex(streams, 240))
        // Empty list stays -1 (the caller coerces it to a safe index).
        assertEquals(-1, OnlineWatchCache.pickDefaultStreamIndex(emptyList(), OnlineWatchCache.QUALITY_HIGHEST))
    }

    @Test
    fun `stream keys are stable per rendition and split same-label containers`() {
        val mp4 = com.example.model.OnlineStream(
            url = "https://a/v.mp4", qualityLabel = "720p", mimeType = "video/mp4; codecs=\"avc1\"", height = 720
        )
        val webm = com.example.model.OnlineStream(
            url = "https://a/v.webm", qualityLabel = "720p", mimeType = "video/webm; codecs=\"vp9\"", height = 720
        )
        assertEquals(OnlineWatchCache.streamKey("abc123", mp4), OnlineWatchCache.streamKey("abc123", mp4.copy(url = "https://b/v.mp4")))
        // Same label, different container: two files, two keys.
        org.junit.Assert.assertNotEquals(OnlineWatchCache.streamKey("abc123", mp4), OnlineWatchCache.streamKey("abc123", webm))
        // The adaptive pair's audio is one shared file per video.
        assertEquals(OnlineWatchCache.audioKey("abc123"), OnlineWatchCache.audioKey("abc123"))
        org.junit.Assert.assertNotEquals(OnlineWatchCache.audioKey("abc123"), OnlineWatchCache.audioKey("xyz789"))
    }

    @Test
    fun `dash manifest urls are extracted and xml-unescaped`() {
        val manifest = buildString {
            append("<?xml version=\"1.0\"?><MPD><Period>")
            append("<AdaptationSet><Representation id=\"video\"><BaseURL>https://rr1.googlevideo.com/videoplayback?clen=1&amp;ratebypass=yes</BaseURL></Representation></AdaptationSet>")
            append("<AdaptationSet><Representation id=\"audio\"><BaseURL>https://rr2.googlevideo.com/videoplayback?clen=2&amp;mime=audio</BaseURL></Representation></AdaptationSet>")
            append("</Period></MPD>")
        }
        val encoded = "data:application/dash+xml;charset=utf-8;base64," +
            android.util.Base64.encodeToString(manifest.toByteArray(), android.util.Base64.NO_WRAP)
        assertEquals(
            listOf(
                "https://rr1.googlevideo.com/videoplayback?clen=1&ratebypass=yes",
                "https://rr2.googlevideo.com/videoplayback?clen=2&mime=audio"
            ),
            OnlineWatchCache.dashManifestUrls(encoded)
        )
        // Plain URLs and foreign data URIs read as "nothing to map".
        org.junit.Assert.assertTrue(OnlineWatchCache.dashManifestUrls("https://plain.example/v.mp4").isEmpty())
        org.junit.Assert.assertTrue(OnlineWatchCache.dashManifestUrls("data:text/plain;base64,aGk=").isEmpty())
        org.junit.Assert.assertTrue(OnlineWatchCache.dashManifestUrls("data:application/dash+xml;base64,%%%not-base64%%%").isEmpty())
    }

    private fun stream(label: String, mime: String, height: Int, progressive: Boolean = true) =
        com.example.model.OnlineStream(
            url = "https://a/$label.$mime",
            qualityLabel = label,
            mimeType = mime,
            height = height,
            isProgressive = progressive
        )
}
