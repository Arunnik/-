package com.example

import com.example.crypto.Base91
import com.example.crypto.PackAndHideEngine
import com.example.crypto.PayloadType
import com.example.crypto.Shake256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackAndHideTest {

    @Test
    fun testShake256Stream() {
        val input = "pass123".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val stream = Shake256.digest(input, 32)
        val hex = stream.joinToString("") { "%02x".format(it) }
        assertEquals("fa60777b8fd36d576548bbdb8a62eee9a6ef6128cbf800b20c654fc320ad775e", hex)
    }

    @Test
    fun testPythonVectorsUnpack() {
        // Non-salted python vector
        val res1 = PackAndHideEngine.unpackAndRead("8qo?F=1`(&BznlR8", "pass123")
        assertEquals("Привет, мир!", res1.text)
        assertEquals(PayloadType.CP1251, res1.payloadType)

        // Salted python vector with salt "1234"
        val res2 = PackAndHideEngine.unpackAndRead("Sw_,\$kvf`o@MSSiWCqx@9", "pass123")
        assertEquals("Привет, мир!", res2.text)
        assertTrue(res2.isSalted)
        assertArrayEquals("1234".toByteArray(Charsets.US_ASCII), res2.salt)

        // No key vector
        val res3 = PackAndHideEngine.unpackAndRead(".Y]IX<oCi$4t}8!q", null)
        assertEquals("Hello no key", res3.text)
    }

    @Test
    fun testRoundTripAllTypes() {
        // UTF8 text with emoji
        val emojiText = "Привет мир! 🔐 Rocket 🚀 1234"
        val packEmoji = PackAndHideEngine.packAndHide(emojiText, "secretKey", salted = true)
        val unpackEmoji = PackAndHideEngine.unpackAndRead(packEmoji.token, "secretKey")
        assertEquals(emojiText, unpackEmoji.text)

        // JSON text
        val jsonText = "{\"name\":\"Alice\",\"age\":30,\"roles\":[\"admin\",\"user\"]}"
        val packJson = PackAndHideEngine.packAndHide(jsonText, "jsonPass", salted = false, preferredType = PayloadType.JSON)
        val unpackJson = PackAndHideEngine.unpackAndRead(packJson.token, "jsonPass")
        assertTrue(unpackJson.text.contains("Alice"))
        assertEquals(PayloadType.JSON, unpackJson.payloadType)

        // Uncompressed vs Compressed
        val largeText = "Repeat this text many times! ".repeat(50)
        val packLarge = PackAndHideEngine.packAndHide(largeText, "key999", salted = true)
        assertTrue(packLarge.isCompressed)
        val unpackLarge = PackAndHideEngine.unpackAndRead(packLarge.token, "key999")
        assertEquals(largeText, unpackLarge.text)
    }

    @Test
    fun testBase91RoundTrip() {
        val data = "The quick brown fox jumps over the lazy dog 1234567890!@#$%^&*()_+".toByteArray(Charsets.UTF_8)
        val encoded = Base91.encode(data)
        val decoded = Base91.decode(encoded)
        assertArrayEquals(data, decoded)
    }
}
