package com.example.zonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The /models/ interceptor must answer like a tiny static file server: 206 for ranges, real 404s, HEAD without a body length. */
class ModelFileServerTest {

    private val body = ByteArray(10) { it.toByte() }
    private val lookup: (String) -> ModelFileServer.FileEntry? = { rel ->
        if (rel == "backbone/shard-000.bin") ModelFileServer.FileEntry(body.size.toLong()) { body.inputStream() } else null
    }

    @Test
    fun missingFileIsARealNotFound() {
        val r = ModelFileServer.decide("/models/backbone/nope.bin", "GET", emptyMap(), lookup)
        assertNotNull(r)
        assertEquals(404, r!!.status)
    }

    @Test
    fun oneByteProbeIsPartialContent() {
        val r = ModelFileServer.decide("/models/backbone/shard-000.bin", "GET", mapOf("Range" to "bytes=0-0"), lookup)
        assertNotNull(r)
        assertEquals(206, r!!.status)
        assertEquals("bytes 0-0/10", r.headers["Content-Range"])
        assertEquals("1", r.headers["Content-Length"])
        assertEquals(1, r.stream.readBytes().size)
    }

    @Test
    fun fullGetStreamsTheWholeFile() {
        val r = ModelFileServer.decide("/models/backbone/shard-000.bin", "GET", emptyMap(), lookup)
        assertEquals(200, r!!.status)
        assertEquals("10", r.headers["Content-Length"])
        assertEquals(10, r.stream.readBytes().size)
    }

    @Test
    fun headHasNoContentLength() {
        val r = ModelFileServer.decide("/models/backbone/shard-000.bin", "HEAD", emptyMap(), lookup)
        assertEquals(200, r!!.status)
        assertFalse(r.headers.containsKey("Content-Length"))
        assertEquals("bytes", r.headers["Accept-Ranges"])
    }

    @Test
    fun otherPathsFallThroughToTheAssetLoader() {
        assertNull(ModelFileServer.decide("/assets/zonos/index.html", "GET", emptyMap(), lookup))
        assertNull(ModelFileServer.decide("/models/../secret", "GET", emptyMap(), lookup))
        assertNull(ModelFileServer.decide(null, "GET", emptyMap(), lookup))
    }
}
