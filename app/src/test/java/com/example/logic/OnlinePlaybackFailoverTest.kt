package com.example.logic

import com.example.model.OnlineInstance
import com.example.model.OnlineInstanceKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Playback failover against real local HTTP servers: a 403 (YouTube blocked
 * the instance), a 500 and a dropped connection each move on to the next
 * instance, and at most PLAYBACK_MAX_ATTEMPTS instances are asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlinePlaybackFailoverTest {

    private val servers = mutableListOf<ServerSocket>()

    private val pipedStreams = """
        {"title":"Test clip","uploader":"Teacher","uploaderUrl":"/channel/UCHaHD477h-FeBbVh9Sh7syA","duration":60,
         "videoStreams":[
           {"url":"https://cdn.example/v1080","quality":"1080p","mimeType":"video/mp4","videoOnly":true,"height":1080,
            "bitrate":4000000,"codec":"avc1.640028","initStart":0,"initEnd":740,"indexStart":741,"indexEnd":1200,"width":1920,"fps":30},
           {"url":"https://cdn.example/v360","quality":"360p","mimeType":"video/mp4","videoOnly":false,"height":360}
         ],
         "audioStreams":[
           {"url":"https://cdn.example/a","quality":"128 kbps","mimeType":"audio/mp4","bitrate":128000,"codec":"mp4a.40.2",
            "initStart":0,"initEnd":600,"indexStart":601,"indexEnd":900}
         ],
         "subtitles":[],"relatedStreams":[]}
    """.trimIndent()

    @Before
    fun setUp() = OnlineVideoRepository.forgetOutages()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.close() } }
        OnlineVideoRepository.forgetOutages()
    }

    /** A one-response-per-connection HTTP server on 127.0.0.1; [respond] = null drops the connection. */
    private fun server(respond: ((String) -> Pair<Int, String>)?): String {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        servers.add(socket)
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client: Socket = try { socket.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) {
                    client.use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        val requestLine = reader.readLine().orEmpty()
                        while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                        val handler = respond ?: return@use
                        val (code, body) = handler(requestLine)
                        val bytes = body.toByteArray()
                        val head = "HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        c.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
                    }
                }
            }
        }
        return "http://127.0.0.1:${socket.localPort}"
    }

    private fun piped(url: String) = OnlineInstance(url, OnlineInstanceKind.PIPED)

    @Test
    fun `403, 500 and a dropped connection fail over to the next instance`() = runBlocking {
        val forbidden = server { 403 to "" }
        val broken = server { 500 to "<html><title>Bad gateway</title></html>" }
        val dropped = server(null)
        val working = server { line -> if (line.contains("/streams/")) 200 to pipedStreams else 404 to "" }

        val served = OnlineVideoRepository.videoDetails(
            listOf(piped(forbidden), piped(broken), piped(dropped), piped(working)),
            "dQw4w9WgXcQ"
        )
        assertEquals(working, served.instance?.baseUrl)
        val d = served.value
        assertEquals("Test clip", d.video.title)
        // Muxed 360p is what the player opens first.
        val first = d.progressiveStreams[OnlineStreamSelector.defaultStreamIndex(d.progressiveStreams)]
        assertEquals("https://cdn.example/v360", first.url)
        assertTrue(first.isProgressive)
        // The 1080p video-only stream became a DASH manifest paired with audio.
        val hd = d.progressiveStreams.first { it.height == 1080 }
        assertTrue(hd.url.startsWith("data:application/dash+xml"))
        assertEquals("application/dash+xml", hd.mimeType)
        assertNull(hd.audioUrl)
    }

    @Test
    fun `gives up after four instances instead of walking the whole list`() = runBlocking {
        val hits = java.util.concurrent.atomic.AtomicInteger()
        val forbidden = (1..5).map { server { hits.incrementAndGet(); 403 to "" } }
        val working = server { 200 to pipedStreams }
        try {
            OnlineVideoRepository.videoDetails((forbidden + working).map { piped(it) }, "dQw4w9WgXcQ")
            fail("Expected AllInstancesFailed")
        } catch (e: OnlineVideoRepository.AllInstancesFailed) {
            assertEquals(OnlineVideoRepository.PLAYBACK_MAX_ATTEMPTS, e.failures.size)
            assertTrue(e.failures.all { it.reason.startsWith("HTTP 403") })
        }
        assertEquals(OnlineVideoRepository.PLAYBACK_MAX_ATTEMPTS, hits.get())
    }

    @Test
    fun `byte ranges and dash manifests`() {
        assertEquals(741L to 1200L, OnlineVideoRepository.parseByteRange("741-1200"))
        assertNull(OnlineVideoRepository.parseByteRange("1200-741"))
        assertNull(OnlineVideoRepository.parseByteRange(""))
        val video = OnlineVideoRepository.DashTrack("https://cdn.example/v?a=1&b=2", "video/mp4", "avc1.4d401f", 1000, 0L to 740L, 741L to 1200L, 1280, 720, 30)
        val audio = OnlineVideoRepository.DashTrack("https://cdn.example/a", "audio/mp4", "mp4a.40.2", 128, 0L to 600L, 601L to 900L)
        val uri = OnlineVideoRepository.buildDashManifest(video, audio, 60)
        assertNotNull(uri)
        val xml = String(android.util.Base64.decode(uri!!.substringAfter("base64,"), android.util.Base64.DEFAULT))
        assertTrue(xml.contains("<BaseURL>https://cdn.example/v?a=1&amp;b=2</BaseURL>"))
        assertTrue(xml.contains("indexRange=\"741-1200\""))
        assertTrue(xml.contains("mimeType=\"audio/mp4\""))
        assertTrue(xml.contains("mediaPresentationDuration=\"PT60S\""))
        // Missing ranges or duration → no manifest (the caller keeps the explicit pair).
        assertNull(OnlineVideoRepository.buildDashManifest(video.copy(indexRange = null), audio, 60))
        assertNull(OnlineVideoRepository.buildDashManifest(video, audio, 0))
    }
}
