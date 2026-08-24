package com.example.namaztime.tv.presentation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.example.namaztime.tv.BuildConfig
import com.example.namaztime.tv.R
import com.example.namaztime.tv.data.snapshot.SnapshotBootstrapState
import com.example.namaztime.tv.domain.CampaignEngine
import com.example.namaztime.tv.domain.CampaignPreview
import com.example.namaztime.tv.domain.CampaignResolution
import com.example.namaztime.tv.domain.PrayerTimeEngine
import com.example.namaztime.tv.domain.PrayerTimeResolution
import com.example.namaztime.tv.domain.QrCodeGenerator
import com.example.namaztime.tv.repository.CorruptLocalSnapshotException
import com.example.namaztime.tv.repository.EmptyPrayerScheduleRepository
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorPreferencesRepository
import com.example.namaztime.tv.repository.AndroidOperatorImageAssetImporter
import com.example.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID
import com.example.namaztime.tv.repository.OperatorImageImportResult
import com.example.namaztime.tv.repository.OperatorImageSlot
import com.example.namaztime.tv.repository.PrayerScheduleRepository
import com.example.namaztime.tv.repository.toCampaignInputs
import com.example.namaztime.tv.repository.toCampaignInput
import com.example.namaztime.tv.repository.toTimeEngineInput
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val DISPLAY_ROUTE = "display"
private const val SETTINGS_ROUTE = "settings"
const val DISPLAY_UNAVAILABLE_TAG = "display-unavailable"
const val UNAVAILABLE_PANEL_TAG = "unavailable-panel"

@Composable
fun NamazTvApp(
    operatorPreferencesRepository: OperatorPreferencesRepository,
    prayerScheduleRepository: PrayerScheduleRepository = EmptyPrayerScheduleRepository,
    bootstrapState: Flow<SnapshotBootstrapState> = flowOf(
        SnapshotBootstrapState.Diagnostic("NO_LOCAL_SNAPSHOT"),
    ),
    clock: Clock = Clock.systemUTC(),
    tickIntervalMillis: Long? = 1_000L,
) {
    val preferences by operatorPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = OperatorPreferences(),
    )
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageImporter = remember(context) { AndroidOperatorImageAssetImporter(context) }
    var customAssetVersion by remember { mutableLongStateOf(0L) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri ->
        if (uri != null) {
            coroutineScope.launch {
                if (imageImporter.import(OperatorImageSlot.BACKGROUND, uri) ==
                    OperatorImageImportResult.Imported
                ) {
                    try {
                        operatorPreferencesRepository.setBackgroundStyleId(
                            CUSTOM_BACKGROUND_STYLE_ID,
                        )
                        customAssetVersion += 1L
                    } catch (_: IOException) {
                        // The imported app-local copy remains available for a later selection.
                    }
                }
            }
        }
    }
    val campaignEngine = remember { CampaignEngine() }
    val qrCodeGenerator = remember { QrCodeGenerator() }
    val observedSchedule by prayerScheduleRepository.observeForDisplay()
        .collectAsStateWithLifecycle(initialValue = DisplayScheduleState.Unavailable)
    val bootstrap by bootstrapState.collectAsStateWithLifecycle(
        initialValue = SnapshotBootstrapState.Pending,
    )
    val availableSchedule = (observedSchedule as? DisplayScheduleState.Available)?.schedule
    val campaignPreview = remember(
        availableSchedule,
        preferences.qrConfiguration,
        campaignEngine,
        qrCodeGenerator,
    ) {
        val operatorCampaign = preferences.qrConfiguration
            .takeUnless { it.isEmpty }
            ?.toCampaignInput()
            ?.let(campaignEngine::preview)
            ?.let { it as? CampaignPreview.Valid }
            ?.campaign
        val snapshotCampaign = if (preferences.qrConfiguration.isEmpty) {
            availableSchedule?.let { schedule ->
                val validPreviews = schedule.toCampaignInputs().mapNotNull { campaign ->
                    (campaignEngine.preview(campaign) as? CampaignPreview.Valid)?.campaign
                }
                validPreviews.singleOrNull()
            }
        } else {
            null
        }
        (operatorCampaign ?: snapshotCampaign)?.let { campaign ->
            runCatching {
                campaign.toQrCampaignUiState(
                    qrCode = qrCodeGenerator.generate(campaign.httpsUrl),
                    preview = true,
                )
            }.getOrNull()
        }
    }

    AppLanguageProvider(preferences.languageTag) {
        NamazTvTheme {
            Box(Modifier.fillMaxSize()) {
                TvAtmosphericBackground(
                    styleId = preferences.backgroundStyleId,
                    customAssetVersion = customAssetVersion,
                )
                NavHost(
                    navController = navController,
                    startDestination = DISPLAY_ROUTE,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(DISPLAY_ROUTE) {
                        DisplayRoute(
                            schedule =
                                (observedSchedule as? DisplayScheduleState.Available)?.schedule,
                            localDiagnostic =
                                (observedSchedule as? DisplayScheduleState.Diagnostic)?.supportCode,
                            bootstrapState = bootstrap,
                            clock = clock,
                            tickIntervalMillis = tickIntervalMillis,
                            campaignEngine = campaignEngine,
                            qrCodeGenerator = qrCodeGenerator,
                            operatorPreferences = preferences,
                            screenRetentionShiftEnabled =
                                preferences.screenRetentionShiftEnabled,
                            onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        )
                    }
                    composable(SETTINGS_ROUTE) {
                        SettingsShell(
                            initialDestination = SettingsDestination.fromRoute(
                                preferences.lastSettingsDestination,
                            ),
                            onDestinationChanged = { destination ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository.setLastSettingsDestination(
                                            destination.route,
                                        )
                                    } catch (_: IOException) {
                                        // Focus remains usable if non-critical persistence fails.
                                    }
                                }
                            },
                            onExit = { navController.popBackStack() },
                            campaignPreview = campaignPreview,
                            schedule = availableSchedule,
                            preferences = preferences,
                            appVersion = BuildConfig.VERSION_NAME,
                            pilotLocalRuntime = BuildConfig.PILOT_LOCAL_RUNTIME,
                            onScreenRetentionShiftChanged = { enabled ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository
                                            .setScreenRetentionShiftEnabled(enabled)
                                    } catch (_: IOException) {
                                        // The display remains usable if a preference write fails.
                                    }
                                }
                            },
                            onBackgroundStyleChanged = { styleId ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository.setBackgroundStyleId(styleId)
                                    } catch (_: IOException) {
                                        // The current background remains if persistence fails.
                                    }
                                }
                            },
                            onLanguageChanged = { languageTag ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository.setLanguageTag(languageTag)
                                    } catch (_: IOException) {
                                        // The current language remains if persistence fails.
                                    }
                                }
                            },
                            onQrConfigurationChanged = { configuration ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository.setQrConfiguration(configuration)
                                    } catch (_: IOException) {
                                        // The last valid local QR remains active if persistence fails.
                                    } catch (_: IllegalArgumentException) {
                                        // Invalid operator input is rejected without changing the display.
                                    }
                                }
                            },
                            onIqamahOffsetsChanged = { offsets ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository.setIqamahOffsets(offsets)
                                    } catch (_: IOException) {
                                        // The last valid local iqamah settings remain active.
                                    } catch (_: IllegalArgumentException) {
                                        // Invalid operator input is rejected without changing the display.
                                    }
                                }
                            },
                            onPickCustomBackground = {
                                backgroundPicker.launch(
                                    arrayOf("image/jpeg", "image/png", "image/webp"),
                                )
                            },
                            customAssetVersion = customAssetVersion,
                            onOpenSystemSettings = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayRoute(
    schedule: LocalPrayerSchedule?,
    localDiagnostic: String?,
    bootstrapState: SnapshotBootstrapState,
    clock: Clock,
    tickIntervalMillis: Long?,
    campaignEngine: CampaignEngine,
    qrCodeGenerator: QrCodeGenerator,
    operatorPreferences: OperatorPreferences,
    screenRetentionShiftEnabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    DisplayKeepAwakeEffect()
    var currentInstant by remember(clock) {
        mutableStateOf(clock.instant())
    }
    LaunchedEffect(clock, tickIntervalMillis) {
        if (tickIntervalMillis != null) {
            while (true) {
                delay(tickIntervalMillis)
                currentInstant = clock.instant()
            }
        }
    }
    if (schedule != null) {
        ConnectedDisplayContent(
            schedule = schedule,
            currentInstant = currentInstant,
            bootstrapState = bootstrapState,
            campaignEngine = campaignEngine,
            qrCodeGenerator = qrCodeGenerator,
            operatorPreferences = operatorPreferences,
            screenRetentionShiftEnabled = screenRetentionShiftEnabled,
            onOpenSettings = onOpenSettings,
        )
        return
    }
    DisplayUnavailableScreen(
        localDiagnostic = localDiagnostic,
        bootstrapState = bootstrapState,
        retentionOffset = if (screenRetentionShiftEnabled) {
            screenRetentionOffsetAt(currentInstant)
        } else {
            DpOffset.Zero
        },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
internal fun ConnectedDisplayContent(
    schedule: LocalPrayerSchedule,
    currentInstant: Instant,
    bootstrapState: SnapshotBootstrapState,
    campaignEngine: CampaignEngine,
    qrCodeGenerator: QrCodeGenerator,
    operatorPreferences: OperatorPreferences = OperatorPreferences(),
    screenRetentionShiftEnabled: Boolean = true,
    onOpenSettings: () -> Unit,
) {
    val strings = appStrings()
    val retentionOffset = if (screenRetentionShiftEnabled) {
        screenRetentionOffsetAt(currentInstant)
    } else {
        DpOffset.Zero
    }
    val engine = remember { PrayerTimeEngine() }
    val mosqueZone = remember(schedule.timezoneId) { ZoneId.of(schedule.timezoneId) }
    val mosqueLocalDate = remember(currentInstant, mosqueZone) {
        currentInstant.atZone(mosqueZone).toLocalDate()
    }
    val projectionInstant = remember(mosqueLocalDate, mosqueZone) {
        mosqueLocalDate.atStartOfDay(mosqueZone).toInstant()
    }
    val timeInput = remember(schedule, operatorPreferences.iqamahOffsets, projectionInstant) {
        schedule.toTimeEngineInput(operatorPreferences.iqamahOffsets, projectionInstant)
    }
    val validationCode = remember(timeInput) { engine.validate(timeInput) }
    if (validationCode != null) {
        DisplayUnavailableScreen(
            localDiagnostic = validationCode,
            bootstrapState = bootstrapState,
            retentionOffset = retentionOffset,
            onOpenSettings = onOpenSettings,
        )
        return
    }
    val resolution = remember(timeInput, currentInstant) {
        engine.resolve(timeInput, currentInstant)
    }
    if (resolution is PrayerTimeResolution.Unavailable) {
        DisplayUnavailableScreen(
            localDiagnostic = resolution.supportCode,
            bootstrapState = bootstrapState,
            retentionOffset = retentionOffset,
            onOpenSettings = onOpenSettings,
        )
        return
    }
    val resolvedCampaign = remember(
        schedule.campaigns,
        operatorPreferences.qrConfiguration,
        currentInstant,
        campaignEngine,
    ) {
        val operatorCampaign = operatorPreferences.qrConfiguration
            .takeUnless { it.isEmpty }
            ?.toCampaignInput()
            ?.let(campaignEngine::preview)
            ?.let { it as? CampaignPreview.Valid }
            ?.campaign
        if (operatorPreferences.qrConfiguration.isEmpty) {
            operatorCampaign ?: (campaignEngine.resolve(schedule.toCampaignInputs(), currentInstant)
                as? CampaignResolution.Active)?.campaign
        } else {
            operatorCampaign
        }
    }
    val campaign = remember(resolvedCampaign, qrCodeGenerator) {
        resolvedCampaign?.let { resolved ->
            runCatching {
                resolved.toQrCampaignUiState(
                    qrCode = qrCodeGenerator.generate(resolved.httpsUrl),
                    preview = false,
                )
            }.getOrNull()
        }
    }
    val recoveryCode = (bootstrapState as? SnapshotBootstrapState.Ready)?.recoveryCode
    MainPrayerDisplay(
        state = schedule.toPrayerDisplayUiState(
            resolution = resolution as PrayerTimeResolution.Available,
            strings = strings,
        ).copy(supportCode = recoveryCode, campaign = campaign),
        retentionOffset = retentionOffset,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun DisplayUnavailableScreen(
    localDiagnostic: String?,
    bootstrapState: SnapshotBootstrapState,
    retentionOffset: DpOffset = DpOffset.Zero,
    onOpenSettings: () -> Unit,
) {
    val settingsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(settingsFocusRequester) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { settingsFocusRequester.requestFocus() }
    }

    TvSafeFrame(
        testTag = DISPLAY_UNAVAILABLE_TAG,
        contentAlignment = Alignment.Center,
        contentOffset = retentionOffset,
        contentShiftBudget = SCREEN_RETENTION_SHIFT_BUDGET,
    ) {
        TvGlassPanel(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .testTag(UNAVAILABLE_PANEL_TAG),
            radius = 28.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 56.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when {
                        localDiagnostic != null -> appString(R.string.schedule_unavailable)
                        else -> when (bootstrapState) {
                            SnapshotBootstrapState.Pending -> appString(R.string.schedule_loading)
                            is SnapshotBootstrapState.Ready -> appString(R.string.schedule_unavailable)
                            is SnapshotBootstrapState.Diagnostic -> appString(R.string.schedule_unavailable)
                        }
                    },
                    modifier = Modifier.semantics { heading() },
                    fontSize = 44.sp,
                    color = NamazTvTheme.colors.textPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = when {
                        localDiagnostic != null -> appString(R.string.support_code, localDiagnostic)
                        else -> when (bootstrapState) {
                            SnapshotBootstrapState.Pending -> appString(R.string.validating_local_snapshot)
                            is SnapshotBootstrapState.Ready ->
                                appString(R.string.no_current_local_schedule)
                            is SnapshotBootstrapState.Diagnostic ->
                                appString(R.string.support_code, bootstrapState.supportCode)
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                    fontSize = 24.sp,
                    color = NamazTvTheme.colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (
                    bootstrapState is SnapshotBootstrapState.Ready &&
                    bootstrapState.recoveryCode != null
                ) {
                    Text(
                        text = appString(R.string.support_code, bootstrapState.recoveryCode),
                        modifier = Modifier.padding(bottom = 24.dp),
                        fontSize = 20.sp,
                        color = NamazTvTheme.colors.warning,
                    )
                }
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.colors(
                        containerColor = NamazTvTheme.colors.accentSoft,
                        contentColor = NamazTvTheme.colors.accent,
                        focusedContainerColor = NamazTvTheme.colors.accent,
                        focusedContentColor = NamazTvTheme.colors.backgroundBottom,
                    ),
                    modifier = Modifier
                        .testTag(MAIN_DISPLAY_SETTINGS_TAG)
                        .focusRequester(settingsFocusRequester),
                ) {
                    Text(appString(R.string.open_settings))
                }
            }
        }
    }
}

private sealed interface DisplayScheduleState {
    data object Unavailable : DisplayScheduleState
    data class Available(val schedule: LocalPrayerSchedule) : DisplayScheduleState
    data class Diagnostic(val supportCode: String) : DisplayScheduleState
}

private fun PrayerScheduleRepository.observeForDisplay(): Flow<DisplayScheduleState> =
    observeActiveSchedule()
        .map { schedule ->
            schedule?.let(DisplayScheduleState::Available) ?: DisplayScheduleState.Unavailable
        }
        .catch { error ->
            if (error is CancellationException) throw error
            val supportCode = if (error is CorruptLocalSnapshotException) {
                "SNAPSHOT_LOCAL_${error.code.uppercase()}"
            } else {
                "SNAPSHOT_DATABASE_READ_FAILED"
            }
            emit(DisplayScheduleState.Diagnostic(supportCode))
        }
