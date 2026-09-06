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
    fun `final raster has uniform integer module geometry and intact quiet zone`() {
        val target = "https://example.org/sadaqah"
        val native = com.google.zxing.qrcode.QRCodeWriter().encode(
            target, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
                com.google.zxing.EncodeHintType.MARGIN to 4),
        )
        for (size in listOf(132, 176, 198, 264, 396, 528)) {
            val pitch = size / native.width
            val inset = (size - native.width * pitch) / 2
            val pixels = qrArgbPixels(QrCodeGenerator().generate(target), size)
            for (y in 0 until size) for (x in 0 until size) {
                val mx = (x - inset) / pitch
                val my = (y - inset) / pitch
                val dark = x >= inset && y >= inset && mx < native.width &&
                    my < native.height && native[mx, my]
                assertEquals("size=$size x=$x y=$y", dark, pixels[y * size + x] == 0xFF172331.toInt())
            }
        }
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
