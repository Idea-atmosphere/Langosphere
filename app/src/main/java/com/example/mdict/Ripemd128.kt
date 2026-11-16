package com.example.mdict

/**
 * RIPEMD-128, pure Kotlin port of ripemd128.py (mdict-utils).
 * The JDK has no RIPEMD-128, and MDict needs it to derive block keys.
 *
 * Test vector (from the Python source):
 * ripemd128("The quick brown fox jumps over the lazy dog") =
 * 3fa9b57f053c053fbe2735b2380db596
 */
object Ripemd128 {

    private fun f(j: Int, x: Int, y: Int, z: Int): Int = when {
        j < 16 -> x xor y xor z
        j < 32 -> (x and y) or (z and x.inv())
        j < 48 -> (x or y.inv()) xor z
        else -> (x and z) or (y and z.inv())
    }

    private fun k(j: Int): Int = when {
        j < 16 -> 0x00000000
        j < 32 -> 0x5a827999
        j < 48 -> 0x6ed9eba1
        else -> 0x8f1bbcdc.toInt()
    }

    private fun kp(j: Int): Int = when {
        j < 16 -> 0x50a28be6.toInt()
        j < 32 -> 0x5c4dd124
        j < 48 -> 0x6d703ef3
        else -> 0x00000000
    }

    private val R = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2
    )
    private val Rp = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14
    )
    private val S = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12
    )
    private val Sp = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8
    )

    private fun rol(x: Int, s: Int): Int = (x shl s) or (x ushr (32 - s))

    fun digest(message: ByteArray): ByteArray {
        // Pad: 0x80, zeros until length == 56 (mod 64), then LE 64-bit bit length.
        // NOTE: Python's % is floored; Kotlin's % keeps the dividend's sign,
        // so emulate the Python formula with a floored modulo.
        val origLen = message.size
        val padLen = 64 - (((origLen - 56) % 64 + 64) % 64)
        val padded = ByteArray(origLen + padLen + 8)
        message.copyInto(padded)
        padded[origLen] = 0x80.toByte()
        val bitLen = origLen.toLong() * 8
        for (i in 0 until 8) {
            padded[origLen + padLen + i] = (bitLen ushr (8 * i)).toByte()
        }

        var h0 = 0x67452301
        var h1 = 0xefcdab89.toInt()
        var h2 = 0x98badcfe.toInt()
        var h3 = 0x10325476

        val x = IntArray(16)
        var off = 0
        while (off < padded.size) {
            for (i in 0 until 16) {
                val p = off + i * 4
                x[i] = (padded[p].toInt() and 0xff) or
                    ((padded[p + 1].toInt() and 0xff) shl 8) or
                    ((padded[p + 2].toInt() and 0xff) shl 16) or
                    ((padded[p + 3].toInt() and 0xff) shl 24)
            }
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var ap = h0
            var bp = h1
            var cp = h2
            var dp = h3
            for (j in 0 until 64) {
                var t = rol(a + f(j, b, c, d) + x[R[j]] + k(j), S[j])
                val na = d
                d = c
                c = b
                b = t
                a = na
                t = rol(ap + f(63 - j, bp, cp, dp) + x[Rp[j]] + kp(j), Sp[j])
                val nap = dp
                dp = cp
                cp = bp
                bp = t
                ap = nap
            }
            val t = h1 + c + dp
            h1 = h2 + d + ap
            h2 = h3 + a + bp
            h3 = h0 + b + cp
            h0 = t
            off += 64
        }

        // Little-endian output.
        val out = ByteArray(16)
        val hs = intArrayOf(h0, h1, h2, h3)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                out[i * 4 + j] = (hs[i] ushr (8 * j)).toByte()
            }
        }
        return out
    }
}
