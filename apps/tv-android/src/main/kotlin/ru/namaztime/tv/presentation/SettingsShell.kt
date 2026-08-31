package ru.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import ru.namaztime.tv.AppBuildIdentity
import ru.namaztime.tv.R
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.OperatorIqamahConfiguration
import ru.namaztime.tv.repository.OperatorMosquePresentationIdentity
import ru.namaztime.tv.repository.OperatorDisplayMode
import ru.namaztime.tv.repository.OperatorDonationConfiguration
import ru.namaztime.tv.repository.OperatorPreferences
import ru.namaztime.tv.repository.OperatorQrConfiguration
import ru.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.OperatorImageSlot
import ru.namaztime.tv.repository.isValidIqamahConfiguration
import ru.namaztime.tv.repository.isValidMosquePresentationIdentity
import ru.namaztime.tv.repository.isValidDonationConfiguration
import ru.namaztime.tv.repository.isValidQrConfiguration

const val SETTINGS_PAGE_ACTION_TEST_TAG = "settings-page-primary-action"
const val SETTINGS_LOCAL_ACTION_TEST_TAG = "settings-page-local-action"
const val SETTINGS_BACKGROUND_ACTION_TEST_TAG = "settings-background-action"
const val SETTINGS_BACKGROUND_SELECTED_PREVIEW_TAG = "settings-background-selected-preview"
const val SETTINGS_BACKGROUND_FILMSTRIP_TAG = "settings-background-filmstrip"
const val SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX = "settings-background-preview-"
const val SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG = "settings-custom-background-picker"
const val SETTINGS_SHELL_TAG = "settings-shell"
const val SETTINGS_NAVIGATION_PANEL_TAG = "settings-navigation-panel"
const val SETTINGS_CONTENT_PANEL_TAG = "settings-content-panel"
const val SETTINGS_DEVICE_SETUP_ACTION_TAG = "settings-device-setup-action"

data class OperatorImagePickerFocusRequest(
    val slot: OperatorImageSlot,
    val token: Long,
)

@Composable
fun SettingsShell(
    initialDestination: SettingsDestination,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    campaignPreview: QrCampaignUiState? = null,
    schedule: LocalPrayerSchedule? = null,
    preferences: OperatorPreferences = OperatorPreferences(),
    buildIdentity: AppBuildIdentity? = null,
    pilotLocalRuntime: Boolean = false,
    onScreenRetentionShiftChanged: ((Boolean) -> Unit)? = null,
    onMosquePresentationIdentityChanged: ((OperatorMosquePresentationIdentity) -> Unit)? = null,
    onOpenDeviceSetup: (() -> Unit)? = null,
    onBackgroundStyleChanged: ((String) -> Unit)? = null,
    onLanguageChanged: ((String) -> Unit)? = null,
    onQrConfigurationChanged: ((OperatorQrConfiguration) -> Unit)? = null,
    onIqamahConfigurationChanged: ((OperatorIqamahConfiguration) -> Unit)? = null,
    onDonationConfigurationChanged: ((OperatorDonationConfiguration) -> Unit)? = null,
    onDonationDisplayModeChanged: ((OperatorDonationConfiguration, OperatorDisplayMode) -> Unit)? = null,
    onPickCustomBackground: (() -> Unit)? = null,
    onPickCustomDonationImage: (() -> Unit)? = null,
    customAssetVersion: Long = 0L,
    donationAssetVersion: Long = 0L,
    imagePickerFocusRequest: OperatorImagePickerFocusRequest? = null,
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

    LaunchedEffect(imagePickerFocusRequest?.token) {
        val destination = when (imagePickerFocusRequest?.slot) {
            OperatorImageSlot.BACKGROUND -> SettingsDestination.APPEARANCE
            OperatorImageSlot.DONATION -> SettingsDestination.DONATION
            null -> null
        }
        destination?.let {
            withFrameNanos { }
            withFrameNanos { }
            selectedRoute = it.route
        }
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
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = appString(R.string.settings_title),
                        modifier = Modifier
                            .semantics { heading() }
                            .padding(bottom = 4.dp),
                        color = NamazTvTheme.colors.textPrimary,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SettingsDestination.entries.forEach { destination ->
                        val isSelected = selectedRoute == destination.route
                        val navigationShape = RoundedCornerShape(12.dp)
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
                            shape = ButtonDefaults.shape(
                                shape = navigationShape,
                                focusedShape = navigationShape,
                                pressedShape = navigationShape,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag(destination.navigationTestTag)
                                .semantics { selected = isSelected }
                                .focusRequester(navigationRequesters.getValue(destination))
                                .focusProperties {
                                    destination.previous?.let { up = navigationRequesters.getValue(it) }
                                    destination.next?.let { down = navigationRequesters.getValue(it) }
                                    right = pageActionRequester
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && selectedRoute != destination.route) {
                                        selectedRoute = destination.route
                                        onDestinationChanged(destination)
                                    }
                                },
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
                    buildIdentity = buildIdentity,
                    pilotLocalRuntime = pilotLocalRuntime,
                    onScreenRetentionShiftChanged = onScreenRetentionShiftChanged,
                    onMosquePresentationIdentityChanged = onMosquePresentationIdentityChanged,
                    onOpenDeviceSetup = onOpenDeviceSetup,
                    onBackgroundStyleChanged = onBackgroundStyleChanged,
                    onLanguageChanged = onLanguageChanged,
                    onQrConfigurationChanged = onQrConfigurationChanged,
                    onIqamahConfigurationChanged = onIqamahConfigurationChanged,
                    onDonationConfigurationChanged = onDonationConfigurationChanged,
                    onDonationDisplayModeChanged = onDonationDisplayModeChanged,
                    onPickCustomBackground = onPickCustomBackground,
                    onPickCustomDonationImage = onPickCustomDonationImage,
                    customAssetVersion = customAssetVersion,
                    donationAssetVersion = donationAssetVersion,
                    imagePickerFocusRequest = imagePickerFocusRequest,
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
    buildIdentity: AppBuildIdentity?,
    pilotLocalRuntime: Boolean,
    onScreenRetentionShiftChanged: ((Boolean) -> Unit)?,
    onMosquePresentationIdentityChanged: ((OperatorMosquePresentationIdentity) -> Unit)?,
    onOpenDeviceSetup: (() -> Unit)?,
    onBackgroundStyleChanged: ((String) -> Unit)?,
    onLanguageChanged: ((String) -> Unit)?,
    onQrConfigurationChanged: ((OperatorQrConfiguration) -> Unit)?,
    onIqamahConfigurationChanged: ((OperatorIqamahConfiguration) -> Unit)?,
    onDonationConfigurationChanged: ((OperatorDonationConfiguration) -> Unit)?,
    onDonationDisplayModeChanged: ((OperatorDonationConfiguration, OperatorDisplayMode) -> Unit)?,
    onPickCustomBackground: (() -> Unit)?,
    onPickCustomDonationImage: (() -> Unit)?,
    customAssetVersion: Long,
    donationAssetVersion: Long,
    imagePickerFocusRequest: OperatorImagePickerFocusRequest?,
    onOpenSystemSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val language = AppLanguage.fromTag(preferences.languageTag)
    val backgroundStyle = TvBackgroundStyle.fromId(preferences.backgroundStyleId)
    var mosqueDisplayName by rememberSaveable(
        preferences.mosquePresentationIdentity.displayName,
    ) {
        mutableStateOf(preferences.mosquePresentationIdentity.displayName)
    }
    var mosqueDisplayAddress by rememberSaveable(
        preferences.mosquePresentationIdentity.displayAddress,
    ) {
        mutableStateOf(preferences.mosquePresentationIdentity.displayAddress)
    }
    var qrUrl by rememberSaveable(preferences.qrConfiguration.httpsUrl) {
        mutableStateOf(preferences.qrConfiguration.httpsUrl)
    }
    var qrTitle by rememberSaveable(preferences.qrConfiguration.title) {
        mutableStateOf(preferences.qrConfiguration.title)
    }
    var qrMessage by rememberSaveable(preferences.qrConfiguration.message) {
        mutableStateOf(preferences.qrConfiguration.message)
    }
    var donationUrl by rememberSaveable(preferences.donationConfiguration.httpsUrl) {
        mutableStateOf(preferences.donationConfiguration.httpsUrl)
    }
    var donationRecipient by rememberSaveable(preferences.donationConfiguration.recipient) {
        mutableStateOf(preferences.donationConfiguration.recipient)
    }
    var donationBank by rememberSaveable(preferences.donationConfiguration.bank) {
        mutableStateOf(preferences.donationConfiguration.bank)
    }
    var donationCardNumber by rememberSaveable(preferences.donationConfiguration.cardNumber) {
        mutableStateOf(preferences.donationConfiguration.cardNumber)
    }
    var donationPhone by rememberSaveable(preferences.donationConfiguration.phone) {
        mutableStateOf(preferences.donationConfiguration.phone)
    }
    var donationGratitudeMessage by rememberSaveable(
        preferences.donationConfiguration.gratitudeMessage,
    ) {
        mutableStateOf(preferences.donationConfiguration.gratitudeMessage)
    }
    var donationImageStyleId by rememberSaveable(preferences.donationConfiguration.imageStyleId) {
        mutableStateOf(preferences.donationConfiguration.imageStyleId)
    }
    var fajrIqamah by rememberSaveable(preferences.iqamahConfiguration.fajrOffsetMinutes) {
        mutableStateOf(preferences.iqamahConfiguration.fajrOffsetMinutes)
    }
    var dhuhrIqamah by rememberSaveable(preferences.iqamahConfiguration.dhuhrFixedTimeMinutes) {
        mutableStateOf(preferences.iqamahConfiguration.dhuhrFixedTimeMinutes)
    }
    var asrIqamah by rememberSaveable(preferences.iqamahConfiguration.asrOffsetMinutes) {
        mutableStateOf(preferences.iqamahConfiguration.asrOffsetMinutes)
    }
    var maghribIqamah by rememberSaveable(preferences.iqamahConfiguration.maghribOffsetMinutes) {
        mutableStateOf(preferences.iqamahConfiguration.maghribOffsetMinutes)
    }
    var ishaIqamah by rememberSaveable(preferences.iqamahConfiguration.ishaOffsetMinutes) {
        mutableStateOf(preferences.iqamahConfiguration.ishaOffsetMinutes)
    }
    val mosqueIdentityDraft = OperatorMosquePresentationIdentity(
        displayName = mosqueDisplayName,
        displayAddress = mosqueDisplayAddress,
    )
    val qrDraft = OperatorQrConfiguration(qrUrl, qrTitle, qrMessage)
    val donationDraft = OperatorDonationConfiguration(
        httpsUrl = donationUrl,
        recipient = donationRecipient,
        bank = donationBank,
        cardNumber = donationCardNumber,
        phone = donationPhone,
        gratitudeMessage = donationGratitudeMessage,
        imageStyleId = donationImageStyleId,
    )
    val iqamahDraft = OperatorIqamahConfiguration(
        fajrOffsetMinutes = fajrIqamah,
        dhuhrFixedTimeMinutes = dhuhrIqamah,
        asrOffsetMinutes = asrIqamah,
        maghribOffsetMinutes = maghribIqamah,
        ishaOffsetMinutes = ishaIqamah,
    )
    val isEditor = (destination == SettingsDestination.MOSQUE && schedule != null) ||
        destination == SettingsDestination.CAMPAIGNS ||
        destination == SettingsDestination.IQAMAH ||
        destination == SettingsDestination.DONATION
    val localActions = when (destination) {
        SettingsDestination.MOSQUE -> buildList {
            if (schedule != null) {
                onMosquePresentationIdentityChanged?.let { change ->
                    add(
                        LocalSettingsAction(
                            label = appString(R.string.save_mosque_identity_settings),
                            invoke = { change(mosqueIdentityDraft) },
                            testTag = SETTINGS_MOSQUE_IDENTITY_SAVE_TAG,
                            enabled = isValidMosquePresentationIdentity(mosqueIdentityDraft),
                        ),
                    )
                }
            }
            onOpenDeviceSetup?.let { open ->
                add(
                    LocalSettingsAction(
                        label = appString(R.string.device_setup_open_action),
                        invoke = open,
                        testTag = SETTINGS_DEVICE_SETUP_ACTION_TAG,
                    ),
                )
            }
        }
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
        SettingsDestination.IQAMAH -> onIqamahConfigurationChanged?.let { change ->
            listOf(
                LocalSettingsAction(
                    label = appString(R.string.save_iqamah_settings),
                    invoke = { change(iqamahDraft) },
                    testTag = SETTINGS_IQAMAH_SAVE_TAG,
                    enabled = isValidIqamahConfiguration(iqamahDraft),
                ),
            )
        }.orEmpty()
        SettingsDestination.APPEARANCE -> buildList {
            onPickCustomBackground?.let { pick ->
                add(
                    LocalSettingsAction(
                        label = appString(R.string.action_choose_custom_background),
                        invoke = pick,
                        testTag = SETTINGS_CUSTOM_BACKGROUND_PICKER_TAG,
                    ),
                )
            }
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
        }
        SettingsDestination.DONATION -> buildList {
            onPickCustomDonationImage?.let { pick ->
                add(
                    LocalSettingsAction(
                        label = appString(R.string.choose_donation_image),
                        invoke = pick,
                        testTag = SETTINGS_DONATION_PICKER_TAG,
                    ),
                )
            }
            onDonationConfigurationChanged?.let { change ->
                add(
                    LocalSettingsAction(
                        label = appString(R.string.save_donation_settings),
                        invoke = { change(donationDraft) },
                        testTag = SETTINGS_DONATION_SAVE_TAG,
                        enabled = isValidDonationConfiguration(donationDraft),
                    ),
                )
            }
            onDonationDisplayModeChanged?.let { change ->
                val nextMode = if (preferences.displayMode == OperatorDisplayMode.DONATION) {
                    OperatorDisplayMode.SCHEDULE
                } else {
                    OperatorDisplayMode.DONATION
                }
                add(
                    LocalSettingsAction(
                        label = appString(
                            if (nextMode == OperatorDisplayMode.DONATION) {
                                R.string.show_donation_screen
                            } else {
                                R.string.show_prayer_schedule
                            },
                        ),
                        invoke = { change(donationDraft, nextMode) },
                        testTag = SETTINGS_DONATION_MODE_TAG,
                        enabled = nextMode == OperatorDisplayMode.SCHEDULE ||
                            (isValidDonationConfiguration(donationDraft) && !donationDraft.isEmpty),
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
    val appearanceActionRequesters = remember(destination, localActions.size) {
        List(localActions.size) { FocusRequester() }
    }
    val actionRequesters = if (isEditor) {
        listOf(editorSaveRequester) + secondaryActionRequesters
    } else if (destination == SettingsDestination.APPEARANCE) {
        appearanceActionRequesters
    } else {
        listOf(pageActionRequester) + secondaryActionRequesters
    }

    LaunchedEffect(imagePickerFocusRequest?.token, destination, actionRequesters) {
        val expectedDestination = when (imagePickerFocusRequest?.slot) {
            OperatorImageSlot.BACKGROUND -> SettingsDestination.APPEARANCE
            OperatorImageSlot.DONATION -> SettingsDestination.DONATION
            null -> null
        }
        if (destination == expectedDestination && actionRequesters.isNotEmpty()) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { actionRequesters.first().requestFocus() }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val compactPreview = maxHeight < 600.dp
        val compactLongHeading = compactPreview && destination == SettingsDestination.MOSQUE
        val compactSingleLineDescription = compactPreview &&
            (destination == SettingsDestination.APPEARANCE ||
                destination == SettingsDestination.IQAMAH ||
                destination == SettingsDestination.DONATION)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(
                if (compactLongHeading) 8.dp else if (compactPreview) 10.dp else 16.dp,
            ),
        ) {
            Text(
                text = appString(destination.titleRes),
                modifier = Modifier.semantics { heading() },
                color = NamazTvTheme.colors.textPrimary,
                fontSize = if (compactLongHeading) 29.sp else if (compactPreview) 34.sp else 42.sp,
                lineHeight = if (compactLongHeading) 36.sp else if (compactPreview) 42.sp else 50.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = appString(destination.descriptionRes),
                color = NamazTvTheme.colors.textSecondary,
                fontSize = if (compactSingleLineDescription) {
                    17.sp
                } else if (compactPreview) {
                    19.sp
                } else {
                    24.sp
                },
                lineHeight = if (compactSingleLineDescription) {
                    22.sp
                } else if (compactPreview) {
                    24.sp
                } else {
                    32.sp
                },
                maxLines = if (compactSingleLineDescription) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            SettingsContent(
                destination = destination,
                schedule = schedule,
                preferences = preferences,
                buildIdentity = buildIdentity,
                pilotLocalRuntime = pilotLocalRuntime,
                campaignPreview = campaignPreview,
                mosquePresentationIdentity = mosqueIdentityDraft,
                onMosquePresentationIdentityChange = { updated ->
                    mosqueDisplayName = updated.displayName
                    mosqueDisplayAddress = updated.displayAddress
                },
                qrConfiguration = qrDraft,
                onQrConfigurationChange = { updated ->
                    qrUrl = updated.httpsUrl
                    qrTitle = updated.title
                    qrMessage = updated.message
                },
                iqamahConfiguration = iqamahDraft,
                onIqamahConfigurationChange = { updated ->
                    fajrIqamah = updated.fajrOffsetMinutes
                    dhuhrIqamah = updated.dhuhrFixedTimeMinutes
                    asrIqamah = updated.asrOffsetMinutes
                    maghribIqamah = updated.maghribOffsetMinutes
                    ishaIqamah = updated.ishaOffsetMinutes
                },
                donationConfiguration = donationDraft,
                onDonationConfigurationChange = { updated ->
                    donationUrl = updated.httpsUrl
                    donationRecipient = updated.recipient
                    donationBank = updated.bank
                    donationCardNumber = updated.cardNumber
                    donationPhone = updated.phone
                    donationGratitudeMessage = updated.gratitudeMessage
                    donationImageStyleId = updated.imageStyleId
                },
                entryRequester = pageActionRequester,
                saveRequester = actionRequesters.firstOrNull() ?: returnActionRequester,
                onBackgroundStyleChanged = onBackgroundStyleChanged,
                customAssetVersion = customAssetVersion,
                donationAssetVersion = donationAssetVersion,
                compact = compactPreview,
                modifier = Modifier.weight(1f),
            )
            if (destination == SettingsDestination.DONATION || destination == SettingsDestination.APPEARANCE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                                .weight(
                                    if (destination == SettingsDestination.APPEARANCE && index == 0) {
                                        1.2f
                                    } else if (destination == SettingsDestination.APPEARANCE) {
                                        0.8f
                                    } else if (action.testTag == SETTINGS_DONATION_PICKER_TAG) {
                                        1.1f
                                    } else if (action.testTag == SETTINGS_DONATION_SAVE_TAG) {
                                        0.9f
                                    } else {
                                        1f
                                    },
                                )
                                .testTag(action.testTag)
                                .focusRequester(actionRequesters[index])
                                .focusProperties {
                                    left = actionRequesters.getOrNull(index - 1)
                                        ?: navigationRequester
                                    actionRequesters.getOrNull(index + 1)?.let { right = it }
                                    down = returnActionRequester
                                },
                        ) {
                            Text(
                                text = action.label,
                                fontSize = if (destination == SettingsDestination.DONATION && compactPreview) {
                                    14.sp
                                } else if (destination == SettingsDestination.APPEARANCE && compactPreview) {
                                    16.sp
                                } else {
                                    18.sp
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
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
                                down = actionRequesters.getOrNull(index + 1)
                                    ?: returnActionRequester
                            },
                    ) {
                        Text(action.label)
                    }
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
    buildIdentity: AppBuildIdentity?,
    pilotLocalRuntime: Boolean,
    campaignPreview: QrCampaignUiState?,
    mosquePresentationIdentity: OperatorMosquePresentationIdentity,
    onMosquePresentationIdentityChange: (OperatorMosquePresentationIdentity) -> Unit,
    qrConfiguration: OperatorQrConfiguration,
    onQrConfigurationChange: (OperatorQrConfiguration) -> Unit,
    iqamahConfiguration: OperatorIqamahConfiguration,
    onIqamahConfigurationChange: (OperatorIqamahConfiguration) -> Unit,
    donationConfiguration: OperatorDonationConfiguration,
    onDonationConfigurationChange: (OperatorDonationConfiguration) -> Unit,
    entryRequester: FocusRequester,
    saveRequester: FocusRequester,
    onBackgroundStyleChanged: ((String) -> Unit)?,
    customAssetVersion: Long,
    donationAssetVersion: Long,
    compact: Boolean,
    modifier: Modifier,
) {
    val backgroundStyle = TvBackgroundStyle.fromId(preferences.backgroundStyleId)
    if (destination == SettingsDestination.MOSQUE && schedule != null) {
        MosqueIdentitySettingsEditor(
            identity = mosquePresentationIdentity,
            onIdentityChange = onMosquePresentationIdentityChange,
            fallbackIdentity = schedule.toMosqueDisplayIdentity(),
            canonicalLocality = schedule.locality,
            timezoneId = schedule.timezoneId,
            entryRequester = entryRequester,
            saveRequester = saveRequester,
            compact = compact,
            modifier = modifier,
        )
        return
    }
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
            configuration = iqamahConfiguration,
            onConfigurationChange = onIqamahConfigurationChange,
            approvedDhuhrTimeMinutes = schedule.approvedDhuhrFixedTimeMinutes(),
            entryRequester = entryRequester,
            saveRequester = saveRequester,
            compact = compact,
            modifier = modifier,
        )
        return
    }
    if (destination == SettingsDestination.APPEARANCE) {
        AppearanceBackgroundGallery(
            selectedStyleId = preferences.backgroundStyleId,
            onStyleSelected = onBackgroundStyleChanged,
            entryRequester = entryRequester,
            nextRequester = saveRequester,
            customAssetVersion = customAssetVersion,
            compact = compact,
            modifier = modifier,
        )
        return
    }
    if (destination == SettingsDestination.DONATION) {
        DonationSettingsEditor(
            configuration = donationConfiguration,
            onConfigurationChange = onDonationConfigurationChange,
            entryRequester = entryRequester,
            saveRequester = saveRequester,
            customAssetVersion = donationAssetVersion,
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
    val displayIdentity = schedule.toMosqueDisplayIdentity()
    val details = when (destination) {
        SettingsDestination.MOSQUE -> listOf(
            R.string.field_mosque to displayIdentity.name,
            R.string.field_location to (displayIdentity.locality ?: appString(R.string.value_not_available)),
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
        SettingsDestination.DONATION -> emptyList()
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
            R.string.field_app_version to (
                buildIdentity?.versionLabel ?: appString(R.string.value_not_available)
            ),
            R.string.field_build_identity to buildIdentity?.let { identity ->
                appString(
                    R.string.value_build_identity,
                    identity.variant,
                    identity.shortCommit,
                    appString(
                        if (identity.dirty) R.string.value_build_dirty else R.string.value_build_clean,
                    ),
                )
            }.orEmpty().ifEmpty { appString(R.string.value_not_available) },
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

private fun LocalPrayerSchedule?.approvedDhuhrFixedTimeMinutes(): Int? = this
    ?.iqamahRules
    ?.asSequence()
    ?.filter { it.prayer == "dhuhr" && it.mode == "fixed_time" }
    ?.mapNotNull { rule ->
        rule.fixedTime?.split(':')?.takeIf { it.size == 2 }?.let { parts ->
            val hour = parts[0].toIntOrNull() ?: return@let null
            val minute = parts[1].toIntOrNull() ?: return@let null
            (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
        }
    }
    ?.distinct()
    ?.singleOrNull()

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
private fun AppearanceBackgroundGallery(
    selectedStyleId: String,
    onStyleSelected: ((String) -> Unit)?,
    entryRequester: FocusRequester,
    nextRequester: FocusRequester,
    customAssetVersion: Long,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val choices = remember { TvBackgroundStyle.entries.map { it.id } }
    val requesters = remember(entryRequester) {
        choices.associateWith { FocusRequester() }.toMutableMap().apply {
            this[choices.first()] = entryRequester
        }
    }
    val listState = rememberLazyListState()
    var focusedIndex by rememberSaveable {
        mutableStateOf(choices.indexOf(selectedStyleId).coerceAtLeast(0))
    }
    val customImage = rememberOperatorImageBitmap(
        slot = OperatorImageSlot.BACKGROUND,
        assetVersion = customAssetVersion,
    )

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 160.dp else 250.dp)
                .testTag(SETTINGS_BACKGROUND_SELECTED_PREVIEW_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(NamazTvTheme.colors.backgroundBottom)
                    .border(
                        width = 1.dp,
                        color = NamazTvTheme.colors.surfaceOutline,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ),
            ) {
                val selectedStyle = TvBackgroundStyle.entries.firstOrNull { it.id == selectedStyleId }
                if (selectedStyleId == CUSTOM_BACKGROUND_STYLE_ID && customImage != null) {
                    Image(
                        painter = BitmapPainter(customImage),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(
                            (selectedStyle ?: TvBackgroundStyle.entries.first()).drawableRes,
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NamazTvTheme.colors.backgroundTop.copy(alpha = 0.2f)),
                )
                AppearancePreviewChrome(compact = compact)
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 58.dp else 78.dp)
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(NamazTvTheme.colors.surfaceTop.copy(alpha = 0.72f))
                .testTag(SETTINGS_BACKGROUND_FILMSTRIP_TAG),
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                count = choices.size,
                key = { choices[it] },
            ) {
                val styleId = choices[it]
                val style = TvBackgroundStyle.entries.first { style -> style.id == styleId }
                val selected = selectedStyleId == styleId
                val focused = focusedIndex == it
                Button(
                    onClick = { onStyleSelected?.invoke(styleId) },
                    enabled = onStyleSelected != null,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        focusedContentColor = Color.White,
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(9.dp)),
                    modifier = Modifier
                        .width(if (compact) 96.dp else 132.dp)
                        .height(if (compact) 50.dp else 68.dp)
                        .testTag("$SETTINGS_BACKGROUND_PREVIEW_TAG_PREFIX$styleId")
                        .semantics { this.selected = selected }
                        .focusRequester(requesters.getValue(styleId))
                        .focusProperties {
                            choices.getOrNull(it - 1)?.let { left = requesters.getValue(it) }
                            choices.getOrNull(it + 1)?.let { right = requesters.getValue(it) }
                            down = nextRequester
                        }
                        .onFocusChanged { state ->
                            if (state.isFocused) focusedIndex = it
                        }
                        .border(
                            width = if (selected || focused) 3.dp else 1.dp,
                            color = if (selected || focused) {
                                NamazTvTheme.colors.accent
                            } else {
                                NamazTvTheme.colors.surfaceOutline
                            },
                            shape = RoundedCornerShape(9.dp),
                        ),
                ) {
                    Image(
                        painter = painterResource(style.drawableRes),
                        contentDescription = appString(style.labelRes),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearancePreviewChrome(compact: Boolean) {
    val radius = if (compact) 5.dp else 8.dp
    Column(
        modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(if (compact) 86.dp else 118.dp)
                .height(if (compact) 18.dp else 25.dp)
                .background(NamazTvTheme.colors.surfaceStrong.copy(alpha = 0.76f), RoundedCornerShape(50)),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Column(
                modifier = Modifier.weight(0.36f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
            ) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(NamazTvTheme.colors.surfaceTop.copy(alpha = 0.78f), RoundedCornerShape(radius))
                            .border(0.5.dp, NamazTvTheme.colors.surfaceOutline, RoundedCornerShape(radius)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
                    .background(NamazTvTheme.colors.surfaceTop.copy(alpha = 0.78f), RoundedCornerShape(radius))
                    .border(0.5.dp, NamazTvTheme.colors.surfaceOutline, RoundedCornerShape(radius)),
            )
        }
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
