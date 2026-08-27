package com.example.namaztime.tv.presentation

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.namaztime.tv.repository.LocalIqamahRule
import com.example.namaztime.tv.repository.LocalJumuahSession
import com.example.namaztime.tv.repository.LocalPrayerDay
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.LocalSnapshotDiagnostics
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OPERATOR_IQAMAH_PRAYER_IDS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SettingsShellUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun dpadReachesEveryNavigationItemAndSelectedPageAction() {
        compose.setContent {
            SettingsShell(
                initialDestination = SettingsDestination.initial,
                onDestinationChanged = {},
                onExit = {},
            )
        }

        SettingsDestination.entries.forEachIndexed { index, destination ->
            compose.onNodeWithTag(destination.navigationTestTag)
                .assertIsFocused()
                .assertIsSelected()
            if (index < SettingsDestination.entries.lastIndex) {
                compose.onNodeWithTag(destination.navigationTestTag).performKeyInput {
                    pressKey(Key.DirectionDown)
                }
            }
        }

        compose.onNodeWithTag(SettingsDestination.entries.last().navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).assertIsFocused()
        compose.onNodeWithTag(SETTINGS_PAGE_ACTION_TEST_TAG).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        compose.onNodeWithTag(SettingsDestination.entries.last().navigationTestTag).assertIsFocused()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun settingsPanelsFit720pSafeFrame() = assertSettingsPanelsFit()

    @Test
    @Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
    fun settingsPanelsFit1080pDensitySafeFrame() = assertSettingsPanelsFit()

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun settingsPanelsFit4kDensitySafeFrame() = assertSettingsPanelsFit()

    @Test
    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun everySectionShowsRealPilotStateAndLocalActionsAreEffective() {
        var shiftChoice: Boolean? = null
        var languageChoice: String? = null
        var systemSettingsOpenCount = 0
        compose.setContent {
            SettingsShell(
                initialDestination = SettingsDestination.initial,
                onDestinationChanged = {},
                onExit = {},
                schedule = pilotSchedule(),
                preferences = OperatorPreferences(),
                appVersion = "0.4.0-pilot-local",
                pilotLocalRuntime = true,
                onScreenRetentionShiftChanged = { shiftChoice = it },
                onLanguageChanged = { languageChoice = it },
                onOpenSystemSettings = { systemSettingsOpenCount += 1 },
            )
        }

        compose.onNodeWithText("Вторая Соборная Мечеть").assertIsDisplayed()
        compose.onNodeWithText("Ульяновск").assertIsDisplayed()

        moveDownFrom(SettingsDestination.MOSQUE)
        compose.onNodeWithText("Региональное духовное управление мусульман Ульяновской области").assertIsDisplayed()
        compose.onNodeWithText("Одобрено").assertIsDisplayed()

        moveDownFrom(SettingsDestination.SOURCE)
        OPERATOR_IQAMAH_PRAYER_IDS.forEach { prayerId ->
            compose.onNodeWithTag("$SETTINGS_IQAMAH_FIELD_TAG_PREFIX$prayerId").assertIsDisplayed()
        }
        compose.onNodeWithText("Пятничный намаз").assertDoesNotExist()
        compose.onNodeWithText("Зухр в пятницу").assertDoesNotExist()

        moveDownFrom(SettingsDestination.IQAMAH)
        compose.onNodeWithTag("${SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX}golden_dusk")
            .assertIsDisplayed()
        compose.onNodeWithTag(SettingsDestination.APPEARANCE.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
            repeat(3) { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag(SETTINGS_LOCAL_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag(SettingsDestination.APPEARANCE.navigationTestTag).assertIsFocused()
        assertEquals(false, shiftChoice)

        moveDownFrom(SettingsDestination.APPEARANCE)
        compose.onNodeWithTag(SETTINGS_QR_URL_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_QR_TITLE_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_QR_MESSAGE_FIELD_TAG).assertIsDisplayed()

        moveDownFrom(SettingsDestination.CAMPAIGNS)
        compose.onNodeWithTag(SETTINGS_DONATION_URL_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_RECIPIENT_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_BANK_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_CARD_NUMBER_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_PHONE_FIELD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_DONATION_COLLECTION_URL_FIELD_TAG).assertIsDisplayed()
        DonationImageStyle.entries.forEach { style ->
            compose.onNodeWithTag("$SETTINGS_DONATION_IMAGE_TAG_PREFIX${style.id}")
                .assertIsDisplayed()
        }

        moveDownFrom(SettingsDestination.DONATION)
        compose.onNodeWithText("Русский").assertIsDisplayed()
        invokeLocalActionFrom(SettingsDestination.LANGUAGE)
        assertEquals("en", languageChoice)

        moveDownFrom(SettingsDestination.LANGUAGE)
        compose.onNodeWithText("Автозапуск: не настроен").assertIsDisplayed()
        invokeLocalActionFrom(SettingsDestination.KIOSK)
        assertEquals(1, systemSettingsOpenCount)

        moveDownFrom(SettingsDestination.KIOSK)
        compose.onNodeWithText("ulyanovsk-second-cathedral-2026-pilot-local-v1").assertIsDisplayed()
        compose.onNodeWithText("0.4.0-pilot-local").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun moveDownFrom(destination: SettingsDestination) {
        compose.onNodeWithTag(destination.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionDown)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun invokeLocalActionFrom(destination: SettingsDestination) {
        compose.onNodeWithTag(destination.navigationTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithTag(SETTINGS_LOCAL_ACTION_TEST_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
            .performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag(destination.navigationTestTag).assertIsFocused()
    }

    private fun assertSettingsPanelsFit() {
        compose.setContent {
            NamazTvTheme {
                SettingsShell(
                    initialDestination = SettingsDestination.initial,
                    onDestinationChanged = {},
                    onExit = {},
                )
            }
        }

        val shell = compose.onNodeWithTag(SETTINGS_SHELL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        val rootHeight = root.bottom - root.top
        assert(shell.left - root.left >= rootWidth * 0.04f)
        assert(root.right - shell.right >= rootWidth * 0.04f)
        assert(shell.top - root.top >= rootHeight * 0.04f)
        assert(root.bottom - shell.bottom >= rootHeight * 0.04f)
        val navigation = compose.onNodeWithTag(SETTINGS_NAVIGATION_PANEL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val content = compose.onNodeWithTag(SETTINGS_CONTENT_PANEL_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        listOf(navigation, content).forEach { panel ->
            assert(panel.left >= shell.left && panel.right <= shell.right)
            assert(panel.top >= shell.top && panel.bottom <= shell.bottom)
        }
        assert(navigation.right < content.left)
    }

    private fun pilotSchedule() = LocalPrayerSchedule(
        snapshotId = "ulyanovsk-second-cathedral-2026-pilot-local-v1",
        mosqueId = "second-cathedral-mosque-ulyanovsk",
        mosqueName = "Вторая Соборная мечеть Ульяновска",
        locality = "Ульяновск, ул. Дзержинского, 18А",
        timezoneId = "Europe/Ulyanovsk",
        sourceKind = "manual_import",
        authorityName = "Composite schedule; see bound source components",
        sourceId = "effective-ulyanovsk-2026-v1",
        geographicScope = "Вторая Соборная мечеть Ульяновска; 2026",
        attribution = "Региональное духовное управление мусульман Ульяновской области",
        retrievedAt = "2026-08-20T11:31:33Z",
        coverageFrom = "2026-01-01",
        coverageTo = "2026-12-31",
        days = listOf(
            LocalPrayerDay("2026-08-20", "02:55", "05:29", "13:15", "17:49", "20:14", "22:08"),
        ),
        iqamahRules = listOf(
            LocalIqamahRule(
                id = "fajr-plus-five-2026",
                prayer = "fajr",
                validFrom = "2026-01-01",
                validTo = "2026-12-31",
                weekdaysMask = 127,
                priority = 100,
                mode = "offset_after_adhan",
                fixedTime = null,
                offsetMinutes = 5,
                reason = null,
            ),
        ),
        jumuahSessions = listOf(
            LocalJumuahSession(
                id = "jumuah-friday-1315-2026",
                label = "Джума",
                khutbahTime = null,
                salahTime = "13:15",
                validFrom = "2026-01-01",
                validTo = "2026-12-31",
            ),
        ),
        diagnostics = LocalSnapshotDiagnostics(
            dataClassification = "production",
            generatedAt = "2026-08-20T15:00:00Z",
            rawSha256 = "c7d95bbc900a683b3be4fa66f6d1a8237ccf3e882452674a2cdd946c800d935a",
            parserVersion = "effective-schedule/v1",
            approvalId = "approval-second-cathedral-mosque-ulyanovsk-2026-001",
            approvalStatus = "approved",
            approvedBy = "approver:ulyanovsk-mosques:akhmedov-elmaddin-fazil-ogly",
            approvedAt = "2026-08-20T14:31:00Z",
            approvalScope = "2026",
            signingKeyId = "pilot-local-schedule-2026-01",
        ),
    )
}
