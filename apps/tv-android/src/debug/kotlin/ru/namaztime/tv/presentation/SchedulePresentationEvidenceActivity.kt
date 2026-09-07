package ru.namaztime.tv.presentation

import android.os.Bundle
import android.app.Presentation
import android.hardware.display.DisplayManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ru.namaztime.tv.data.snapshot.SnapshotBootstrapState
import ru.namaztime.tv.domain.CampaignEngine
import ru.namaztime.tv.domain.CountdownPolicy
import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution
import ru.namaztime.tv.domain.QrCodeGenerator
import ru.namaztime.tv.repository.LocalPrayerDay
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import ru.namaztime.tv.repository.OperatorDisplayMode
import ru.namaztime.tv.repository.OperatorIqamahConfiguration
import ru.namaztime.tv.repository.OperatorPreferences
import ru.namaztime.tv.repository.OperatorQrConfiguration
import ru.namaztime.tv.repository.ScheduleLayoutMode
import ru.namaztime.tv.repository.toTimeEngineInput
import java.time.Instant

/** Debug-only synthetic, fixed-clock evidence; never reads or changes device configuration. */
class SchedulePresentationEvidenceActivity : ComponentActivity() {
    private var evidencePresentation: Presentation? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val language = intent.getStringExtra("language") ?: "ru"
        val scenario = intent.getStringExtra("scenario") ?: "compact"
        val payload = intent.getStringExtra("payload") ?: "https://example.org/sadaqah"
        val showIqamah = intent.getBooleanExtra("iqamah", true)
        val qr = intent.getBooleanExtra("qr", true)
        val sourceRequiresAttention = intent.getBooleanExtra("attention", false)
        val instant = Instant.parse(intent.getStringExtra("instant") ?: "2026-08-19T11:23:00Z")
            .plusSeconds(intent.getIntExtra("shift", 0) * 600L)
        val english = language == "en"
        val campaign = QrCampaignUiState(
            "synthetic-t045", "donation", intent.getStringExtra("title") ?: if (english) "Support the mosque" else "На развитие мечети",
            intent.getStringExtra("message"), QrCodeGenerator().generate(payload), false,
        )
        val preferences = OperatorPreferences(
            languageTag = language,
            showIqamahOnSchedule = showIqamah,
            scheduleLayoutMode = if (scenario == "standard") ScheduleLayoutMode.STANDARD else ScheduleLayoutMode.RIGHT_SIDE_COMPACT,
            iqamahConfiguration = OperatorIqamahConfiguration(5, 795, 5, 5, 5),
            qrConfiguration = if (qr) OperatorQrConfiguration(payload, campaign.title, campaign.subtitle.orEmpty()) else OperatorQrConfiguration(),
            displayMode = if (scenario == "donation") OperatorDisplayMode.DONATION else OperatorDisplayMode.SCHEDULE,
            donationConfiguration = OperatorDonationConfiguration(
                httpsUrl = payload,
                recipient = if (english) "Synthetic community" else "Синтетическая община",
                bank = "Example Bank", cardNumber = "0000 0000 0000 0000", phone = "+0 000 000 00 00",
            ),
        )
        val schedule = LocalPrayerSchedule(
            snapshotId = "synthetic-t045", mosqueId = "synthetic-t045",
            mosqueName = intent.getStringExtra("mosqueName")
                ?: if (english) "Synthetic mosque" else "Синтетическая мечеть",
            locality = if (english) "Example city" else "Тестовый город",
            timezoneId = "Europe/Ulyanovsk", sourceKind = "manual_import",
            authorityName = "Synthetic evidence", coverageFrom = "2026-08-19", coverageTo = "2026-08-20",
            days = listOf(
                LocalPrayerDay("2026-08-19", "04:31", "05:56", "12:25", "15:47", "18:54", "20:21"),
                LocalPrayerDay("2026-08-20", "04:33", "05:58", "12:25", "15:45", "18:52", "20:19"),
            ),
        )
        val evidenceDisplayState = if (scenario == "compact") {
            val engine = PrayerTimeEngine(CountdownPolicy(includeIqamah = showIqamah))
            val resolution = engine.resolve(
                schedule.toTimeEngineInput(preferences.iqamahConfiguration, instant),
                instant,
            ) as PrayerTimeResolution.Available
            schedule.toPrayerDisplayUiState(
                resolution = resolution,
                strings = appStringsFor(this, AppLanguage.fromTag(language)),
                showIqamahOnSchedule = showIqamah,
            ).copy(
                campaign = campaign.takeIf { qr },
                sourceRequiresAttention = sourceRequiresAttention,
                sourceLabel = if (english) "NOT APPROVED" else "НЕ ОДОБРЕНО",
            )
        } else {
            null
        }
        val content: @Composable () -> Unit = {
            AppLanguageProvider(language) {
                NamazTvTheme {
                    Box(Modifier.fillMaxSize()) {
                        TvAtmosphericBackground(
                            styleId = intent.getStringExtra("background") ?: "golden_dusk",
                            compact = scenario == "compact" || scenario == "background",
                        )
                        when (scenario) {
                            "background" -> Unit
                            "settings-iqamah", "settings-appearance", "settings-qr" -> SettingsShell(
                                initialDestination = when (scenario) {
                                    "settings-iqamah" -> SettingsDestination.IQAMAH
                                    "settings-appearance" -> SettingsDestination.APPEARANCE
                                    else -> SettingsDestination.CAMPAIGNS
                                },
                                onDestinationChanged = {}, onExit = {}, preferences = preferences,
                                schedule = schedule, campaignPreview = campaign.copy(preview = true),
                                onIqamahConfigurationChanged = {}, onQrConfigurationChanged = {},
                                onShowIqamahOnScheduleChanged = {}, onScheduleLayoutModeChanged = {},
                                onBackgroundStyleChanged = {},
                            )
                            "compact" -> CompactPrayerDisplay(
                                state = requireNotNull(evidenceDisplayState),
                                retentionOffset = screenRetentionOffsetAt(instant),
                                requestInitialFocus = false,
                                onOpenSettings = {},
                            )
                            else -> ConnectedDisplayContent(
                                schedule, instant, SnapshotBootstrapState.Ready(schedule.snapshotId),
                                CampaignEngine(), QrCodeGenerator(), preferences,
                                donationQrState = campaign, onOpenSettings = {},
                            )
                        }
                    }
                }
            }
        }
        val displayId = intent.getIntExtra("presentationDisplay", -1)
        if (displayId >= 0) {
            val display = requireNotNull(getSystemService(DisplayManager::class.java).getDisplay(displayId))
            evidencePresentation = Presentation(this, display).apply {
                val view = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(this@SchedulePresentationEvidenceActivity)
                    setViewTreeSavedStateRegistryOwner(this@SchedulePresentationEvidenceActivity)
                    setContent(content)
                }
                setContentView(view)
                window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                show()
            }
        } else {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setContent(content = content)
        }
    }

    override fun onDestroy() {
        evidencePresentation?.dismiss()
        super.onDestroy()
    }
}
