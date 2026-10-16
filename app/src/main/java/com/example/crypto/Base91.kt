package com.example.crypto

import java.io.ByteArrayOutputStream

/**
 * Base91 encoder and decoder matching the reference Python implementation.
 */
object Base91 {
    const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#\$%&()*+,./:;<=>?@[]^_`{|}~\""
    
    private val DECODE_MAP: IntArray = IntArray(256) { -1 }.apply {
        for (i in ALPHABET.indices) {
            val c = ALPHABET[i].code
            if (c in indices) {
                this[c] = i
            }
        }
    }

    fun encode(data: ByteArray): String {
        var b = 0
        var n = 0
        val sb = StringBuilder()
        val len = data.size

        for (i in 0 until len) {
            val byteVal = data[i].toInt() and 0xFF
            b = b or (byteVal shl n)
            n += 8
            if (n > 13) {
                var v = b and 8191
                if (v > 88) {
                    b = b ushr 13
                    n -= 13
                } else {
                    v = b and 16383
                    b = b ushr 14
                    n -= 14
                }
                sb.append(ALPHABET[v % 91])
                sb.append(ALPHABET[v / 91])
            }
        }

        if (n > 0) {
            sb.append(ALPHABET[b % 91])
            if (n > 7 || b > 90) {
                sb.append(ALPHABET[b / 91])
            }
        }

        return sb.toString()
    }

    fun decode(encoded: String): ByteArray {
        var v = -1
        var b = 0
        var n = 0
        val out = ByteArrayOutputStream()

        for (i in encoded.indices) {
            val c = encoded[i].code
            val cVal = if (c in 0 until 256) DECODE_MAP[c] else -1
            if (cVal < 0) continue

            if (v < 0) {
                v = cVal
            } else {
                v += cVal * 91
                b = b or (v shl n)
                n += if ((v and 8191) > 88) 13 else 14
                while (n > 7) {
                    out.write(b and 0xFF)
                    b = b ushr 8
                    n -= 8
                }
                v = -1
            }
        }

        if (v + 1 != 0) {
            out.write((b or (v shl n)) and 0xFF)
        }

        return out.toByteArray()
    }
}
