package ru.namaztime.tv.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.namaztime.tv.domain.QrCodeGenerator
import ru.namaztime.tv.repository.OperatorQrTextFitPolicy

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CompactReferenceTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun referenceHierarchyAndAllowedCopy720p() = verify()

    @Test @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun referenceHierarchyAndAllowedCopy1080p() = verify()

    @Test @Config(qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun referenceHierarchyAndAllowedCopy4k() = verify()

    @Test @Config(qualifiers = "w960dp-h540dp-land-xhdpi")
    fun longCampaignParagraphUsesAvailableSpace1080p() = verifyLongParagraph()

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun longCampaignParagraphUsesAvailableSpace720p() = verifyLongParagraph()

    @Test @Config(qualifiers = "w1280dp-h720dp-land-xxhdpi")
    fun longCampaignParagraphUsesAvailableSpace4k() = verifyLongParagraph()

    private fun verifyLongParagraph() {
        val message = "Every sincere contribution supports the mosque, helps our community, welcomes every visitor, and becomes lasting good."
        compose.setContent { NamazTvTheme {
            CompactPrayerDisplay(fixture().copy(campaign = fixture().campaign!!.copy(subtitle = message)), {}, requestInitialFocus = false)
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(QR_CAMPAIGN_SUBTITLE_TAG)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.hasVisualOverflow)
        val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).getUnclippedBoundsInRoot()
        val scale = (root.bottom - root.top).value / 540f
        assertTrue("long paragraphs must grow beyond T046's 10sp", layout.layoutInput.style.fontSize.value >= 12f * scale)
        compose.onNodeWithTag(QR_CAMPAIGN_SUBTITLE_TAG).assertTextEquals(message)
    }

    private fun verify() {
        val state = mutableStateOf(fixture())
        compose.setContent { NamazTvTheme { CompactPrayerDisplay(state.value, {}, requestInitialFocus = false) } }
        for (label in listOf("Аср", "Фаджр · завтра", "Джума · 2", "Fajr · tomorrow", "Jumu'ah · iqamah")) {
            for (iqamah in listOf(false, true)) for (longCopy in listOf(false, true)) {
                compose.runOnIdle {
                    state.value = fixture().copy(nextPrayerLabel = label, showIqamahOnSchedule = iqamah,
                        jumuahSessions = if (label.contains("Джума") || label.contains("Jumu"))
                            listOf(JumuahDisplaySession("second", "Джума 2 · 13:15")) else emptyList(),
                        campaign = fixture().campaign!!.copy(
                            kind = if (longCopy) "website" else "donation",
                            title = if (longCopy) "Информация о работе и мероприятиях нашей общины. ".repeat(4).take(160) else "На развитие мечети",
                            subtitle = if (longCopy) "Первая строка\nВторая строка\nТретья строка\nЧетвёртая строка\nПятая строка\nШестая строка" else
                                "Тем же из вас, которые уверовали и расходовали, уготована великая награда.",
                        ))
                }
                assertNotNull(OperatorQrTextFitPolicy.fit(state.value.campaign!!.subtitle!!))
                val containers = mapOf(
                    MOSQUE_NAME_TEST_TAG to COMPACT_HEADER_TAG,
                    NEXT_EVENT_LABEL_TAG to NEXT_EVENT_CARD_TAG, NEXT_EVENT_NAME_TAG to NEXT_EVENT_CARD_TAG,
                    COUNTDOWN_TEST_TAG to NEXT_EVENT_CARD_TAG, DATE_LABEL_TAG to LOCAL_CLOCK_CARD_TAG,
                    WEEKDAY_LABEL_TAG to LOCAL_CLOCK_CARD_TAG, LOCAL_CLOCK_VALUE_TAG to LOCAL_CLOCK_CARD_TAG,
                    QR_CAMPAIGN_TITLE_TAG to QR_CAMPAIGN_PANEL_TAG, QR_CAMPAIGN_SUBTITLE_TAG to QR_CAMPAIGN_PANEL_TAG,
                ) + state.value.rows.flatMap { row -> listOf(
                    "compact-prayer-name-${row.id}" to PRAYER_LIST_CARD_TAG,
                    "compact-adhan-${row.id}" to PRAYER_LIST_CARD_TAG,
                ) }.toMap()
                for ((tag, parent) in containers) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
                    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertTrue("$tag measured", layouts.isNotEmpty())
                    assertFalse("$tag overflow for $label iqamah=$iqamah long=$longCopy", layouts.any { it.hasVisualOverflow })
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    val card = compose.onNodeWithTag(parent).fetchSemanticsNode().boundsInRoot
                    assertTrue("$tag contained $bounds in $card", bounds.left >= card.left && bounds.right <= card.right + 1 &&
                        bounds.top >= card.top && bounds.bottom <= card.bottom + 1)
                }
                for ((upper, lower) in listOf(NEXT_EVENT_LABEL_TAG to NEXT_EVENT_NAME_TAG,
                    NEXT_EVENT_NAME_TAG to COUNTDOWN_TEST_TAG, DATE_LABEL_TAG to WEEKDAY_LABEL_TAG,
                    WEEKDAY_LABEL_TAG to LOCAL_CLOCK_VALUE_TAG, QR_CAMPAIGN_TITLE_TAG to QR_CAMPAIGN_SUBTITLE_TAG)) {
                    val a = compose.onNodeWithTag(upper).fetchSemanticsNode().boundsInRoot
                    val b = compose.onNodeWithTag(lower).fetchSemanticsNode().boundsInRoot
                    assertTrue("$upper overlaps $lower: $a $b", a.bottom <= b.top)
                }
                val nameLayouts = mutableListOf<TextLayoutResult>()
                val name = compose.onNodeWithTag(MOSQUE_NAME_TEST_TAG)
                name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nameLayouts) }
                val nameBounds = name.fetchSemanticsNode().boundsInRoot
                val gear = compose.onNodeWithTag(MAIN_DISPLAY_SETTINGS_TAG).fetchSemanticsNode().boundsInRoot
                val layout = nameLayouts.single()
                for (index in state.value.mosqueName.indices) {
                    val glyph = layout.getBoundingBox(index).translate(nameBounds.topLeft)
                    assertFalse("mosque name touches Settings: $glyph $gear", glyph.overlaps(gear))
                }
                if (!longCopy && !iqamah && label == "Аср") {
                    val timeLayouts = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithTag("compact-adhan-fajr", useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(timeLayouts) }
                    val root = compose.onNodeWithTag(MAIN_PRAYER_DISPLAY_TAG).getUnclippedBoundsInRoot()
                    val scale = (root.bottom - root.top).value / 540f
                    assertTrue("OFF prayer times must be larger than T046's 22sp at 540dp",
                        timeLayouts.single().layoutInput.style.fontSize.value > 22f * scale)
                    fun font(tag: String): Float {
                        val result = mutableListOf<TextLayoutResult>()
                        compose.onNodeWithTag(tag, useUnmergedTree = true)
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
                        return result.single().layoutInput.style.fontSize.value
                    }
                    assertTrue("countdown leads the next prayer hierarchy", font(COUNTDOWN_TEST_TAG) > font(NEXT_EVENT_NAME_TAG))
                    assertTrue("next prayer leads its label", font(NEXT_EVENT_NAME_TAG) > font(NEXT_EVENT_LABEL_TAG))
                    assertTrue("clock leads date", font(LOCAL_CLOCK_VALUE_TAG) > font(DATE_LABEL_TAG) * 2)
                    assertTrue("adhan leads prayer name", font("compact-adhan-fajr") > font("compact-prayer-name-fajr"))
                    assertTrue("campaign title readable at TV size", font(QR_CAMPAIGN_TITLE_TAG) >= 19 * scale)
                    assertTrue("ordinary campaign message readable at TV size", font(QR_CAMPAIGN_SUBTITLE_TAG) >= 15 * scale)
                    compose.onNodeWithTag(COUNTDOWN_TEST_TAG)
                        .assertTextEquals(state.value.countdown)
                    compose.onNodeWithTag(LOCAL_CLOCK_VALUE_TAG)
                        .assertTextEquals(state.value.mosqueLocalTime)
                    for (tag in listOf(COMPACT_RIGHT_RAIL_TAG, COMPACT_HEADER_TAG, PRAYER_LIST_CARD_TAG,
                        NEXT_EVENT_CARD_TAG, LOCAL_CLOCK_CARD_TAG, QR_CAMPAIGN_PANEL_TAG, QR_CODE_IMAGE_TAG)) {
                        println("T046 geometry $tag ${compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot}")
                    }
                }
            }
        }
    }

    private fun fixture() = PrayerDisplayUiState(
        "Вторая Соборная Мечеть", "Ульяновск", "6 сентября 2026", "Воскресенье", "16:58:10",
        "Зухр", "Аср", "Азан", "17:19", "00:20:50", null,
        sourceLabel = "Synthetic", sourceDescription = "Fixture", sourceRequiresAttention = false,
        campaign = QrCampaignUiState("fixture", "donation", "На развитие мечети", null,
            QrCodeGenerator().generate("https://example.org/sadaqah"), false),
        rows = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha").zip(
            listOf("Фаджр", "Восход", "Зухр", "Аср", "Магриб", "Иша")).map { (id, label) ->
            PrayerDisplayRow(id, label, "12:00", if (id == "sunrise") null else "12:05", isNextEvent = id == "asr")
        },
    )
}
