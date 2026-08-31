package ru.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ru.namaztime.tv.domain.QrCodeGenerator
import ru.namaztime.tv.repository.CUSTOM_DONATION_IMAGE_STYLE_ID
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import ru.namaztime.tv.repository.OperatorPreferences
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
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun donationSettingsUsesFiveBuiltInFilmstripAndSeparateCustomAction() {
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

        compose.onNodeWithTag(SETTINGS_DONATION_SELECTED_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_FILMSTRIP_TAG).assertIsDisplayed()
        compose.onNodeWithTag(
            "$SETTINGS_DONATION_IMAGE_TAG_PREFIX$CUSTOM_DONATION_IMAGE_STYLE_ID",
        ).assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_DONATION_RECIPIENT_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_BANK_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_PHONE_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag("settings-donation-collection-url").assertDoesNotExist()
        compose.onNodeWithTag(SETTINGS_DONATION_GRATITUDE_FIELD_TAG).assertIsDisplayed()

        compose.onNodeWithTag(SettingsDestination.DONATION.navigationTestTag)
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_DONATION_URL_FIELD_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_RECIPIENT_FIELD_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag(SETTINGS_DONATION_BANK_FIELD_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_PHONE_FIELD_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag(SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_GRATITUDE_FIELD_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        DonationImageStyle.entries.forEachIndexed { index, style ->
            compose.onNodeWithTag("$SETTINGS_DONATION_IMAGE_TAG_PREFIX${style.id}")
                .assertIsDisplayed()
                .assertIsFocused()
            if (index < DonationImageStyle.entries.lastIndex) {
                compose.onNodeWithTag("$SETTINGS_DONATION_IMAGE_TAG_PREFIX${style.id}")
                    .performKeyInput { pressKey(Key.DirectionRight) }
            }
        }
        compose.onNodeWithTag(
            "$SETTINGS_DONATION_IMAGE_TAG_PREFIX${DonationImageStyle.entries.last().id}",
        ).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag(SETTINGS_DONATION_PICKER_TAG).assertIsFocused()
        compose.onNodeWithTag(SETTINGS_DONATION_SAVE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun blankDonationGratitudeUsesLocalizedRussianFallback() {
        setDonationDisplayContent(configuration())

        compose.onNodeWithText(
            "Да вознаградит вас Аллах за вашу щедрость и доброе сердце",
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun blankDonationGratitudeUsesLocalizedEnglishFallback() {
        val configuration = configuration()
        compose.setContent {
            AppLanguageProvider("en") {
                NamazTvTheme {
                    DonationDisplayScreen(
                        configuration = configuration,
                        qrState = qrState(configuration),
                        status = status(),
                        customAssetVersion = 0L,
                        onOpenSettings = {},
                    )
                }
            }
        }

        compose.onNodeWithText(
            "May Allah reward you for your generosity and kind heart",
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun nonBlankDonationGratitudeReplacesFallback() {
        setDonationDisplayContent(configuration().copy(gratitudeMessage = "Спасибо за поддержку"))

        compose.onNodeWithText("Спасибо за поддержку").assertIsDisplayed()
        compose.onNodeWithText(
            "Да вознаградит вас Аллах за вашу щедрость и доброе сердце",
        ).assertDoesNotExist()
    }

    private fun assertDonationDisplayFits() {
        val configuration = configuration()
        setDonationDisplayContent(configuration)

        val root = compose.onNodeWithTag(DONATION_DISPLAY_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val safe = compose.onNodeWithTag(DONATION_DISPLAY_SAFE_CONTENT_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        listOf(
            DONATION_DISPLAY_STATUS_TAG,
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
        compose.onNodeWithTag(DONATION_DISPLAY_BRAND_TAG).assertDoesNotExist()
        compose.onNodeWithText("20 августа 2026").assertIsDisplayed()
        compose.onNodeWithText("Четверг").assertIsDisplayed()
        compose.onNodeWithText("15:23").assertIsDisplayed()
        compose.onNodeWithText("Аср").assertIsDisplayed()
        compose.onNodeWithTag(QR_CODE_IMAGE_TAG).assertIsDisplayed()
        compose.onNodeWithText(configuration.recipient).assertIsDisplayed()
        compose.onNodeWithText(configuration.bank).assertIsDisplayed()
        compose.onNodeWithText(configuration.cardNumber).assertIsDisplayed()
        compose.onNodeWithText(configuration.phone).assertIsDisplayed()
        compose.onNodeWithText("СБП:").assertIsDisplayed()
        compose.onNodeWithText("Ссылка:").assertDoesNotExist()
        compose.onAllNodesWithTag("donation-display-detail-row", useUnmergedTree = true)
            .assertCountEquals(4)
        compose.onNodeWithText(configuration.httpsUrl).assertDoesNotExist()
        compose.onNodeWithTag(DONATION_DISPLAY_SETTINGS_TAG).assertIsFocused()

        val scale = (root.right - root.left).value / 960f
        assertReferenceBounds(DONATION_DISPLAY_STATUS_TAG, 656f, 28f, 206f, 50f, scale)
        assertReferenceBounds(DONATION_DISPLAY_SETTINGS_VISUAL_TAG, 870f, 28f, 50f, 50f, scale)
        assertReferenceBounds(DONATION_DISPLAY_DETAILS_TAG, 656f, 88f, 264f, 348f, scale)
        assertReferenceBounds(DONATION_DISPLAY_QR_TAG, 715f, 156f, 146f, 146f, scale, tolerance = 6f)
        assertReferenceBounds(DONATION_DISPLAY_ROWS_TAG, 671f, 306f, 234f, 120f, scale, tolerance = 6f)
        assertReferenceBounds(DONATION_DISPLAY_FOOTER_TAG, 656f, 446f, 264f, 66f, scale)

        val railMembers = listOf(
            DONATION_DISPLAY_STATUS_TAG,
            DONATION_DISPLAY_DETAILS_TAG,
            DONATION_DISPLAY_FOOTER_TAG,
            DONATION_DISPLAY_SETTINGS_VISUAL_TAG,
        ).map { tag ->
            compose.onNodeWithTag(
                tag,
                useUnmergedTree = tag == DONATION_DISPLAY_SETTINGS_VISUAL_TAG,
            ).getUnclippedBoundsInRoot()
        }
        val railLeft = railMembers.minOf { it.left.value }
        val railRight = railMembers.maxOf { it.right.value }
        val rootWidth = (root.right - root.left).value
        assert(railLeft >= root.left.value + rootWidth * 0.68f) {
            "donation rail must leave at least 68% of the viewport free on the left"
        }
        assert(railRight - railLeft <= rootWidth * 0.30f) {
            "donation rail must occupy no more than 30% of the viewport width"
        }

        val statusBounds = compose.onNodeWithTag(DONATION_DISPLAY_STATUS_TAG)
            .getUnclippedBoundsInRoot()
        val settingsBounds = compose.onNodeWithTag(
            DONATION_DISPLAY_SETTINGS_VISUAL_TAG,
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        assertNear(
            (statusBounds.bottom - statusBounds.top).value,
            (settingsBounds.bottom - settingsBounds.top).value,
            1f * scale,
            "top block heights",
        )
    }

    private fun setDonationDisplayContent(configuration: OperatorDonationConfiguration) {
        compose.setContent {
            NamazTvTheme {
                DonationDisplayScreen(
                    configuration = configuration,
                    qrState = qrState(configuration),
                    status = status(),
                    customAssetVersion = 0L,
                    onOpenSettings = {},
                )
            }
        }
    }

    private fun qrState(configuration: OperatorDonationConfiguration) = QrCampaignUiState(
        id = "operator-local-donation-screen",
        kind = "donation",
        title = configuration.recipient,
        subtitle = null,
        qrCode = QrCodeGenerator().generate(configuration.httpsUrl),
        preview = false,
    )

    private fun status() = DonationStatusUiState(
        dateLabel = "20 августа 2026",
        weekdayLabel = "Четверг",
        mosqueLocalTime = "15:23",
        currentPrayerLabel = "Аср",
    )

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
    )
}
