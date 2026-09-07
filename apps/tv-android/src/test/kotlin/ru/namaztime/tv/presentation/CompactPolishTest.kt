package ru.namaztime.tv.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.namaztime.tv.domain.QrCodeGenerator

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class CompactPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun clockPresentationDropsSecondsWithoutChangingExactState() {
        assertEquals("16:58", compactClockPresentation("16:58:10"))
        assertEquals("—:——", compactClockPresentation("—:——:——"))
        assertEquals("invalid", compactClockPresentation("invalid"))
    }

    @Test
    fun countdownPresentationRoundsUpUntilTheRealEvent() {
        assertEquals("00:00", compactCountdownPresentation("00:00:00"))
        assertEquals("00:01", compactCountdownPresentation("00:00:01"))
        assertEquals("00:01", compactCountdownPresentation("00:00:59"))
        assertEquals("00:01", compactCountdownPresentation("00:01:00"))
        assertEquals("00:02", compactCountdownPresentation("00:01:01"))
        assertEquals("02:50", compactCountdownPresentation("02:49:57"))
        assertEquals("25:01", compactCountdownPresentation("25:00:01"))
        assertEquals("—:——", compactCountdownPresentation("—:——:——"))
    }

    @Test
    fun compactDisplayShowsMinutesAndKeepsExactAccessibilityValues() {
        val state = fixture().copy(mosqueLocalTime = "16:58:10", countdown = "00:00:01")
        compose.setContent {
            AppLanguageProvider("ru") {
                NamazTvTheme { CompactPrayerDisplay(state, {}, requestInitialFocus = false) }
            }
        }

        compose.onNodeWithTag(LOCAL_CLOCK_VALUE_TAG, useUnmergedTree = true)
            .assertTextEquals("16:58")
            .assertContentDescriptionEquals("16:58:10")
        compose.onNodeWithTag(COUNTDOWN_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("00:01")
            .assertContentDescriptionEquals("До следующего события 00:00:01")
    }

    @Test
    fun longMosqueIdentityUsesTwoReadableLinesWithoutTouchingSettings() {
        val longName = "Синтетическая Местная религиозная организация мусульман Города"
        compose.setContent {
            NamazTvTheme {
                CompactPrayerDisplay(fixture().copy(mosqueName = longName), {}, requestInitialFocus = false)
            }
        }

        val layouts = textLayouts(MOSQUE_NAME_TEST_TAG)
        val layout = layouts.single()
        val name = compose.onNodeWithTag(MOSQUE_NAME_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val brand = compose.onNodeWithTag(BRAND_PILL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag(COMPACT_HEADER_TAG).fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals("long identity should use a deliberate two-line hero", 2, layout.lineCount)
        assertFalse(
            "long identity overflow: size=${layout.size}, font=${layout.layoutInput.style.fontSize}, " +
                "lineHeight=${layout.layoutInput.style.lineHeight}, bounds=$name",
            layout.hasVisualOverflow,
        )
        assertTrue("long identity must stay above secondary type", layout.layoutInput.style.fontSize.value >= 18f)
        assertTrue("brand must remain inside header: brand=$brand header=$header", brand.top >= header.top)
        assertTrue("brand must not overlap the identity: brand=$brand name=$name", brand.bottom <= name.top)
        for (index in layout.layoutInput.text.text.indices.filter { layout.layoutInput.text.text[it] != '\n' }) {
            assertFalse(layout.getBoundingBox(index).translate(name.topLeft).overlaps(settings))
        }
    }

    @Test
    fun typographyKeepsRegularInformationSeparateFromHeroValues() {
        compose.setContent { NamazTvTheme { CompactPrayerDisplay(fixture(), {}, requestInitialFocus = false) } }

        assertEquals(FontWeight.Normal, textLayouts("compact-prayer-name-fajr").single().layoutInput.style.fontWeight)
        assertEquals(FontWeight.Normal, textLayouts(DATE_LABEL_TAG).single().layoutInput.style.fontWeight)
        assertEquals(FontWeight.Normal, textLayouts(WEEKDAY_LABEL_TAG).single().layoutInput.style.fontWeight)
        assertEquals(FontWeight.Medium, textLayouts(NEXT_EVENT_NAME_TAG).single().layoutInput.style.fontWeight)
        assertEquals(FontWeight.SemiBold, textLayouts(COUNTDOWN_TEST_TAG).single().layoutInput.style.fontWeight)
        assertEquals(FontWeight.SemiBold, textLayouts(LOCAL_CLOCK_VALUE_TAG).single().layoutInput.style.fontWeight)
    }

    @Test
    fun warningIsABoundedHeaderChipAndApprovedStateAddsNothing() {
        val approved = fixture()
        val state = mutableStateOf(approved)
        compose.setContent { NamazTvTheme { CompactPrayerDisplay(state.value, {}, requestInitialFocus = false) } }
        compose.onNodeWithTag(COMPACT_SOURCE_STATUS_TAG).assertDoesNotExist()

        compose.runOnIdle {
            state.value = approved.copy(sourceRequiresAttention = true, sourceLabel = "НЕ ОДОБРЕНО")
        }
        val chip = compose.onNodeWithTag(COMPACT_SOURCE_STATUS_TAG).assertIsDisplayed()
        chip.assertContentDescriptionEquals("НЕ ОДОБРЕНО")
        val chipBounds = chip.fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag(COMPACT_HEADER_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(chipBounds.left >= header.left && chipBounds.right <= header.right)
        assertTrue(chipBounds.top >= header.top && chipBounds.bottom <= header.bottom)
    }

    @Test
    fun sixLineCampaignCopyGrowsInsteadOfBecomingMicrocopy() {
        val message = "Первая строка\nВторая строка\nТретья строка\nЧетвёртая строка\nПятая строка\nШестая строка"
        compose.setContent {
            NamazTvTheme {
                CompactPrayerDisplay(
                    fixture().copy(campaign = fixture().campaign!!.copy(subtitle = message)),
                    {},
                    requestInitialFocus = false,
                )
            }
        }

        val layout = textLayouts(QR_CAMPAIGN_SUBTITLE_TAG).single()
        assertFalse(layout.hasVisualOverflow)
        assertTrue("accepted campaign copy must remain TV-readable", layout.layoutInput.style.fontSize.value >= 12f)
        compose.onNodeWithTag(QR_CAMPAIGN_SUBTITLE_TAG).assertTextEquals(message)
    }

    private fun textLayouts(tag: String): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts
    }

    private fun fixture() = PrayerDisplayUiState(
        mosqueName = "Вторая Соборная Мечеть",
        location = "Ульяновск",
        dateLabel = "6 сентября 2026",
        weekdayLabel = "Воскресенье",
        mosqueLocalTime = "16:58:10",
        currentPrayerLabel = "Зухр",
        nextPrayerLabel = "Аср",
        nextEventKindLabel = "Азан",
        nextEventTime = "17:19",
        countdown = "00:20:50",
        iqamahSummary = null,
        sourceLabel = "ОДОБРЕНО",
        sourceDescription = "Fixture",
        sourceRequiresAttention = false,
        campaign = QrCampaignUiState(
            "fixture",
            "donation",
            "На развитие мечети",
            "Поддержка нашей общины",
            QrCodeGenerator().generate("https://example.org/sadaqah"),
            false,
        ),
        rows = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").zip(
            listOf("Фаджр", "Восход", "Зухр", "Аср", "Магриб", "Иша"),
        ).map { (id, label) ->
            PrayerDisplayRow(
                id,
                label,
                "12:00",
                if (id == "sunrise") null else "12:05",
                isNextEvent = id == "asr",
            )
        },
    )
}
