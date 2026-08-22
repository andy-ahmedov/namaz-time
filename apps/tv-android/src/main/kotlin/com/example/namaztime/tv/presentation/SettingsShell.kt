package com.example.namaztime.tv.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.OperatorIqamahTimes
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorQrConfiguration
import com.example.namaztime.tv.repository.isValidIqamahTimes
import com.example.namaztime.tv.repository.isValidQrConfiguration

const val SETTINGS_PAGE_ACTION_TEST_TAG = "settings-page-primary-action"
const val SETTINGS_LOCAL_ACTION_TEST_TAG = "settings-page-local-action"
const val SETTINGS_BACKGROUND_ACTION_TEST_TAG = "settings-background-action"
const val SETTINGS_SHELL_TAG = "settings-shell"
const val SETTINGS_NAVIGATION_PANEL_TAG = "settings-navigation-panel"
const val SETTINGS_CONTENT_PANEL_TAG = "settings-content-panel"

@Composable
fun SettingsShell(
    initialDestination: SettingsDestination,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    campaignPreview: QrCampaignUiState? = null,
    schedule: LocalPrayerSchedule? = null,
    preferences: OperatorPreferences = OperatorPreferences(),
    appVersion: String = "",
    pilotLocalRuntime: Boolean = false,
    onScreenRetentionShiftChanged: ((Boolean) -> Unit)? = null,
    onBackgroundStyleChanged: ((String) -> Unit)? = null,
    onLanguageChanged: ((String) -> Unit)? = null,
    onQrConfigurationChanged: ((OperatorQrConfiguration) -> Unit)? = null,
    onIqamahTimesChanged: ((OperatorIqamahTimes) -> Unit)? = null,
    onOpenSystemSettings: (() -> Unit)? = null,
) {
    val navigationRequesters = remember {
        SettingsDestination.entries.associateWith { FocusRequester() }
    }
    val pageActionRequester = remember { FocusRequester() }
    val returnActionRequester = remember { FocusRequester() }
    var selectedRoute by rememberSaveable { mutableStateOf(initialDestination.route) }
    val selectedDestination = SettingsDestination.fromRoute(selectedRoute)

    LaunchedEffect(initialDestination) {
        selectedRoute = initialDestination.route
        withFrameNanos { }
        withFrameNanos { }
        runCatching { navigationRequesters.getValue(initialDestination).requestFocus() }
    }

    TvSafeFrame(testTag = SETTINGS_SHELL_TAG, modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            TvGlassPanel(
                modifier = Modifier
                    .width(340.dp)
                    .fillMaxHeight()
                    .testTag(SETTINGS_NAVIGATION_PANEL_TAG),
                radius = 24.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = appString(R.string.settings_title),
                        modifier = Modifier
                            .semantics { heading() }
                            .padding(bottom = 12.dp),
                        color = NamazTvTheme.colors.textPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SettingsDestination.entries.forEach { destination ->
                        var focused by remember(destination) { mutableStateOf(false) }
                        val isSelected = selectedRoute == destination.route
                        Button(
                            onClick = {
                                navigationRequesters.getValue(destination).requestFocus()
                                selectedRoute = destination.route
                                onDestinationChanged(destination)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = if (isSelected) {
                                    NamazTvTheme.colors.accentSoft
                                } else {
                                    Color.Transparent
                                },
                                contentColor = NamazTvTheme.colors.textPrimary,
                                focusedContainerColor = NamazTvTheme.colors.accent,
                                focusedContentColor = NamazTvTheme.colors.backgroundBottom,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(destination.navigationTestTag)
                                .semantics { selected = isSelected }
                                .focusRequester(navigationRequesters.getValue(destination))
                                .focusProperties {
                                    destination.previous?.let { up = navigationRequesters.getValue(it) }
                                    destination.next?.let { down = navigationRequesters.getValue(it) }
                                    right = pageActionRequester
                                }
                                .onFocusChanged { focusState ->
                                    val gainedFocus = focusState.isFocused && !focused
                                    focused = focusState.isFocused
                                    if (gainedFocus && selectedRoute != destination.route) {
                                        selectedRoute = destination.route
                                        onDestinationChanged(destination)
                                    }
                                }
                                .border(
                                    width = when {
                                        focused -> 3.dp
                                        isSelected -> 1.dp
                                        else -> 0.dp
                                    },
                                    color = when {
                                        focused -> NamazTvTheme.colors.focus
                                        isSelected -> NamazTvTheme.colors.accentOutline
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Text(appString(destination.titleRes))
                        }
                    }
                }
            }

            Spacer(Modifier.width(56.dp))

            TvGlassPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(SETTINGS_CONTENT_PANEL_TAG),
                radius = 24.dp,
            ) {
                SettingsPage(
                    destination = selectedDestination,
                    navigationRequester = navigationRequesters.getValue(selectedDestination),
                    pageActionRequester = pageActionRequester,
                    returnActionRequester = returnActionRequester,
                    onExit = onExit,
                    campaignPreview = campaignPreview,
                    schedule = schedule,
                    preferences = preferences,
                    appVersion = appVersion,
                    pilotLocalRuntime = pilotLocalRuntime,
                    onScreenRetentionShiftChanged = onScreenRetentionShiftChanged,
                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                    onLanguageChanged = onLanguageChanged,
                    onQrConfigurationChanged = onQrConfigurationChanged,
                    onIqamahTimesChanged = onIqamahTimesChanged,
                    onOpenSystemSettings = onOpenSystemSettings,
                    modifier = Modifier.padding(28.dp),
                )
            }
        }
    }
}

private data class LocalSettingsAction(
    val label: String,
    val invoke: () -> Unit,
    val testTag: String = SETTINGS_LOCAL_ACTION_TEST_TAG,
    val enabled: Boolean = true,
)

@Composable
private fun SettingsPage(
    destination: SettingsDestination,
    navigationRequester: FocusRequester,
    pageActionRequester: FocusRequester,
    returnActionRequester: FocusRequester,
    onExit: () -> Unit,
    campaignPreview: QrCampaignUiState?,
    schedule: LocalPrayerSchedule?,
    preferences: OperatorPreferences,
    appVersion: String,
    pilotLocalRuntime: Boolean,
    onScreenRetentionShiftChanged: ((Boolean) -> Unit)?,
    onBackgroundStyleChanged: ((String) -> Unit)?,
    onLanguageChanged: ((String) -> Unit)?,
    onQrConfigurationChanged: ((OperatorQrConfiguration) -> Unit)?,
    onIqamahTimesChanged: ((OperatorIqamahTimes) -> Unit)?,
    onOpenSystemSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val language = AppLanguage.fromTag(preferences.languageTag)
    val backgroundStyle = TvBackgroundStyle.fromId(preferences.backgroundStyleId)
    var qrUrl by rememberSaveable(preferences.qrConfiguration.httpsUrl) {
        mutableStateOf(preferences.qrConfiguration.httpsUrl)
    }
    var qrTitle by rememberSaveable(preferences.qrConfiguration.title) {
        mutableStateOf(preferences.qrConfiguration.title)
    }
    var qrMessage by rememberSaveable(preferences.qrConfiguration.message) {
        mutableStateOf(preferences.qrConfiguration.message)
    }
    var fajrIqamah by rememberSaveable(preferences.iqamahTimes.fajr) {
        mutableStateOf(preferences.iqamahTimes.fajr)
    }
    var dhuhrIqamah by rememberSaveable(preferences.iqamahTimes.dhuhr) {
        mutableStateOf(preferences.iqamahTimes.dhuhr)
    }
    var asrIqamah by rememberSaveable(preferences.iqamahTimes.asr) {
        mutableStateOf(preferences.iqamahTimes.asr)
    }
    var maghribIqamah by rememberSaveable(preferences.iqamahTimes.maghrib) {
        mutableStateOf(preferences.iqamahTimes.maghrib)
    }
    var ishaIqamah by rememberSaveable(preferences.iqamahTimes.isha) {
        mutableStateOf(preferences.iqamahTimes.isha)
    }
    val qrDraft = OperatorQrConfiguration(qrUrl, qrTitle, qrMessage)
    val iqamahDraft = OperatorIqamahTimes(
        fajr = fajrIqamah,
        dhuhr = dhuhrIqamah,
        asr = asrIqamah,
        maghrib = maghribIqamah,
        isha = ishaIqamah,
    )
    val isEditor = destination == SettingsDestination.CAMPAIGNS ||
        destination == SettingsDestination.IQAMAH
    val localActions = when (destination) {
        SettingsDestination.CAMPAIGNS -> onQrConfigurationChanged?.let { change ->
            listOf(
                LocalSettingsAction(
                    label = appString(R.string.save_qr_settings),
                    invoke = { change(qrDraft) },
                    testTag = SETTINGS_QR_SAVE_TAG,
                    enabled = isValidQrConfiguration(qrDraft),
                ),
            )
        }.orEmpty()
        SettingsDestination.IQAMAH -> onIqamahTimesChanged?.let { change ->
            listOf(
                LocalSettingsAction(
                    label = appString(R.string.save_iqamah_settings),
                    invoke = { change(iqamahDraft) },
                    testTag = SETTINGS_IQAMAH_SAVE_TAG,
                    enabled = isValidIqamahTimes(iqamahDraft),
                ),
            )
        }.orEmpty()
        SettingsDestination.APPEARANCE -> buildList {
            onScreenRetentionShiftChanged?.let { change ->
                add(
                    LocalSettingsAction(
                        label = appString(
                            if (preferences.screenRetentionShiftEnabled) {
                                R.string.action_disable_screen_shift
                            } else {
                                R.string.action_enable_screen_shift
                            },
                        ),
                        invoke = { change(!preferences.screenRetentionShiftEnabled) },
                    ),
                )
            }
            onBackgroundStyleChanged?.let { change ->
                add(
                    LocalSettingsAction(
                        label = appString(
                            R.string.action_switch_background,
                            appString(backgroundStyle.next.labelRes),
                        ),
                        invoke = { change(backgroundStyle.next.id) },
                        testTag = SETTINGS_BACKGROUND_ACTION_TEST_TAG,
                    ),
                )
            }
        }
        SettingsDestination.LANGUAGE -> onLanguageChanged?.let { change ->
            listOf(
                LocalSettingsAction(
                    label = appString(
                        R.string.action_switch_language,
                        appString(language.next.displayNameRes),
                    ),
                    invoke = { change(language.next.tag) },
                ),
            )
        }.orEmpty()
        SettingsDestination.KIOSK -> onOpenSystemSettings?.let { open ->
            listOf(LocalSettingsAction(appString(R.string.action_open_system_settings), open))
        }.orEmpty()
        else -> emptyList()
    }
    val secondaryActionRequesters = remember(destination, localActions.size) {
        List((localActions.size - 1).coerceAtLeast(0)) { FocusRequester() }
    }
    val editorSaveRequester = remember(destination) { FocusRequester() }
    val actionRequesters = if (isEditor) {
        listOf(editorSaveRequester) + secondaryActionRequesters
    } else {
        listOf(pageActionRequester) + secondaryActionRequesters
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val compactPreview = maxHeight < 600.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (compactPreview) 10.dp else 16.dp),
        ) {
            Text(
                text = appString(destination.titleRes),
                modifier = Modifier.semantics { heading() },
                color = NamazTvTheme.colors.textPrimary,
                fontSize = if (compactPreview) 34.sp else 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = appString(destination.descriptionRes),
                color = NamazTvTheme.colors.textSecondary,
                fontSize = if (compactPreview) 19.sp else 24.sp,
                lineHeight = if (compactPreview) 24.sp else 32.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SettingsContent(
                destination = destination,
                schedule = schedule,
                preferences = preferences,
                appVersion = appVersion,
                pilotLocalRuntime = pilotLocalRuntime,
                campaignPreview = campaignPreview,
                qrConfiguration = qrDraft,
                onQrConfigurationChange = { updated ->
                    qrUrl = updated.httpsUrl
                    qrTitle = updated.title
                    qrMessage = updated.message
                },
                iqamahTimes = iqamahDraft,
                onIqamahTimesChange = { updated ->
                    fajrIqamah = updated.fajr
                    dhuhrIqamah = updated.dhuhr
                    asrIqamah = updated.asr
                    maghribIqamah = updated.maghrib
                    ishaIqamah = updated.isha
                },
                entryRequester = pageActionRequester,
                saveRequester = actionRequesters.firstOrNull() ?: returnActionRequester,
                compact = compactPreview,
                modifier = Modifier.weight(1f),
            )
            localActions.forEachIndexed { index, action ->
                Button(
                    onClick = action.invoke,
                    enabled = action.enabled,
                    colors = ButtonDefaults.colors(
                        containerColor = NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.72f),
                        contentColor = NamazTvTheme.colors.textPrimary,
                        focusedContainerColor = NamazTvTheme.colors.accent,
                        focusedContentColor = NamazTvTheme.colors.backgroundBottom,
                    ),
                    modifier = Modifier
                        .testTag(action.testTag)
                        .focusRequester(actionRequesters[index])
                        .focusProperties {
                            left = navigationRequester
                            if (index > 0) up = actionRequesters[index - 1]
                            down = actionRequesters.getOrNull(index + 1) ?: returnActionRequester
                        },
                ) {
                    Text(action.label)
                }
            }
            Button(
                onClick = onExit,
                colors = ButtonDefaults.colors(
                    containerColor = NamazTvTheme.colors.accentSoft,
                    contentColor = NamazTvTheme.colors.accent,
                    focusedContainerColor = NamazTvTheme.colors.accent,
                    focusedContentColor = NamazTvTheme.colors.backgroundBottom,
                ),
                modifier = Modifier
                    .testTag(SETTINGS_PAGE_ACTION_TEST_TAG)
                    .focusRequester(
                        if (localActions.isEmpty() && !isEditor) {
                            pageActionRequester
                        } else {
                            returnActionRequester
                        },
                    )
                    .focusProperties {
                        left = navigationRequester
                        localActions.lastOrNull()?.let { up = actionRequesters.last() }
                    },
            ) {
                Text(appString(R.string.return_to_display))
            }
        }
    }
}

@Composable
private fun SettingsContent(
    destination: SettingsDestination,
    schedule: LocalPrayerSchedule?,
    preferences: OperatorPreferences,
    appVersion: String,
    pilotLocalRuntime: Boolean,
    campaignPreview: QrCampaignUiState?,
    qrConfiguration: OperatorQrConfiguration,
    onQrConfigurationChange: (OperatorQrConfiguration) -> Unit,
    iqamahTimes: OperatorIqamahTimes,
    onIqamahTimesChange: (OperatorIqamahTimes) -> Unit,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    compact: Boolean,
    modifier: Modifier,
) {
    val backgroundStyle = TvBackgroundStyle.fromId(preferences.backgroundStyleId)
    if (destination == SettingsDestination.CAMPAIGNS) {
        QrSettingsEditor(
            configuration = qrConfiguration,
            onConfigurationChange = onQrConfigurationChange,
            entryRequester = entryRequester,
            saveRequester = saveRequester,
            campaignPreview = campaignPreview,
            compact = compact,
            modifier = modifier,
        )
        return
    }
    if (destination == SettingsDestination.IQAMAH) {
        IqamahSettingsEditor(
            times = iqamahTimes,
            onTimesChange = onIqamahTimesChange,
            entryRequester = entryRequester,
            saveRequester = saveRequester,
            compact = compact,
            modifier = modifier,
        )
        return
    }
    if (schedule == null) {
        Text(
            text = appString(R.string.no_active_schedule),
            modifier = modifier,
            color = NamazTvTheme.colors.warning,
            fontSize = 22.sp,
        )
        return
    }
    val diagnostics = schedule.diagnostics
    val details = when (destination) {
        SettingsDestination.MOSQUE -> listOf(
            R.string.field_mosque to schedule.mosqueName,
            R.string.field_location to (schedule.locality ?: appString(R.string.value_not_available)),
            R.string.field_timezone to schedule.timezoneId,
            R.string.field_coverage to "${schedule.coverageFrom} — ${schedule.coverageTo}",
        )
        SettingsDestination.SOURCE -> listOf(
            R.string.field_authority to (schedule.attribution ?: schedule.authorityName),
            R.string.field_source_type to sourceKindLabel(schedule.sourceKind),
            R.string.field_approval to appString(
                if (diagnostics?.approvalStatus == "approved") R.string.value_approved else R.string.value_not_approved,
            ),
            R.string.field_source_id to schedule.sourceId,
            R.string.field_raw_hash to (diagnostics?.rawSha256 ?: appString(R.string.value_not_available)),
        )
        SettingsDestination.IQAMAH -> emptyList()
        SettingsDestination.APPEARANCE -> listOf(
            R.string.field_theme to appString(R.string.value_dark_theme),
            R.string.field_background to appString(backgroundStyle.labelRes),
            R.string.field_screen_shift to appString(
                if (preferences.screenRetentionShiftEnabled) R.string.value_screen_shift_on else R.string.value_screen_shift_off,
            ),
        )
        SettingsDestination.CAMPAIGNS -> emptyList()
        SettingsDestination.LANGUAGE -> {
            val language = AppLanguage.fromTag(preferences.languageTag)
            listOf(
                R.string.field_language to appString(language.displayNameRes),
                R.string.field_supported_languages to appString(R.string.supported_languages),
            )
        }
        SettingsDestination.KIOSK -> listOf(
            R.string.field_autostart to appString(R.string.autostart_not_configured),
            R.string.field_kiosk to appString(R.string.kiosk_not_active),
            R.string.field_installation_mode to appString(
                if (pilotLocalRuntime) R.string.pilot_local_build else R.string.release_build,
            ),
        )
        SettingsDestination.DIAGNOSTICS -> listOf(
            R.string.field_snapshot_id to schedule.snapshotId,
            R.string.field_app_version to appVersion,
            R.string.field_parser to (diagnostics?.parserVersion ?: appString(R.string.value_not_available)),
            R.string.field_approval_id to (diagnostics?.approvalId ?: appString(R.string.value_not_available)),
            R.string.field_signing_key to (diagnostics?.signingKeyId ?: appString(R.string.value_not_available)),
            R.string.field_data_state to appString(
                if (diagnostics?.dataClassification == "production" && diagnostics.approvalStatus == "approved") {
                    R.string.data_state_approved_real
                } else {
                    R.string.data_state_synthetic
                },
            ),
        )
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        details.forEach { (label, value) -> SettingsDetail(appString(label), value, compact) }
    }
}

@Composable
private fun SettingsDetail(label: String, value: String, compact: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.36f),
            color = NamazTvTheme.colors.textSecondary,
            fontSize = if (compact) 16.sp else 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.64f),
            color = NamazTvTheme.colors.textPrimary,
            fontSize = if (compact) 17.sp else 21.sp,
            fontFamily = if (value.matches(Regex("[0-9a-f]{32,}"))) FontFamily.Monospace else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun sourceKindLabel(kind: String): String = appString(
    when (kind) {
        "official_file" -> R.string.value_official_file
        "manual_import" -> R.string.value_manual_import
        "calculation_profile" -> R.string.value_calculation
        else -> R.string.value_other_source
    },
)
