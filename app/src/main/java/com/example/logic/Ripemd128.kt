package com.example.logic

import org.bouncycastle.crypto.digests.RIPEMD128Digest

/**
 * RIPEMD-128 hash using BouncyCastle.
 */
object Ripemd128 {

    fun hash(input: ByteArray): ByteArray {
        val digest = RIPEMD128Digest()
        digest.update(input, 0, input.size)
        val output = ByteArray(16)
        digest.doFinal(output, 0)
        return output
    }
}
