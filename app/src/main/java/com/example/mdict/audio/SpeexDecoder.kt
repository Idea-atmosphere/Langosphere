package com.example.mdict.audio

import org.xiph.speex.Bits
import org.xiph.speex.Decoder
import org.xiph.speex.NbDecoder
import org.xiph.speex.SbDecoder
import org.xiph.speex.SpeexDecoder as JSpeexDecoder
import java.io.ByteArrayOutputStream

/**
 * Decodes Speex (.spx) audio to 16-bit PCM, pure Java via the vendored
 * JSpeex sources (org.xiph.speex, compiled by package_apk.sh). Two
 * container shapes are supported: raw Speex (80-byte "Speex   " header
 * + frames, the shape MDict MDDs store) and Ogg Speex ("OggS" pages).
 *
 * No native code and no MediaPlayer: Android cannot decode Speex, which
 * is why the original MDict app cannot play these files either (BlueDict
 * bundles a native Speex decoder for the same reason).
 *
 * Raw frames are byte-aligned in the file (each frame is stored as a
 * whole number of bytes), so the bitstream itself delimits frames: each
 * decode() consumes exactly one frame, and Bits.bufferSize reports how
 * many bytes that frame occupied. That makes the raw path independent
 * of the header bitrate (which MDict files write as -1).
 */
object SpeexDecoder {

    data class Pcm(val sampleRate: Int, val channels: Int, val data: ByteArray)

    /** True when the bytes look like Speex (Ogg or raw with header). */
    fun isSpeex(bytes: ByteArray): Boolean {
        if (bytes.size >= 4 && bytes[0] == 'O'.code.toByte() &&
            bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() &&
            bytes[3] == 'S'.code.toByte()
        ) return true
        return bytes.size >= 80 &&
            String(bytes, 0, 8, Charsets.US_ASCII) == "Speex   "
    }

    /** Decodes raw or Ogg Speex bytes to 16-bit PCM. Null when the bytes are not Speex or cannot be decoded. */
    fun decode(spx: ByteArray): Pcm? {
        return try {
            if (spx.size >= 4 && spx[0] == 'O'.code.toByte() &&
                spx[1] == 'g'.code.toByte() && spx[2] == 'g'.code.toByte() &&
                spx[3] == 'S'.code.toByte()
            ) decodeOgg(spx) else decodeRaw(spx)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeRaw(spx: ByteArray): Pcm? {
        if (spx.size < 88) return null
        if (String(spx, 0, 8, Charsets.US_ASCII) != "Speex   ") return null
        val sampleRate = le32(spx, 36)
        val mode = spx[40].toInt() and 0xFF
        val channels = le32(spx, 48)
        if (mode !in 0..2 || sampleRate <= 0 || channels !in 1..2) return null
        val decoder = newDecoder(mode) ?: return null
        val frameSamples = decoder.frameSize
        val bits = Bits()
        bits.init()
        val out = ByteArrayOutputStream()
        val floatBuf = FloatArray(frameSamples * channels)
        var pos = 80
        // Feed in 1 KiB windows (Bits' internal buffer is 1024 bytes);
        // a frame is at most ~100 bytes, so it always fits in one window.
        while (pos < spx.size) {
            val remaining = spx.size - pos
            if (remaining < 5) break // trailing padding; smallest frame is 6 bytes
            val chunk = minOf(1024, remaining)
            bits.read_from(spx, pos, chunk)
            try {
                decoder.decode(bits, floatBuf)
            } catch (e: Exception) {
                break
            }
            val consumed = bits.bufferSize
            if (consumed <= 0 || consumed > chunk) break
            pos += consumed
            if (channels == 2) decoder.decodeStereo(floatBuf, frameSamples)
            writePcm16(out, floatBuf, frameSamples * channels)
        }
        if (out.size() == 0) return null
        return Pcm(sampleRate, channels, out.toByteArray())
    }

    private fun decodeOgg(spx: ByteArray): Pcm? {
        val packets = readOggPackets(spx) ?: return null
        if (packets.isEmpty()) return null
        val header = packets[0]
        if (header.size != 80 ||
            String(header, 0, 8, Charsets.US_ASCII) != "Speex   "
        ) return null
        val sampleRate = le32(header, 36)
        val mode = header[40].toInt() and 0xFF
        val channels = le32(header, 48)
        val nframes = le32(header, 64).coerceAtLeast(1)
        if (mode !in 0..2 || sampleRate <= 0 || channels !in 1..2) return null
        val decoder = JSpeexDecoder()
        if (!decoder.init(mode, sampleRate, channels, true)) return null
        val out = ByteArrayOutputStream()
        // Packet 1 is the comment; audio packets start at 2.
        for (i in 2 until packets.size) {
            val packet = packets[i]
            if (packet.isEmpty()) continue
            try {
                decoder.processData(packet, 0, packet.size)
                for (f in 1 until nframes) decoder.processData(false)
            } catch (e: Exception) {
                break
            }
            val size = decoder.processedDataByteSize
            if (size > 0) {
                val buf = ByteArray(size)
                decoder.getProcessedData(buf, 0)
                out.write(buf)
            }
        }
        if (out.size() == 0) return null
        return Pcm(sampleRate, channels, out.toByteArray())
    }

    /** Splits an Ogg stream into its packets (segments of 255 bytes continue the packet). */
    private fun readOggPackets(data: ByteArray): List<ByteArray>? {
        val packets = ArrayList<ByteArray>()
        var pos = 0
        var continued: ByteArrayOutputStream? = null
        while (pos + 27 <= data.size) {
            if (String(data, pos, 4, Charsets.US_ASCII) != "OggS") return null
            val nseg = data[pos + 26].toInt() and 0xFF
            if (pos + 27 + nseg > data.size) return null
            var off = pos + 27 + nseg
            for (i in 0 until nseg) {
                val seg = data[pos + 27 + i].toInt() and 0xFF
                if (off + seg > data.size) return null
                if (seg < 255) {
                    val pkt = ByteArrayOutputStream()
                    continued?.let { pkt.write(it.toByteArray()); continued = null }
                    pkt.write(data, off, seg)
                    packets.add(pkt.toByteArray())
                } else {
                    if (continued == null) continued = ByteArrayOutputStream()
                    continued.write(data, off, seg)
                }
                off += seg
            }
            pos = off
        }
        return packets
    }

    private fun newDecoder(mode: Int): Decoder? {
        val decoder: Decoder = when (mode) {
            0 -> NbDecoder().apply { nbinit() }
            1 -> SbDecoder().apply { wbinit() }
            2 -> SbDecoder().apply { uwbinit() }
            else -> return null
        }
        decoder.setPerceptualEnhancement(true)
        return decoder
    }

    private fun writePcm16(out: ByteArrayOutputStream, samples: FloatArray, count: Int) {
        val buf = ByteArray(count * 2)
        for (i in 0 until count) {
            var s = samples[i]
            if (s > 32767f) s = 32767f else if (s < -32768f) s = -32768f
            val v = if (s > 0) (s + 0.5f).toInt() else (s - 0.5f).toInt()
            buf[i * 2] = (v and 0xFF).toByte()
            buf[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(buf)
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)
}
