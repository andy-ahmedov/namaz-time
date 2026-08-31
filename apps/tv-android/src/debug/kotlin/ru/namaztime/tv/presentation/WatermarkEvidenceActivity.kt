package ru.namaztime.tv.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ru.namaztime.tv.domain.QrCodeGenerator
import ru.namaztime.tv.repository.BLUE_HOUR_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.DEFAULT_BACKGROUND_STYLE_ID

/** Debug-only synthetic renderer for controlled T044 watermark screenshots. */
class WatermarkEvidenceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra(EXTRA_SCENARIO).orEmpty()
        setContent {
            NamazTvTheme {
                Box(Modifier.fillMaxSize()) {
                    TvAtmosphericBackground(
                        styleId = if (scenario == SCENARIO_SHORT_BLUE) {
                            BLUE_HOUR_BACKGROUND_STYLE_ID
                        } else {
                            DEFAULT_BACKGROUND_STYLE_ID
                        },
                    )
                    MainPrayerDisplay(
                        state = watermarkEvidenceState(
                            nextPrayerLabel = if (scenario == SCENARIO_LONG_GOLDEN) {
                                "Фаджр · завтра"
                            } else {
                                "Аср"
                            },
                        ),
                        onOpenSettings = {},
                        requestInitialFocus = false,
                    )
                }
            }
        }
    }

    private companion object {
        const val EXTRA_SCENARIO = "scenario"
        const val SCENARIO_LONG_GOLDEN = "long-golden"
        const val SCENARIO_SHORT_BLUE = "short-blue"
    }
}

private fun watermarkEvidenceState(nextPrayerLabel: String) = PrayerDisplayUiState(
    mosqueName = "Синтетическая мечеть",
    location = "Тестовый город",
    dateLabel = "19 августа 2026",
    weekdayLabel = "Среда",
    mosqueLocalTime = "15:23:00",
    currentPrayerLabel = "Зухр",
    nextPrayerLabel = nextPrayerLabel,
    nextEventKindLabel = "Азан",
    nextEventTime = "15:47",
    countdown = "00:24:00",
    iqamahSummary = IqamahSummaryUiState(
        label = "Икамат · Аср",
        time = "15:52",
        countdownLabel = "До азана",
    ),
    sourceLabel = "Синтетическое доказательство",
    sourceDescription = "Только T044 runtime evidence",
    sourceRequiresAttention = false,
    campaign = QrCampaignUiState(
        id = "synthetic-t044",
        kind = "donation",
        title = "На развитие мечети",
        subtitle = "Ваша поддержка помогает общине",
        qrCode = QrCodeGenerator().generate("https://example.org/t044"),
        preview = false,
    ),
    rows = listOf(
        PrayerDisplayRow("fajr", "Фаджр", "04:31", "04:36"),
        PrayerDisplayRow(
            "sunrise",
            "Восход",
            "05:56",
            iqamahPresentation = IqamahPresentation.NOT_APPLICABLE,
        ),
        PrayerDisplayRow("dhuhr", "Зухр", "12:25", "13:15"),
        PrayerDisplayRow("asr", "Аср", "15:47", "15:52", isNextEvent = true),
        PrayerDisplayRow("maghrib", "Магриб", "18:54", "18:59"),
        PrayerDisplayRow("isha", "Иша", "20:21", "20:26"),
    ),
)
