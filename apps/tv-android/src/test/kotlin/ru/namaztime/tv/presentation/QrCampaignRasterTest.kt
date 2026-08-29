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
    fun `high-correction TV rasters decode with the centered brand badge area obscured`() {
        val target = "https://example.org/sadaqah"
        val matrix = QrCodeGenerator().generate(target)

        listOf(128, 192, 360, 540).forEach { physicalPixels ->
            val pixels = qrArgbPixels(matrix, physicalPixels)
            val badgeSize = (physicalPixels * REFERENCE_QR_BADGE_FRACTION).toInt()
            val badgeStart = (physicalPixels - badgeSize) / 2
            repeat(badgeSize) { badgeY ->
                repeat(badgeSize) { badgeX ->
                    pixels[(badgeStart + badgeY) * physicalPixels + badgeStart + badgeX] =
                        0xFF101A28.toInt()
                }
            }
            val decoded = QRCodeReader().decode(
                BinaryBitmap(
                    HybridBinarizer(
                        RGBLuminanceSource(physicalPixels, physicalPixels, pixels),
                    ),
                ),
            )
            assertEquals("size=$physicalPixels", target, decoded.text)
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
