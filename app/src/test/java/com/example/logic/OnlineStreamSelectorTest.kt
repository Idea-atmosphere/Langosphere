package com.example.logic

import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import com.example.model.OnlineStream
import com.example.model.OnlineVideo
import com.example.model.OnlineVideoDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stream choice for the Online player: muxed first, never a lone video-only file. */
class OnlineStreamSelectorTest {

    private val inst = OnlineInstance("https://pipedapi.example", OnlineInstanceKind.PIPED)

    private fun muxed(height: Int, mime: String = "video/mp4") =
        OnlineStream(url = "https://cdn.example/muxed$height.$mime", qualityLabel = "${height}p", mimeType = mime, height = height, isProgressive = true)

    private fun dash(height: Int) =
        OnlineStream(url = "data:application/dash+xml;base64,$height", qualityLabel = "${height}p", mimeType = "application/dash+xml", height = height, isProgressive = false)

    private fun pair(height: Int) =
        OnlineStream(url = "https://cdn.example/video$height", qualityLabel = "${height}p", mimeType = "video/mp4", audioUrl = "https://cdn.example/audio", height = height, isProgressive = false)

    private fun details(streams: List<OnlineStream>, hls: String? = null, dashUrl: String? = null) =
        OnlineVideoDetails(video = OnlineVideo("dQw4w9WgXcQ", "t"), progressiveStreams = streams, hlsUrl = hls, dashUrl = dashUrl, instance = inst)

    @Test
    fun `muxed 720p is preferred over a higher or equal adaptive rendition`() {
        val streams = listOf(dash(1080), dash(720), muxed(720), muxed(360))
        assertEquals(2, OnlineStreamSelector.defaultStreamIndex(streams))
    }

    @Test
    fun `muxed 360p beats adaptive 720p when there is no muxed 720p`() {
        val streams = listOf(dash(1080), dash(720), muxed(360), muxed(144))
        assertEquals(2, OnlineStreamSelector.defaultStreamIndex(streams))
    }

    @Test
    fun `mp4 wins over webm at the same height`() {
        val streams = listOf(muxed(360, "video/webm"), muxed(360, "video/mp4"))
        assertEquals(1, OnlineStreamSelector.defaultStreamIndex(streams))
    }

    @Test
    fun `adaptive at or below 720p is used when there is no muxed file`() {
        val streams = listOf(dash(1080), dash(720), dash(480))
        assertEquals(1, OnlineStreamSelector.defaultStreamIndex(streams))
        assertEquals(-1, OnlineStreamSelector.defaultStreamIndex(emptyList()))
    }

    @Test
    fun `fallback chain is selected, other muxed, manifests, then adaptive pairs`() {
        val streams = listOf(dash(1080), pair(720), muxed(720), muxed(360))
        val d = details(streams, hls = "https://pipedapi.example/hls.m3u8", dashUrl = "https://pipedapi.example/dash.mpd")
        val chain = OnlineStreamSelector.playbackSources(d, selectedIndex = 2, maxSources = 10)
        assertEquals(listOf(2, 3, -1, -1, 1, 0), chain.map { it.streamIndex })
        assertEquals(OnlineStreamSelector.MIME_HLS, chain[2].mimeType)
        assertEquals(OnlineStreamSelector.MIME_DASH, chain[3].mimeType)
        // The adaptive pair keeps its audio partner so the player merges both.
        assertEquals("https://cdn.example/audio", chain[4].audioUrl)
        assertNull(chain[0].audioUrl)
    }

    @Test
    fun `chain is capped per instance`() {
        val streams = listOf(muxed(720), muxed(480), muxed(360), muxed(240), muxed(144))
        val chain = OnlineStreamSelector.playbackSources(details(streams), selectedIndex = 0)
        assertEquals(OnlineStreamSelector.MAX_SOURCES_PER_INSTANCE, chain.size)
        assertEquals(listOf(0, 2), chain.take(2).map { it.streamIndex })
    }

    @Test
    fun `a video-only url without audio is never offered`() {
        val lonely = OnlineStream(url = "https://cdn.example/videoonly", qualityLabel = "1080p", mimeType = "video/mp4", height = 1080, isProgressive = false)
        val chain = OnlineStreamSelector.playbackSources(details(listOf(lonely, muxed(360))), selectedIndex = 0, maxSources = 10)
        assertTrue(chain.none { it.url == lonely.url })
        assertEquals(1, chain.single().streamIndex)
    }

    @Test
    fun `playback url prefers a muxed file`() {
        val d = details(listOf(dash(1080), muxed(360)))
        assertEquals("https://cdn.example/muxed360.video/mp4", d.playbackUrl)
    }

    @Test
    fun `mime types are stripped of codec parameters`() {
        assertEquals("video/mp4", OnlineStreamSelector.mediaMimeType("video/mp4; codecs=\"avc1.42001E, mp4a.40.2\""))
        assertNull(OnlineStreamSelector.mediaMimeType("  "))
    }
}
