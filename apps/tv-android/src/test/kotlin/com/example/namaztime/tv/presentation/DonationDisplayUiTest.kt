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
        compose.onNodeWithTag(SETTINGS_DONATION_MESSAGE_FIELD_TAG).assertIsDisplayed()
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
                        title = configuration.message,
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
        listOf(DONATION_DISPLAY_IMAGE_TAG, QR_CAMPAIGN_PANEL_TAG, DONATION_DISPLAY_DETAILS_TAG)
            .forEach { tag ->
                val bounds = compose.onNodeWithTag(tag)
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                assert(bounds.left >= safe.left && bounds.right <= safe.right)
                assert(bounds.top >= safe.top && bounds.bottom <= safe.bottom)
            }
        assert(safe.left > root.left && safe.right < root.right)
        assert(safe.top > root.top && safe.bottom < root.bottom)
        compose.onNodeWithTag(QR_CODE_IMAGE_TAG).assertIsDisplayed()
        compose.onNodeWithText(configuration.transferDetails).assertIsDisplayed()
        compose.onNodeWithTag(QR_CAMPAIGN_TITLE_TAG).assertIsDisplayed()
        compose.onNodeWithText(configuration.httpsUrl).assertDoesNotExist()
        compose.onNodeWithTag(DONATION_DISPLAY_SETTINGS_TAG).assertIsFocused()
    }

    private fun configuration() = OperatorDonationConfiguration(
        httpsUrl = "https://example.org/donate",
        transferDetails = "Получатель: Местная религиозная организация\nСчёт: 0000 0000",
        message = "Поддержите нашу мечеть",
    )
}
