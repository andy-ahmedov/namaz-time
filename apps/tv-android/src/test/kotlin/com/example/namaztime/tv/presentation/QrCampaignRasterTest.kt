package com.example.namaztime.tv.presentation

import com.example.namaztime.tv.domain.QrCodeGenerator
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCampaignRasterTest {
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
}
