package com.example.crypto

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

enum class PayloadType(val code: Int, val displayName: String) {
    CP1251(0, "Windows-1251"),
    UTF8(1, "UTF-8"),
    BYTES(2, "Двоичные данные (Bytes)"),
    JSON(3, "JSON");

    companion object {
        fun fromCode(code: Int): PayloadType = entries.find { it.code == code } ?: UTF8
    }
}

data class PackResult(
    val token: String,
    val payloadType: PayloadType,
    val isCompressed: Boolean,
    val isSalted: Boolean,
    val salt: ByteArray?,
    val rawSize: Int,
    val payloadSize: Int,
    val crc4: Int
)

data class UnpackResult(
    val text: String,
    val payloadType: PayloadType,
    val isCompressed: Boolean,
    val isSalted: Boolean,
    val salt: ByteArray?,
    val rawBytes: ByteArray,
    val crc4: Int,
    val isJsonValid: Boolean = false
)

object PackAndHideEngine {
    const val TYPE_CP1251 = 0
    const val TYPE_UTF8 = 1
    const val TYPE_BYTES = 2
    const val TYPE_JSON = 3

    const val FLAG_COMPRESSED = 4
    const val FLAG_SALTED = 8

    private val CP1251_CHARSET = try {
        Charset.forName("windows-1251")
    } catch (_: Exception) {
        Charsets.UTF_8
    }

    private val secureRandom = SecureRandom()

    fun deflateRaw(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            baos.write(buffer, 0, count)
        }
        deflater.end()
        return baos.toByteArray()
    }

    fun inflateRaw(compressed: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(compressed)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            }
            baos.write(buffer, 0, count)
        }
        inflater.end()
        return baos.toByteArray()
    }

    fun xorCrypt(data: ByteArray, key: ByteArray, salt: ByteArray = ByteArray(0)): ByteArray {
        if (data.isEmpty() || key.isEmpty()) return data.copyOf()
        val shakeInput = ByteArray(key.size + 1 + salt.size)
        System.arraycopy(key, 0, shakeInput, 0, key.size)
        shakeInput[key.size] = 0.toByte()
        if (salt.isNotEmpty()) {
            System.arraycopy(salt, 0, shakeInput, key.size + 1, salt.size)
        }

        val stream = Shake256.digest(shakeInput, data.size)
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor stream[i].toInt()).toByte()
        }
        return result
    }

    fun serializePayload(data: Any, preferredType: PayloadType? = null): Pair<ByteArray, Int> {
        when {
            preferredType == PayloadType.JSON && data is String -> {
                val bytes = data.toByteArray(Charsets.UTF_8)
                return Pair(bytes, TYPE_JSON)
            }
            preferredType == PayloadType.BYTES && data is ByteArray -> {
                return Pair(data, TYPE_BYTES)
            }
            preferredType == PayloadType.CP1251 && data is String -> {
                return Pair(data.toByteArray(CP1251_CHARSET), TYPE_CP1251)
            }
            preferredType == PayloadType.UTF8 && data is String -> {
                return Pair(data.toByteArray(Charsets.UTF_8), TYPE_UTF8)
            }
            data is String -> {
                // Auto-detect JSON if string starts with { or [
                val trimmed = data.trim()
                if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]"))
                ) {
                    try {
                        org.json.JSONObject(trimmed)
                        return Pair(trimmed.toByteArray(Charsets.UTF_8), TYPE_JSON)
                    } catch (_: Exception) {
                        try {
                            org.json.JSONArray(trimmed)
                            return Pair(trimmed.toByteArray(Charsets.UTF_8), TYPE_JSON)
                        } catch (_: Exception) {
                            // not json, continue to text
                        }
                    }
                }
                // Try CP1251 encoder
                val encoder = CP1251_CHARSET.newEncoder()
                return if (encoder.canEncode(data)) {
                    Pair(data.toByteArray(CP1251_CHARSET), TYPE_CP1251)
                } else {
                    Pair(data.toByteArray(Charsets.UTF_8), TYPE_UTF8)
                }
            }
            data is ByteArray -> {
                return Pair(data, TYPE_BYTES)
            }
            else -> {
                val str = data.toString()
                return Pair(str.toByteArray(Charsets.UTF_8), TYPE_UTF8)
            }
        }
    }

    fun deserializePayload(raw: ByteArray, dtype: Int): Pair<String, Boolean> {
        return when (dtype) {
            TYPE_CP1251 -> Pair(String(raw, CP1251_CHARSET), false)
            TYPE_UTF8 -> Pair(String(raw, Charsets.UTF_8), false)
            TYPE_BYTES -> {
                val hex = raw.joinToString("") { "%02x".format(it) }
                Pair(hex, false)
            }
            TYPE_JSON -> {
                val str = String(raw, Charsets.UTF_8)
                var isValid = false
                try {
                    val trimmed = str.trim()
                    if (trimmed.startsWith("{")) {
                        val obj = org.json.JSONObject(trimmed)
                        Pair(obj.toString(2), true)
                    } else if (trimmed.startsWith("[")) {
                        val arr = org.json.JSONArray(trimmed)
                        Pair(arr.toString(2), true)
                    } else {
                        Pair(str, false)
                    }
                } catch (_: Exception) {
                    Pair(str, false)
                }
            }
            else -> throw IllegalArgumentException("Неизвестный тип данных payload: $dtype")
        }
    }

    fun packAndHide(
        data: Any,
        key: String? = null,
        salted: Boolean = false,
        customSalt: ByteArray? = null,
        preferredType: PayloadType? = null
    ): PackResult {
        val (raw, dtype) = serializePayload(data, preferredType)
        val compressed = try {
            deflateRaw(raw)
        } catch (_: Exception) {
            raw
        }

        var flags: Int
        val payload: ByteArray
        val isCompressed = compressed.size < raw.size

        if (isCompressed) {
            payload = compressed
            flags = dtype or FLAG_COMPRESSED
        } else {
            payload = raw
            flags = dtype
        }

        val keyBytes = if (!key.isNullOrEmpty()) key.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val isSalted = salted && keyBytes.isNotEmpty()
        if (isSalted) {
            flags = flags or FLAG_SALTED
        }

        val crc = CRC32()
        crc.update(raw)
        val crcTag = (crc.value.toInt() and 0x0F) shl 4
        flags = flags or crcTag

        var finalSalt: ByteArray? = null
        val obfuscated: ByteArray

        if (keyBytes.isNotEmpty()) {
            if (isSalted) {
                finalSalt = customSalt ?: ByteArray(4).also { secureRandom.nextBytes(it) }
                if (finalSalt.size != 4) {
                    finalSalt = finalSalt.copyOf(4)
                }
                val firstMask = Shake256.digest(keyBytes + byteArrayOf(0.toByte()), 1)[0].toInt() and 0xFF
                val head = byteArrayOf((flags xor firstMask).toByte())
                val encrypted = xorCrypt(payload, keyBytes, finalSalt)

                val combined = ByteArray(1 + 4 + encrypted.size)
                combined[0] = head[0]
                System.arraycopy(finalSalt, 0, combined, 1, 4)
                System.arraycopy(encrypted, 0, combined, 5, encrypted.size)
                obfuscated = combined
            } else {
                val toEncrypt = ByteArray(1 + payload.size)
                toEncrypt[0] = flags.toByte()
                System.arraycopy(payload, 0, toEncrypt, 1, payload.size)
                obfuscated = xorCrypt(toEncrypt, keyBytes, ByteArray(0))
            }
        } else {
            val combined = ByteArray(1 + payload.size)
            combined[0] = flags.toByte()
            System.arraycopy(payload, 0, combined, 1, payload.size)
            obfuscated = combined
        }

        val token = Base91.encode(obfuscated)
        return PackResult(
            token = token,
            payloadType = PayloadType.fromCode(dtype),
            isCompressed = isCompressed,
            isSalted = isSalted,
            salt = finalSalt,
            rawSize = raw.size,
            payloadSize = payload.size,
            crc4 = (flags ushr 4) and 0x0F
        )
    }

    fun unpackAndRead(
        token: String,
        key: String? = null
    ): UnpackResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            throw IllegalArgumentException("Токен пуст. Вставьте Base91 строку.")
        }

        val obfuscated = Base91.decode(cleanToken)
        if (obfuscated.isEmpty()) {
            throw IllegalArgumentException("Некорректный Base91 токен (декодирование вернуло 0 байт)")
        }

        val keyBytes = if (!key.isNullOrEmpty()) key.toByteArray(Charsets.UTF_8) else ByteArray(0)
        val flags: Int
        val payload: ByteArray
        var extractedSalt: ByteArray? = null

        if (keyBytes.isNotEmpty()) {
            val firstMask = Shake256.digest(keyBytes + byteArrayOf(0.toByte()), 1)[0].toInt() and 0xFF
            val headByte = obfuscated[0].toInt() and 0xFF
            flags = headByte xor firstMask

            if ((flags and FLAG_SALTED) != 0) {
                if (obfuscated.size < 5) {
                    throw IllegalArgumentException("Повреждённый токен с солью (длина меньше 5 байт)")
                }
                extractedSalt = obfuscated.copyOfRange(1, 5)
                val encPayload = obfuscated.copyOfRange(5, obfuscated.size)
                payload = xorCrypt(encPayload, keyBytes, extractedSalt)
            } else {
                val decrypted = xorCrypt(obfuscated, keyBytes, ByteArray(0))
                payload = decrypted.copyOfRange(1, decrypted.size)
            }
        } else {
            flags = obfuscated[0].toInt() and 0xFF
            payload = obfuscated.copyOfRange(1, obfuscated.size)
        }

        val isCompressed = (flags and FLAG_COMPRESSED) != 0
        val isSalted = (flags and FLAG_SALTED) != 0

        val raw: ByteArray = try {
            if (isCompressed) {
                inflateRaw(payload)
            } else {
                payload
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Неверный ключ или повреждённые сжатые данные Deflate (${e.message ?: "ошибка"})", e)
        }

        val crc = CRC32()
        crc.update(raw)
        val expectedCrc = (crc.value.toInt() and 0x0F)
        val actualCrc = (flags ushr 4) and 0x0F

        if (expectedCrc != actualCrc) {
            throw IllegalArgumentException("Несовпадение контрольной суммы CRC4 ($actualCrc != $expectedCrc). Вероятно указан неверный пароль/ключ.")
        }

        val dtype = flags and 3
        val (textResult, isJson) = deserializePayload(raw, dtype)

        return UnpackResult(
            text = textResult,
            payloadType = PayloadType.fromCode(dtype),
            isCompressed = isCompressed,
            isSalted = isSalted,
            salt = extractedSalt,
            rawBytes = raw,
            crc4 = actualCrc,
            isJsonValid = isJson
        )
    }
}
