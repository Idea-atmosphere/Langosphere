package com.example.mdict.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xiph.speex.AudioFileWriter
import org.xiph.speex.OggSpeexWriter
import org.xiph.speex.SpeexEncoder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Round-trip tests for SpeexDecoder: encode a 440 Hz sine with JSpeex's
 * own SpeexEncoder (raw + Ogg), decode it back, and verify the tone.
 * This is the same Speex shape MDict MDDs store (raw, 80-byte header).
 */
class SpeexDecoderTest {

    @Test
    fun rawRoundTripIs440HzTone() {
        val spx = encodeSineRaw(freqHz = 440.0, seconds = 1.0)
        assertTrue(SpeexDecoder.isSpeex(spx))
        val pcm = SpeexDecoder.decode(spx)
        assertNotNull(pcm)
        pcm!!
        assertEquals(8000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertTone(pcm.data, expectedHz = 440.0, sampleRate = 8000)
    }

    @Test
    fun oggRoundTripIs440HzTone() {
        val ogg = encodeSineOgg(freqHz = 440.0, seconds = 1.0)
        assertTrue(SpeexDecoder.isSpeex(ogg))
        val pcm = SpeexDecoder.decode(ogg)
        assertNotNull(pcm)
        assertTone(pcm!!.data, expectedHz = 440.0, sampleRate = 8000)
    }

    @Test
    fun notSpeexReturnsNull() {
        assertFalse(SpeexDecoder.isSpeex(byteArrayOf(1, 2, 3, 4)))
        assertNull(SpeexDecoder.decode(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(SpeexDecoder.decode("hello world, not speex at all.........".toByteArray()))
    }

    @Test
    fun truncatedHeaderReturnsNull() {
        val spx = encodeSineRaw(440.0, 0.5)
        assertNull(SpeexDecoder.decode(spx.copyOfRange(0, 40)))
        // Header only, no frames.
        assertNull(SpeexDecoder.decode(spx.copyOfRange(0, 80)))
    }

    /** Encodes a sine to raw Speex: 80-byte header + CBR frames. */
    private fun encodeSineRaw(freqHz: Double, seconds: Double): ByteArray {
        val sampleRate = 8000
        val enc = SpeexEncoder()
        assertTrue(enc.init(0, 8, sampleRate, 1))
        val frameSize = enc.frameSize
        val total = (seconds * sampleRate).toInt()
        val out = ByteArrayOutputStream()
        out.write(AudioFileWriter.buildSpeexHeader(sampleRate, 0, 1, false, 1))
        val frameBuf = ByteArray(256)
        var pos = 0
        while (pos < total) {
            val frame = ShortArray(frameSize)
            for (i in 0 until frameSize) {
                val idx = pos + i
                frame[i] = if (idx < total) {
                    (12000 * sin(2 * PI * freqHz * idx / sampleRate)).toInt().toShort()
                } else {
                    0
                }
            }
            assertTrue(enc.processData(frame, 0, frameSize))
            val size = enc.getProcessedData(frameBuf, 0)
            assertTrue(size > 0)
            out.write(frameBuf, 0, size)
            pos += frameSize
        }
        return out.toByteArray()
    }

    /** Encodes a sine to Ogg Speex via OggSpeexWriter. */
    private fun encodeSineOgg(freqHz: Double, seconds: Double): ByteArray {
        val sampleRate = 8000
        val enc = SpeexEncoder()
        assertTrue(enc.init(0, 8, sampleRate, 1))
        val frameSize = enc.frameSize
        val total = (seconds * sampleRate).toInt()
        val tmp = File.createTempFile("speex-test", ".spx")
        val writer = OggSpeexWriter(0, sampleRate, 1, 1, false)
        writer.open(tmp)
        writer.writeHeader("test")
        val frameBuf = ByteArray(256)
        var pos = 0
        while (pos < total) {
            val frame = ShortArray(frameSize)
            for (i in 0 until frameSize) {
                val idx = pos + i
                frame[i] = if (idx < total) {
                    (12000 * sin(2 * PI * freqHz * idx / sampleRate)).toInt().toShort()
                } else {
                    0
                }
            }
            assertTrue(enc.processData(frame, 0, frameSize))
            val size = enc.getProcessedData(frameBuf, 0)
            writer.writePacket(frameBuf, 0, size)
            pos += frameSize
        }
        writer.close()
        return tmp.readBytes()
    }

    /** Checks the PCM is a tone near expectedHz: RMS sane + zero-crossing rate matches. */
    private fun assertTone(pcmBytes: ByteArray, expectedHz: Double, sampleRate: Int) {
        assertTrue(pcmBytes.size >= 8000) // at least ~0.5 s of audio
        val n = pcmBytes.size / 2
        var sumSq = 0.0
        var crossings = 0
        var prev = 0
        for (i in 0 until n) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt()
            val s = (hi shl 8) or lo
            sumSq += s.toDouble() * s
            val sign = if (s >= 0) 1 else -1
            if (i > 0 && sign != prev) crossings++
            prev = sign
        }
        val rms = sqrt(sumSq / n)
        assertTrue("RMS too low ($rms), decoded audio is silent", rms > 1000)
        val measuredHz = crossings * sampleRate / (2.0 * n)
        assertTrue(
            "tone $measuredHz Hz, expected ~$expectedHz Hz",
            measuredHz in expectedHz * 0.8..expectedHz * 1.2
        )
    }
}
