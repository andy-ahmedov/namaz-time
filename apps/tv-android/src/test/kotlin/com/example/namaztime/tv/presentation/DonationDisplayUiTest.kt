package com.example.namaztime.tv.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.namaztime.tv.domain.QrCodeGenerator
import com.example.namaztime.tv.repository.CUSTOM_DONATION_IMAGE_STYLE_ID
import com.example.namaztime.tv.repository.OperatorDonationConfiguration
import com.example.namaztime.tv.repository.OperatorPreferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DonationDisplayUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun donationDisplayFits720pSafeFrame() = assertDonationDisplayFits()

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun donationDisplayFits1080pDensitySafeFrame() = assertDonationDisplayFits()

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun donationDisplayFits4kDensitySafeFrame() = assertDonationDisplayFits()

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun donationSettingsShowsFiveBuiltInsAndCustomSlot() {
        compose.setContent {
            NamazTvTheme {
                SettingsShell(
                    initialDestination = SettingsDestination.DONATION,
                    onDestinationChanged = {},
                    onExit = {},
                    preferences = OperatorPreferences(donationConfiguration = configuration()),
                    onDonationConfigurationChanged = {},
                    onDonationDisplayModeChanged = { _, _ -> },
                    onPickCustomDonationImage = {},
                )
            }
        }

        DonationImageStyle.entries.forEach { style ->
            compose.onNodeWithTag("$SETTINGS_DONATION_IMAGE_TAG_PREFIX${style.id}")
                .assertIsDisplayed()
        }
        compose.onNodeWithTag(
            "$SETTINGS_DONATION_IMAGE_TAG_PREFIX$CUSTOM_DONATION_IMAGE_STYLE_ID",
        ).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_RECIPIENT_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_BANK_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_PHONE_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_COLLECTION_URL_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_SAVE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsDisplayed()
    }

    private fun assertDonationDisplayFits() {
        val configuration = configuration()
        compose.setContent {
            NamazTvTheme {
                DonationDisplayScreen(
                    configuration = configuration,
                    qrState = QrCampaignUiState(
                        id = "operator-local-donation-screen",
                        kind = "donation",
                        title = configuration.recipient,
                        subtitle = null,
                        qrCode = QrCodeGenerator().generate(configuration.httpsUrl),
                        preview = false,
                    ),
                    customAssetVersion = 0L,
                    onOpenSettings = {},
                )
            }
        }

        val root = compose.onNodeWithTag(DONATION_DISPLAY_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val safe = compose.onNodeWithTag(DONATION_DISPLAY_SAFE_CONTENT_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        listOf(
            DONATION_DISPLAY_BRAND_TAG,
            DONATION_DISPLAY_DETAILS_TAG,
            DONATION_DISPLAY_FOOTER_TAG,
            DONATION_DISPLAY_SETTINGS_VISUAL_TAG,
        )
            .forEach { tag ->
                val bounds = compose.onNodeWithTag(
                    tag,
                    useUnmergedTree = tag == DONATION_DISPLAY_SETTINGS_VISUAL_TAG,
                )
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                assert(bounds.left >= safe.left && bounds.right <= safe.right)
                assert(bounds.top >= safe.top && bounds.bottom <= safe.bottom)
            }
        assert(safe.left > root.left && safe.right < root.right)
        assert(safe.top > root.top && safe.bottom < root.bottom)
        assertEquals(root, compose.onNodeWithTag(DONATION_DISPLAY_IMAGE_TAG).getUnclippedBoundsInRoot())
        compose.onNodeWithTag(QR_CODE_IMAGE_TAG).assertIsDisplayed()
        compose.onNodeWithText(configuration.recipient).assertIsDisplayed()
        compose.onNodeWithText(configuration.bank).assertIsDisplayed()
        compose.onNodeWithText(configuration.cardNumber).assertIsDisplayed()
        compose.onNodeWithText(configuration.phone).assertIsDisplayed()
        compose.onNodeWithText(configuration.collectionUrl).assertIsDisplayed()
        compose.onNodeWithText(configuration.httpsUrl).assertDoesNotExist()
        compose.onNodeWithTag(DONATION_DISPLAY_SETTINGS_TAG).assertIsFocused()

        val scale = (root.right - root.left).value / 960f
        assertReferenceBounds(DONATION_DISPLAY_BRAND_TAG, 419f, 16.5f, 121f, 35f, scale)
        assertReferenceBounds(DONATION_DISPLAY_SETTINGS_VISUAL_TAG, 903.5f, 14.5f, 38f, 38f, scale)
        assertReferenceBounds(DONATION_DISPLAY_DETAILS_TAG, 596.5f, 59.5f, 295f, 413.5f, scale)
        assertReferenceBounds(DONATION_DISPLAY_QR_TAG, 664f, 125f, 154f, 154f, scale, tolerance = 6f)
        assertReferenceBounds(DONATION_DISPLAY_ROWS_TAG, 616.5f, 315.5f, 255f, 145f, scale, tolerance = 6f)
        assertReferenceBounds(DONATION_DISPLAY_FOOTER_TAG, 97f, 485f, 765f, 40.5f, scale)
    }

    private fun assertReferenceBounds(
        tag: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        tolerance: Float = 4f,
    ) {
        val bounds = compose.onNodeWithTag(
            tag,
            useUnmergedTree = tag == DONATION_DISPLAY_SETTINGS_VISUAL_TAG,
        ).getUnclippedBoundsInRoot()
        assertNear(bounds.left.value, left * scale, tolerance * scale, "$tag left")
        assertNear(bounds.top.value, top * scale, tolerance * scale, "$tag top")
        assertNear((bounds.right - bounds.left).value, width * scale, tolerance * scale, "$tag width")
        assertNear((bounds.bottom - bounds.top).value, height * scale, tolerance * scale, "$tag height")
    }

    private fun assertNear(actual: Float, expected: Float, tolerance: Float, label: String) {
        assert(kotlin.math.abs(actual - expected) <= tolerance) {
            "$label expected $expected±$tolerance, got $actual"
        }
    }

    private fun configuration() = OperatorDonationConfiguration(
        httpsUrl = "https://example.org/donate",
        recipient = "Местная религиозная организация",
        bank = "Тестовый банк",
        cardNumber = "2202 2036 1234 5678",
        phone = "+7 (999) 123-45-67",
        collectionUrl = "example.org/donate",
    )
}
