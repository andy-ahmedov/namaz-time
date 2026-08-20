package com.example.namaztime.tv.domain

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeGeneratorTest {
    private val generator = QrCodeGenerator(size = 256, quietZoneModules = 4)

    @Test
    fun `generated fixture decodes to the exact UTF-8 target`() {
        val target = "https://example.org/расписание?месяц=август"
        val qr = generator.generate(target)
        val pixels = IntArray(qr.size * qr.size) { index ->
            if (qr.darkPixels[index]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val decoded = QRCodeReader().decode(
            BinaryBitmap(
                HybridBinarizer(RGBLuminanceSource(qr.size, qr.size, pixels)),
            ),
            mapOf(DecodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name()),
        )

        assertEquals(target, decoded.text)
    }

    @Test
    fun `output is deterministic and retains a white quiet zone`() {
        val target = "https://example.org/mosque"
        val first = generator.generate(target)
        val second = generator.generate(target)

        assertArrayEquals(first.darkPixels, second.darkPixels)
        val darkCoordinates = buildList {
            first.darkPixels.forEachIndexed { index, dark ->
                if (dark) add(index % first.size to index / first.size)
            }
        }
        assertTrue(darkCoordinates.minOf { it.first } >= 4)
        assertTrue(darkCoordinates.minOf { it.second } >= 4)
        assertTrue(darkCoordinates.maxOf { it.first } <= first.size - 5)
        assertTrue(darkCoordinates.maxOf { it.second } <= first.size - 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty target is rejected`() {
        generator.generate("")
    }
}
