package com.example.namaztime.tv.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.nio.charset.StandardCharsets

data class QrCodeMatrix(
    val size: Int,
    val darkPixels: BooleanArray,
)

class QrCodeGenerator(
    private val size: Int = 256,
    private val quietZoneModules: Int = 4,
) {
    init {
        require(size >= 64) { "QR output size is too small" }
        require(quietZoneModules >= 4) { "QR quiet zone must be at least four modules" }
    }

    fun generate(content: String): QrCodeMatrix {
        require(content.isNotEmpty()) { "QR content cannot be empty" }
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to quietZoneModules,
            ),
        )
        return QrCodeMatrix(
            size = matrix.width,
            darkPixels = BooleanArray(matrix.width * matrix.height) { index ->
                matrix[index % matrix.width, index / matrix.width]
            },
        )
    }
}
