package com.example.crypto

/**
 * Pure Kotlin implementation of FIPS 202 SHAKE-256 extendable-output function (XOF).
 * Tested and verified against standard test vectors and Python hashlib.shake_256.
 */
object Shake256 {
    private const val RATE_BYTES = 136 // 1088 bits

    private val RC = longArrayOf(
        0x0000000000000001UL.toLong(), 0x0000000000008082UL.toLong(), 0x800000000000808AUL.toLong(), 0x8000000080008000UL.toLong(),
        0x000000000000808BUL.toLong(), 0x0000000080000001UL.toLong(), 0x8000000080008081UL.toLong(), 0x8000000000008009UL.toLong(),
        0x000000000000008AUL.toLong(), 0x0000000000000088UL.toLong(), 0x0000000080008009UL.toLong(), 0x000000008000000AUL.toLong(),
        0x000000008000808BUL.toLong(), 0x800000000000008BUL.toLong(), 0x8000000000008089UL.toLong(), 0x8000000000008003UL.toLong(),
        0x8000000000008002UL.toLong(), 0x8000000000000080UL.toLong(), 0x000000000000800AUL.toLong(), 0x800000008000000AUL.toLong(),
        0x8000000080008081UL.toLong(), 0x8000000000008080UL.toLong(), 0x0000000080000001UL.toLong(), 0x8000000080008008UL.toLong()
    )

    private val RHO = arrayOf(
        intArrayOf(0, 36, 3, 41, 18),
        intArrayOf(1, 44, 10, 45, 2),
        intArrayOf(62, 6, 43, 15, 61),
        intArrayOf(28, 55, 25, 21, 56),
        intArrayOf(27, 20, 39, 8, 14)
    )

    private fun rotl64(x: Long, n: Int): Long {
        val shift = n and 63
        return if (shift == 0) x else (x shl shift) or (x ushr (64 - shift))
    }

    private fun keccakP(state: LongArray) {
        val a = Array(5) { x -> LongArray(5) { y -> state[x + 5 * y] } }
        val c = LongArray(5)
        val d = LongArray(5)
        val b = Array(5) { LongArray(5) }

        for (round in 0 until 24) {
            // Theta
            for (x in 0 until 5) {
                c[x] = a[x][0] xor a[x][1] xor a[x][2] xor a[x][3] xor a[x][4]
            }
            for (x in 0 until 5) {
                d[x] = c[(x + 4) % 5] xor rotl64(c[(x + 1) % 5], 1)
            }
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    a[x][y] = a[x][y] xor d[x]
                }
            }

            // Rho and Pi
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    b[y][(2 * x + 3 * y) % 5] = rotl64(a[x][y], RHO[x][y])
                }
            }

            // Chi
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    a[x][y] = b[x][y] xor (b[(x + 1) % 5][y].inv() and b[(x + 2) % 5][y])
                }
            }

            // Iota
            a[0][0] = a[0][0] xor RC[round]
        }

        for (x in 0 until 5) {
            for (y in 0 until 5) {
                state[x + 5 * y] = a[x][y]
            }
        }
    }

    /**
     * Squeezes [outputLength] bytes from SHAKE-256 of [input].
     */
    fun digest(input: ByteArray, outputLength: Int): ByteArray {
        val padLen = RATE_BYTES - (input.size % RATE_BYTES)
        val padded = ByteArray(input.size + padLen)
        System.arraycopy(input, 0, padded, 0, input.size)

        if (padLen == 1) {
            padded[input.size] = (0x1F xor 0x80).toByte()
        } else {
            padded[input.size] = 0x1F.toByte()
            // Intermediate bytes are 0
            padded[padded.size - 1] = 0x80.toByte()
        }

        val state = LongArray(25)
        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until (RATE_BYTES / 8)) {
                var word = 0L
                val base = offset + i * 8
                for (b in 0 until 8) {
                    word = word or ((padded[base + b].toLong() and 0xFFL) shl (b * 8))
                }
                state[i] = state[i] xor word
            }
            keccakP(state)
            offset += RATE_BYTES
        }

        val output = ByteArray(outputLength)
        var outOffset = 0
        while (outOffset < outputLength) {
            for (i in 0 until (RATE_BYTES / 8)) {
                if (outOffset >= outputLength) break
                val word = state[i]
                val bytesToCopy = minOf(8, outputLength - outOffset)
                for (b in 0 until bytesToCopy) {
                    output[outOffset++] = ((word ushr (b * 8)) and 0xFFL).toByte()
                }
            }
            if (outOffset < outputLength) {
                keccakP(state)
            }
        }

        return output
    }
}
