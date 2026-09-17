package ru.namaztime.tv.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.namaztime.tv.domain.QrCodeGenerator
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RenderedQrTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun renderedPublicQr720p() = verifyScreens()

    @Test @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun renderedPublicQr1080p() = verifyScreens()

    @Test @Config(qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun renderedPublicQr4k() = verifyScreens()

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun insufficientSpaceCannotCrashTheSchedule() {
        compose.setContent {
            NamazTvTheme {
                ReferenceQrCode(QrCampaignUiState("fixture", "donation", "Synthetic", null,
                    QrCodeGenerator().generate(PAYLOADS.last()), false), 10.dp)
            }
        }
        compose.onNodeWithTag("qr-insufficient-space").assertExists()
        compose.onNodeWithTag(QR_CODE_IMAGE_TAG).assertDoesNotExist()
    }

    private fun verifyScreens() {
        val payload = mutableStateOf(PAYLOADS.first())
        val screen = mutableStateOf("standard")
        var view: android.view.View? = null
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            NamazTvTheme {
                Box(Modifier.fillMaxSize()) {
                    val campaign = QrCampaignUiState("fixture", "donation", "Synthetic", null,
                        QrCodeGenerator().generate(payload.value), screen.value == "preview")
                    when (screen.value) {
                        "standard" -> MainPrayerDisplay(state(campaign), {}, requestInitialFocus = false)
                        "compact" -> CompactPrayerDisplay(state(campaign), {}, requestInitialFocus = false)
                        "donation" -> DonationDisplayScreen(
                            OperatorDonationConfiguration(payload.value, recipient = "Synthetic"), campaign,
                            DonationStatusUiState("19 August 2026", "Wednesday", "15:23", "Asr"),
                            0L, {}, requestInitialFocus = false,
                        )
                        // Force parent constraints smaller than the nominal QR size.
                        "preview" -> ReferenceQrCode(campaign, 200.dp, Modifier.size(174.dp))
                    }
                }
            }
        }
        for (surface in listOf("standard", "compact", "donation", "preview")) for (target in PAYLOADS) {
            compose.runOnIdle { screen.value = surface; payload.value = target }
            val bounds = compose.onNodeWithTag(QR_CODE_IMAGE_TAG).fetchSemanticsNode().boundsInRoot
            // Capture the whole composed screen, including any sibling overlays, then sample the QR.
            lateinit var image: android.graphics.Bitmap
            compose.runOnIdle {
                val root = requireNotNull(view)
                image = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(image))
            }
            val width = bounds.width.roundToInt()
            val pixels = IntArray(width * width)
            image.getPixels(pixels, 0, width, bounds.left.roundToInt(), bounds.top.roundToInt(), width, width)
            val decoded = try {
                QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, width, pixels))))
            } catch (error: com.google.zxing.ReaderException) {
                throw AssertionError("$surface width=$width payloadLength=${target.length}", error)
            }
            assertEquals("$surface $width", target, decoded.text)
            val matrix = QrCodeGenerator().generate(target)
            val expected = qrArgbPixels(matrix, width)
            val corner = kotlin.math.ceil(qrCornerRadiusPixels(matrix, width)).toInt() + 1
            for (y in 0 until width) for (x in 0 until width) {
                // Rounded corners lie strictly outside the symbol and its side clearances.
                val outerCorner = (x < corner || x >= width - corner) &&
                    (y < corner || y >= width - corner)
                if (!outerCorner) assertEquals("unchanged modules/side margin: $surface $width $x,$y",
                    expected[y * width + x], pixels[y * width + x])
            }
            org.junit.Assert.assertNotEquals("outer paper corner is rounded: $surface",
                expected[0], pixels[0])
        }
    }

    private fun state(campaign: QrCampaignUiState) = PrayerDisplayUiState(
        "Synthetic mosque", "Example city", "19 August 2026", "Wednesday", "15:23:00",
        "Dhuhr", "Asr", "Adhan", "15:47", "00:24:00", null,
        sourceLabel = "Synthetic", sourceDescription = "Fixture", sourceRequiresAttention = false,
        campaign = campaign,
        rows = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").map {
            PrayerDisplayRow(it, it, "12:00", if (it == "sunrise") null else "12:05")
        },
    )

    private companion object {
        val PAYLOADS = listOf(
            ru.namaztime.tv.repository.DEFAULT_QR_HTTPS_URL,
            "https://example.org/sadaqah",
            "https://example.org/mosques/community/donate?campaign=renovation-2026&lang=ru",
            "https://example.org/donate?campaign=mosque-renovation-2026&purpose=community-hall&reference=tv-display&return=https%3A%2F%2Fexample.org%2Fthank-you&language=ru",
        )
    }
}
