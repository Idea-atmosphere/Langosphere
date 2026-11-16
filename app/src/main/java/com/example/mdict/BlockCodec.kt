package com.example.mdict

import java.io.ByteArrayOutputStream
import java.util.zip.Adler32
import java.util.zip.Inflater

/**
 * Block codec, port of _decode_block / _fast_decrypt / _salsa_decrypt
 * from readmdict.py.
 *
 * Every key/record block starts with an 8-byte header:
 * [0..4) little-endian info word, [4..8) big-endian Adler32.
 * info: bits 0..3 = compression (0 none, 1 LZO, 2 zlib),
 * bits 4..7 = encryption (0 none, 1 fast XOR, 2 Salsa20/8),
 * bits 8..15 = number of leading encrypted bytes.
 *
 * Adler32 is checked over the decrypted block for v3+, and over the
 * decompressed block for v1/v2, exactly like the reference.
 */
object BlockCodec {

    fun adler32(data: ByteArray): Long {
        val a = Adler32()
        a.update(data)
        return a.value
    }

    fun adler32(data: ByteArray, off: Int, len: Int): Long {
        val a = Adler32()
        a.update(data, off, len)
        return a.value
    }

    /** Fast XOR decryption (readmdict._fast_decrypt). */
    fun fastDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var previous = 0x36
        for (i in data.indices) {
            val b = data[i].toInt() and 0xff
            var t = ((b ushr 4) or (b shl 4)) and 0xff
            t = t xor previous xor (i and 0xff) xor (key[i % key.size].toInt() and 0xff)
            previous = b
            out[i] = t.toByte()
        }
        return out
    }

    /**
     * Decodes one block. [encryptedKey] is the dictionary key when one is
     * known (passcode / v3 UUID); null means "derive it from the block",
     * i.e. ripemd128(adler bytes), like the reference.
     */
    fun decodeBlock(
        block: ByteArray,
        decompressedSize: Long,
        version: Double,
        encryptedKey: ByteArray?
    ): ByteArray {
        if (block.size < 8) {
            throw MdictException(MdictException.Kind.CORRUPT, "truncated block")
        }
        val info = le32(block, 0)
        val compression = info and 0xf
        val encryption = (info shr 4) and 0xf
        val encryptionSize = (info shr 8) and 0xff
        val adler = be32(block, 4).toLong() and 0xffffffffL
        val key = encryptedKey ?: Ripemd128.digest(block.copyOfRange(4, 8))
        val data = block.copyOfRange(8, block.size)

        val decrypted: ByteArray = when (encryption) {
            0 -> data
            1 -> {
                val n = minOf(encryptionSize, data.size)
                fastDecrypt(data.copyOfRange(0, n), key) + data.copyOfRange(n, data.size)
            }
            2 -> {
                val n = minOf(encryptionSize, data.size)
                Salsa20(key, rounds = 8).crypt(data.copyOfRange(0, n)) +
                    data.copyOfRange(n, data.size)
            }
            else -> throw MdictException(MdictException.Kind.UNSUPPORTED, "unknown block encryption")
        }

        if (version >= 3.0 && adler != adler32(decrypted)) {
            throw MdictException(MdictException.Kind.CORRUPT, "block checksum mismatch")
        }

        val decompressed: ByteArray = when (compression) {
            0 -> decrypted
            1 -> throw MdictException(MdictException.Kind.UNSUPPORTED, "LZO compression")
            2 -> inflate(decrypted, decompressedSize)
            else -> throw MdictException(MdictException.Kind.UNSUPPORTED, "unknown block compression")
        }

        if (version < 3.0 && adler != adler32(decompressed)) {
            throw MdictException(MdictException.Kind.CORRUPT, "block checksum mismatch")
        }
        return decompressed
    }

    private fun inflate(data: ByteArray, expectedSize: Long): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(
                if (expectedSize in 1..(16 * 1024 * 1024)) expectedSize.toInt() else 8192
            )
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput()) break
                    // No progress and no input needed: corrupt stream.
                    throw MdictException(MdictException.Kind.CORRUPT, "zlib inflate failed")
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } catch (e: MdictException) {
            throw e
        } catch (e: Exception) {
            throw MdictException(MdictException.Kind.CORRUPT, "zlib inflate failed")
        } finally {
            inflater.end()
        }
    }

    fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    fun be32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xff) shl 24) or
            ((b[off + 1].toInt() and 0xff) shl 16) or
            ((b[off + 2].toInt() and 0xff) shl 8) or
            (b[off + 3].toInt() and 0xff)
}
