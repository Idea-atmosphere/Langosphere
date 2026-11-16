package com.example.mdict

/**
 * Salsa20 stream cipher, pure Kotlin port of pureSalsa20.py (mdict-utils).
 * MDict uses the reduced Salsa20/8 variant with a 16-byte key and an
 * 8-byte zero IV. Encryption and decryption are the same operation
 * (XOR with the keystream).
 *
 * State layout (16 little-endian words):
 * constants "expand 16-byte k" at 0, 5, 10, 15; key words at 1..4 and
 * 11..14 (16-byte key repeated); IV words at 6, 7; counter at 8, 9.
 */
class Salsa20(key: ByteArray, iv: ByteArray = ByteArray(8), private val rounds: Int = 8) {

    private val ctx = IntArray(16)

    init {
        require(key.size == 16 || key.size == 32) { "Salsa20 key must be 16 or 32 bytes" }
        require(iv.size == 8) { "Salsa20 IV must be 8 bytes" }
        val constants = "expand 16-byte k".toByteArray(Charsets.US_ASCII)
        if (key.size == 32) {
            // "expand 32-byte k", key halves at 1..4 and 11..14.
            val c32 = "expand 32-byte k".toByteArray(Charsets.US_ASCII)
            ctx[1] = le32(key, 0)
            ctx[2] = le32(key, 4)
            ctx[3] = le32(key, 8)
            ctx[4] = le32(key, 12)
            ctx[11] = le32(key, 16)
            ctx[12] = le32(key, 20)
            ctx[13] = le32(key, 24)
            ctx[14] = le32(key, 28)
            ctx[0] = le32(c32, 0)
            ctx[5] = le32(c32, 4)
            ctx[10] = le32(c32, 8)
            ctx[15] = le32(c32, 12)
        } else {
            ctx[1] = le32(key, 0)
            ctx[2] = le32(key, 4)
            ctx[3] = le32(key, 8)
            ctx[4] = le32(key, 12)
            ctx[11] = le32(key, 0)
            ctx[12] = le32(key, 4)
            ctx[13] = le32(key, 8)
            ctx[14] = le32(key, 12)
            ctx[0] = le32(constants, 0)
            ctx[5] = le32(constants, 4)
            ctx[10] = le32(constants, 8)
            ctx[15] = le32(constants, 12)
        }
        ctx[6] = le32(iv, 0)
        ctx[7] = le32(iv, 4)
        ctx[8] = 0
        ctx[9] = 0
    }

    private fun le32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or
            ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or
            ((b[off + 3].toInt() and 0xff) shl 24)

    private fun counter(): Long =
        (ctx[8].toLong() and 0xffffffffL) or ((ctx[9].toLong() and 0xffffffffL) shl 32)

    private fun setCounter(value: Long) {
        ctx[8] = value.toInt()
        ctx[9] = (value ushr 32).toInt()
    }

    /** XORs [data] with the keystream (same function encrypts and decrypts). */
    fun crypt(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var pos = 0
        while (pos < data.size) {
            val keystream = wordToByte(ctx)
            setCounter(counter() + 1) // Long wraps on overflow, like a 64-bit counter.
            val n = minOf(64, data.size - pos)
            for (j in 0 until n) {
                out[pos + j] = (data[pos + j].toInt() xor (keystream[j].toInt() and 0xff)).toByte()
            }
            pos += n
        }
        return out
    }

    private fun wordToByte(input: IntArray): ByteArray {
        val x = input.copyOf()
        repeat(rounds / 2) {
            // Column round.
            x[4] = x[4] xor Integer.rotateLeft(x[0] + x[12], 7)
            x[8] = x[8] xor Integer.rotateLeft(x[4] + x[0], 9)
            x[12] = x[12] xor Integer.rotateLeft(x[8] + x[4], 13)
            x[0] = x[0] xor Integer.rotateLeft(x[12] + x[8], 18)
            x[9] = x[9] xor Integer.rotateLeft(x[5] + x[1], 7)
            x[13] = x[13] xor Integer.rotateLeft(x[9] + x[5], 9)
            x[1] = x[1] xor Integer.rotateLeft(x[13] + x[9], 13)
            x[5] = x[5] xor Integer.rotateLeft(x[1] + x[13], 18)
            x[14] = x[14] xor Integer.rotateLeft(x[10] + x[6], 7)
            x[2] = x[2] xor Integer.rotateLeft(x[14] + x[10], 9)
            x[6] = x[6] xor Integer.rotateLeft(x[2] + x[14], 13)
            x[10] = x[10] xor Integer.rotateLeft(x[6] + x[2], 18)
            x[3] = x[3] xor Integer.rotateLeft(x[15] + x[11], 7)
            x[7] = x[7] xor Integer.rotateLeft(x[3] + x[15], 9)
            x[11] = x[11] xor Integer.rotateLeft(x[7] + x[3], 13)
            x[15] = x[15] xor Integer.rotateLeft(x[11] + x[7], 18)
            // Row round.
            x[1] = x[1] xor Integer.rotateLeft(x[0] + x[3], 7)
            x[2] = x[2] xor Integer.rotateLeft(x[1] + x[0], 9)
            x[3] = x[3] xor Integer.rotateLeft(x[2] + x[1], 13)
            x[0] = x[0] xor Integer.rotateLeft(x[3] + x[2], 18)
            x[6] = x[6] xor Integer.rotateLeft(x[5] + x[4], 7)
            x[7] = x[7] xor Integer.rotateLeft(x[6] + x[5], 9)
            x[4] = x[4] xor Integer.rotateLeft(x[7] + x[6], 13)
            x[5] = x[5] xor Integer.rotateLeft(x[4] + x[7], 18)
            x[11] = x[11] xor Integer.rotateLeft(x[10] + x[9], 7)
            x[8] = x[8] xor Integer.rotateLeft(x[11] + x[10], 9)
            x[9] = x[9] xor Integer.rotateLeft(x[8] + x[11], 13)
            x[10] = x[10] xor Integer.rotateLeft(x[9] + x[8], 18)
            x[12] = x[12] xor Integer.rotateLeft(x[15] + x[14], 7)
            x[13] = x[13] xor Integer.rotateLeft(x[12] + x[15], 9)
            x[14] = x[14] xor Integer.rotateLeft(x[13] + x[12], 13)
            x[15] = x[15] xor Integer.rotateLeft(x[14] + x[13], 18)
        }
        val out = ByteArray(64)
        for (i in 0 until 16) {
            val v = x[i] + input[i]
            out[i * 4] = v.toByte()
            out[i * 4 + 1] = (v ushr 8).toByte()
            out[i * 4 + 2] = (v ushr 16).toByte()
            out[i * 4 + 3] = (v ushr 24).toByte()
        }
        return out
    }
}
