package ru.namaztime.tv.presentation

import ru.namaztime.tv.domain.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCampaignRasterTest {
    @Test
    fun `TV raster uses dark navy modules rather than pure black`() {
        val matrix = QrCodeGenerator().generate("https://example.org/sadaqah")
        val pixels = qrArgbPixels(matrix, 192)

        assertFalse(pixels.any { pixel -> pixel == 0xFF000000.toInt() })
    }

    @Test
    fun `nearest-neighbor TV rasters decode at representative physical sizes`() {
        val target = "https://example.org/mosque"
        val matrix = QrCodeGenerator().generate(target)

        listOf(128, 192, 360, 540).forEach { physicalPixels ->
            val decoded = QRCodeReader().decode(
                BinaryBitmap(
                    HybridBinarizer(
                        RGBLuminanceSource(
                            physicalPixels,
                            physicalPixels,
                            qrArgbPixels(matrix, physicalPixels),
                        ),
                    ),
                ),
            )
            assertEquals("size=$physicalPixels", target, decoded.text)
        }
    }

    @Test
    fun `final raster uses all available space with intact four module side margins`() {
        val target = "https://example.org/sadaqah"
        val native = com.google.zxing.qrcode.QRCodeWriter().encode(
            target, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                com.google.zxing.EncodeHintType.MARGIN to 4),
        )
        for (size in listOf(132, 176, 198, 264, 396, 528)) {
            val widths = (0 until native.width).map { module ->
                (0 until size).count { it * native.width / size == module }
            }
            assertTrue(widths.max() - widths.min() <= 1)
            val pixels = qrArgbPixels(QrCodeGenerator().generate(target), size)
            for (y in 0 until size) for (x in 0 until size) {
                val pitch = size / native.width
                val inset = (size - native.width * pitch) / 2
                val mx = if (pitch < 4) (x - inset) / pitch else x * native.width / size
                val my = if (pitch < 4) (y - inset) / pitch else y * native.width / size
                val inside = pitch >= 4 || (x >= inset && y >= inset &&
                    x < inset + native.width * pitch && y < inset + native.width * pitch)
                val dark = inside && native[mx, my]
                assertEquals("size=$size x=$x y=$y", dark, pixels[y * size + x] == 0xFF172331.toInt())
            }
        }
    }

    @Test
    fun `scaling reclaims leftover padding and rounding stays outside data`() {
        val matrix = QrCodeGenerator().generate("https://example.org/sadaqah")
        val size = matrix.moduleCount * 5 - 1
        val pixels = qrArgbPixels(matrix, size)
        val firstDark = pixels.indices.filter { pixels[it] == 0xFF172331.toInt() }
            .minOf { it % size }
        val oldPitch = size / matrix.moduleCount
        val oldMargin = (size - matrix.moduleCount * oldPitch) / 2 + 4 * oldPitch
        assertTrue(firstDark < oldMargin)
        assertTrue(qrCornerRadiusPixels(matrix, size) <= firstDark)
        assertTrue(qrCornerRadiusPixels(matrix, size) <= size * 0.20f)
    }

    @Test
    fun `reference QR frame declares four symmetric equal-arm corners`() {
        assertEquals(4, REFERENCE_QR_CORNERS.size)
        assertEquals(
            setOf(1 to 1, -1 to 1, 1 to -1, -1 to -1),
            REFERENCE_QR_CORNERS
                .map { it.horizontalDirection to it.verticalDirection }
                .toSet(),
        )
        assertTrue(REFERENCE_QR_CORNER_ARM_FRACTION in 0.12f..0.14f)
    }
}
