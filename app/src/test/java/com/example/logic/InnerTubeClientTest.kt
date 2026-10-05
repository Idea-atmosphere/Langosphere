package com.example.logic

import com.example.ui.components.YouTubeEmbedCleanup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct YouTube extraction (InnerTube `player`): which formats become
 * playable streams, how caption tracks turn into WebVTT URLs, and the
 * stylesheet that cleans up the fallback embed. Uses offline fixtures —
 * youtube.com is not reachable (nor reliable) from CI machines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InnerTubeClientTest {

    private val gv = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&ip=1.2.3.4"

    private val fixture = """
    {
      "playabilityStatus": {"status": "OK"},
      "videoDetails": {
        "videoId": "dQw4w9WgXcQ", "title": "Never Gonna Give You Up", "lengthSeconds": "213",
        "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw", "author": "Rick Astley", "viewCount": "1700000000",
        "shortDescription": "The official video", "isLiveContent": false,
        "thumbnail": {"thumbnails": [{"url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg"},
                                     {"url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"}]}
      },
      "streamingData": {
        "formats": [
          {"itag": 18, "url": "$gv&itag=18", "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
           "qualityLabel": "360p", "width": 640, "height": 360},
          {"itag": 22, "signatureCipher": "s=XYZ&sp=sig&url=https%3A%2F%2Fexample", "mimeType": "video/mp4",
           "qualityLabel": "720p", "height": 720}
        ],
        "adaptiveFormats": [
          {"itag": 136, "url": "$gv&itag=136", "mimeType": "video/mp4; codecs=\"avc1.4d401f\"", "bitrate": 1500000,
           "width": 1280, "height": 720, "fps": 30, "qualityLabel": "720p",
           "initRange": {"start": "0", "end": "739"}, "indexRange": {"start": "740", "end": "1233"}},
          {"itag": 247, "url": "$gv&itag=247", "mimeType": "video/webm; codecs=\"vp9\"", "bitrate": 1200000,
           "width": 1280, "height": 720, "fps": 30, "qualityLabel": "720p",
           "initRange": {"start": "0", "end": "219"}, "indexRange": {"start": "220", "end": "900"}},
          {"itag": 271, "url": "$gv&itag=271", "mimeType": "video/webm; codecs=\"vp9\"", "bitrate": 9000000,
           "width": 2560, "height": 1440, "fps": 30, "qualityLabel": "1440p",
           "initRange": {"start": "0", "end": "219"}, "indexRange": {"start": "220", "end": "900"}},
          {"itag": 135, "url": "$gv&itag=135&n=abcdEFGH", "mimeType": "video/mp4; codecs=\"avc1.4d401e\"", "bitrate": 800000,
           "width": 854, "height": 480, "qualityLabel": "480p",
           "initRange": {"start": "0", "end": "739"}, "indexRange": {"start": "740", "end": "1100"}},
          {"itag": 137, "signatureCipher": "s=AAA&url=x", "mimeType": "video/mp4; codecs=\"avc1.640028\"", "height": 1080},
          {"itag": 140, "url": "$gv&itag=140", "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 130000,
           "initRange": {"start": "0", "end": "631"}, "indexRange": {"start": "632", "end": "951"}},
          {"itag": 140, "url": "$gv&itag=140&xtags=drc", "isDrc": true, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 131000,
           "initRange": {"start": "0", "end": "631"}, "indexRange": {"start": "632", "end": "951"}},
          {"itag": 140, "url": "$gv&itag=140&lang=de", "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 999000,
           "audioTrack": {"displayName": "German", "audioIsDefault": false},
           "initRange": {"start": "0", "end": "631"}, "indexRange": {"start": "632", "end": "951"}},
          {"itag": 251, "url": "$gv&itag=251", "mimeType": "audio/webm; codecs=\"opus\"", "bitrate": 140000,
           "initRange": {"start": "0", "end": "265"}, "indexRange": {"start": "266", "end": "700"}}
        ]
      },
      "captions": {
        "playerCaptionsTracklistRenderer": {
          "captionTracks": [
            {"baseUrl": "/api/timedtext?v=dQw4w9WgXcQ&lang=en&fmt=srv3&xosf=1", "name": {"simpleText": "English"},
             "vssId": ".en", "languageCode": "en"},
            {"baseUrl": "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&kind=asr&lang=en",
             "name": {"runs": [{"text": "English (auto-generated)"}]}, "vssId": "a.en", "languageCode": "en", "kind": "asr"},
            {"baseUrl": "https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=fr&exp=xpe",
             "name": {"simpleText": "French"}, "vssId": ".fr", "languageCode": "fr"}
          ]
        }
      }
    }
    """.trimIndent()

    private fun parse(json: String = fixture) =
        InnerTubeClient.parsePlayerResponse(JSONObject(json), "dQw4w9WgXcQ")

    private fun decodeManifest(url: String): String =
        String(java.util.Base64.getDecoder().decode(url.substringAfter("base64,")), Charsets.UTF_8)

    @Test
    fun `player response becomes clean direct streams`() {
        val parsed = parse()
        assertNotNull(parsed.details)
        val d = parsed.details!!
        assertTrue(InnerTubeClient.isDirect(d.instance))
        assertEquals("Never Gonna Give You Up", d.video.title)
        assertEquals("Rick Astley", d.video.author)
        assertEquals(213L, d.video.lengthSeconds)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", d.video.thumbnailUrl)
        assertEquals("The official video", d.description)

        val streams = d.progressiveStreams
        // 720p MP4 (DASH), 720p WebM (DASH), 360p muxed — highest first.
        assertEquals(listOf(720, 720, 360), streams.map { it.height })
        assertTrue(streams[0].url.startsWith("data:application/dash+xml"))
        assertEquals("application/dash+xml", streams[0].mimeType)
        assertFalse(streams[0].isProgressive)
        val mp4Manifest = decodeManifest(streams[0].url)
        assertTrue(mp4Manifest.contains("itag=136"))
        assertTrue("default, non-DRC audio track", mp4Manifest.contains("itag=140</BaseURL>"))
        assertFalse(mp4Manifest.contains("xtags=drc"))
        assertFalse(mp4Manifest.contains("lang=de"))
        assertTrue("WebM video pairs with Opus", decodeManifest(streams[1].url).contains("itag=251"))

        val muxed = streams.last()
        assertTrue(muxed.isProgressive)
        assertEquals("$gv&itag=18", muxed.url)
        assertEquals("video/mp4", muxed.mimeType)
        assertEquals(muxed.url, d.playbackUrl) // muxed file is the default pick
    }

    @Test
    fun `formats needing youtube javascript are skipped`() {
        val urls = parse().details!!.progressiveStreams.flatMap { s ->
            if (s.url.startsWith("data:")) listOf(decodeManifest(s.url)) else listOf(s.url)
        }.joinToString("\n")
        assertFalse("n-challenge URL", urls.contains("n=abcdEFGH"))
        assertFalse("signatureCipher", urls.contains("itag=22") || urls.contains("itag=137"))
        assertFalse("above 1080p", urls.contains("itag=271"))

        assertNull(InnerTubeClient.usableUrl(JSONObject("""{"url":"$gv&n=xyz"}""")))
        assertNull(InnerTubeClient.usableUrl(JSONObject("""{"signatureCipher":"s=1&url=x"}""")))
        assertNull(InnerTubeClient.usableUrl(JSONObject("""{"url":"$gv","drmFamilies":["WIDEVINE"]}""")))
        assertEquals("$gv&itag=18", InnerTubeClient.usableUrl(JSONObject("""{"url":"$gv&itag=18"}""")))
    }

    @Test
    fun `caption tracks become webvtt urls and po-token-only tracks are dropped`() {
        val tracks = parse().captions
        assertEquals(2, tracks.size)
        assertEquals("https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&lang=en&fmt=vtt", tracks[0].url)
        assertEquals("English", tracks[0].label)
        assertFalse(tracks[0].autoGenerated)
        assertEquals("text/vtt", tracks[0].mimeType)
        assertEquals("https://www.youtube.com/api/timedtext?v=dQw4w9WgXcQ&kind=asr&lang=en&fmt=vtt", tracks[1].url)
        assertEquals("English (auto-generated)", tracks[1].label)
        assertTrue(tracks[1].autoGenerated)
        // Manual English is downloaded before auto captions.
        assertEquals(tracks[0], OnlineVideoRepository.orderCaptionTracks(tracks.reversed()).first())

        assertTrue(InnerTubeClient.captionNeedsPoToken("https://www.youtube.com/api/timedtext?v=a&exp=xpv"))
        assertFalse(InnerTubeClient.captionNeedsPoToken("https://www.youtube.com/api/timedtext?v=a&expire=1"))
        assertEquals("https://x/t?fmt=vtt", InnerTubeClient.vttCaptionUrl("https://x/t"))
    }

    @Test
    fun `sabr-only response still yields captions but no stream`() {
        val json = JSONObject(fixture)
        val sd = json.getJSONObject("streamingData")
        sd.remove("formats")
        sd.put("adaptiveFormats", org.json.JSONArray("""[{"itag":136,"mimeType":"video/mp4","height":720}]"""))
        sd.put("serverAbrStreamingUrl", "https://rr1.googlevideo.com/sabr")
        val parsed = InnerTubeClient.parsePlayerResponse(json, "dQw4w9WgXcQ")
        assertNull(parsed.details)
        assertEquals(2, parsed.captions.size)
    }

    @Test
    fun `hls manifest alone is playable`() {
        val json = JSONObject(fixture)
        json.put("streamingData", JSONObject().put("hlsManifestUrl", "https://manifest.googlevideo.com/api/manifest/hls_variant/x"))
        val d = InnerTubeClient.parsePlayerResponse(json, "dQw4w9WgXcQ").details!!
        assertTrue(d.progressiveStreams.isEmpty())
        assertTrue(d.playbackIsHls)
    }

    @Test
    fun `refused clip reports youtube's reason`() {
        try {
            parse("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm you're not a bot"}}""")
            fail("expected an exception")
        } catch (e: OnlineVideoRepository.OnlineException) {
            assertEquals("LOGIN_REQUIRED: Sign in to confirm you're not a bot", e.message)
        }
    }

    @Test
    fun `player request uses the javascript-less visionos client`() {
        val profile = InnerTubeClient.CLIENTS.first()
        assertEquals("VISIONOS", profile.clientName)
        val body = InnerTubeClient.buildPlayerBody(profile, "dQw4w9WgXcQ", "CgtWSVNJVE9S")
        val client = body.getJSONObject("context").getJSONObject("client")
        assertEquals("VISIONOS", client.getString("clientName"))
        assertEquals("en", client.getString("hl"))
        assertEquals("CgtWSVNJVE9S", client.getString("visitorData"))
        assertEquals("dQw4w9WgXcQ", body.getString("videoId"))
        assertTrue(body.getBoolean("contentCheckOk"))
        assertEquals(
            "HTML5_PREF_WANTS",
            body.getJSONObject("playbackContext").getJSONObject("contentPlaybackContext").getString("html5Preference")
        )
        assertFalse(InnerTubeClient.buildContext(profile, null).getJSONObject("client").has("visitorData"))
    }

    @Test
    fun `embed stylesheet hides youtube clutter but not the video or errors`() {
        val css = YouTubeEmbedCleanup.CSS
        listOf(".ytp-chrome-top", ".ytp-show-cards-title", ".ytp-watermark", ".ytp-pause-overlay", ".ytp-ce-element",
            ".ytp-title-channel", ".ytp-caption-window-container").forEach {
            assertTrue(it, YouTubeEmbedCleanup.HIDDEN_SELECTORS.contains(it))
        }
        assertFalse(YouTubeEmbedCleanup.HIDDEN_SELECTORS.any { it == "video" || it.contains("ytp-error") || it.contains("html5-main-video") })
        assertTrue(css.endsWith("pointer-events:none!important}"))
        assertTrue(YouTubeEmbedCleanup.SCRIPT.contains(".ytp-watermark"))
        assertTrue(YouTubeEmbedCleanup.ORIGINS.contains("https://www.youtube-nocookie.com"))
    }
}
