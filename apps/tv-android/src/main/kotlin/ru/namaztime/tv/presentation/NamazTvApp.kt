package ru.namaztime.tv.presentation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import ru.namaztime.tv.BuildConfig
import ru.namaztime.tv.currentAppBuildIdentity
import ru.namaztime.tv.R
import ru.namaztime.tv.data.snapshot.SnapshotBootstrapState
import ru.namaztime.tv.domain.CampaignEngine
import ru.namaztime.tv.domain.CampaignPreview
import ru.namaztime.tv.domain.CampaignResolution
import ru.namaztime.tv.domain.PrayerTimeEngine
import ru.namaztime.tv.domain.PrayerTimeResolution
import ru.namaztime.tv.domain.QrCodeGenerator
import ru.namaztime.tv.repository.CorruptLocalSnapshotException
import ru.namaztime.tv.repository.EmptyPrayerScheduleRepository
import ru.namaztime.tv.repository.LocalPrayerSchedule
import ru.namaztime.tv.repository.OperatorPreferences
import ru.namaztime.tv.repository.OperatorPreferencesRepository
import ru.namaztime.tv.repository.AndroidOperatorImageAssetImporter
import ru.namaztime.tv.repository.AndroidOperatorImageSelectionEnvironment
import ru.namaztime.tv.repository.AndroidOperatorMediaImageCatalog
import ru.namaztime.tv.repository.CUSTOM_BACKGROUND_STYLE_ID
import ru.namaztime.tv.repository.CUSTOM_DONATION_IMAGE_STYLE_ID
import ru.namaztime.tv.repository.OperatorDisplayMode
import ru.namaztime.tv.repository.OperatorImageImportResult
import ru.namaztime.tv.repository.OperatorImageAssetImporter
import ru.namaztime.tv.repository.OperatorImageSelectionCoordinator
import ru.namaztime.tv.repository.OperatorImageSelectionDecision
import ru.namaztime.tv.repository.OperatorImageSelectionEnvironment
import ru.namaztime.tv.repository.OperatorImageSelectionFeedback
import ru.namaztime.tv.repository.OperatorImageSlot
import ru.namaztime.tv.repository.OperatorMediaImage
import ru.namaztime.tv.repository.OperatorMediaImageCatalog
import ru.namaztime.tv.repository.OperatorMediaImagePager
import ru.namaztime.tv.repository.OperatorMediaImagePickerContent
import ru.namaztime.tv.repository.OPERATOR_IMAGE_MIME_TYPES
import ru.namaztime.tv.repository.PrayerScheduleRepository
import ru.namaztime.tv.repository.toCampaignInputs
import ru.namaztime.tv.repository.toCampaignInput
import ru.namaztime.tv.repository.toDonationCampaignInput
import ru.namaztime.tv.repository.toTimeEngineInput
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
import kotlinx.serialization.Serializable

private const val DISPLAY_ROUTE = "display"
private const val SETTINGS_ROUTE = "settings"
@Serializable
internal data object DeviceSetupRoute
const val DISPLAY_UNAVAILABLE_TAG = "display-unavailable"
const val UNAVAILABLE_PANEL_TAG = "unavailable-panel"

private data class MediaImagePickerSession(
    val slot: OperatorImageSlot,
    val content: OperatorMediaImagePickerContent = OperatorMediaImagePickerContent(),
    val loading: Boolean = true,
)

@Composable
fun NamazTvApp(
    operatorPreferencesRepository: OperatorPreferencesRepository,
    prayerScheduleRepository: PrayerScheduleRepository = EmptyPrayerScheduleRepository,
    bootstrapState: Flow<SnapshotBootstrapState> = flowOf(
        SnapshotBootstrapState.Diagnostic("NO_LOCAL_SNAPSHOT"),
    ),
    deviceSetupController: DeviceSetupController? = null,
    clock: Clock = Clock.systemUTC(),
    tickIntervalMillis: Long? = 1_000L,
    imageSelectionEnvironment: OperatorImageSelectionEnvironment? = null,
    imageAssetImporter: OperatorImageAssetImporter? = null,
    mediaImageCatalog: OperatorMediaImageCatalog? = null,
) {
    val preferences by operatorPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = OperatorPreferences(),
    )
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageEnvironment = remember(context, imageSelectionEnvironment) {
        imageSelectionEnvironment ?: AndroidOperatorImageSelectionEnvironment(context)
    }
    val imageImporter = remember(context, imageAssetImporter) {
        imageAssetImporter ?: AndroidOperatorImageAssetImporter(context)
    }
    val mediaCatalog = remember(context, mediaImageCatalog) {
        mediaImageCatalog ?: AndroidOperatorMediaImageCatalog(context)
    }
    val mediaPager = remember(mediaCatalog) { OperatorMediaImagePager(mediaCatalog) }
    val imageSelectionCoordinator = remember { OperatorImageSelectionCoordinator() }
    var customAssetVersion by remember { mutableLongStateOf(0L) }
    var donationAssetVersion by remember { mutableLongStateOf(0L) }
    var pendingExternalSlot by remember { mutableStateOf<OperatorImageSlot?>(null) }
    var pendingPermissionSlot by remember { mutableStateOf<OperatorImageSlot?>(null) }
    var mediaPickerSession by remember { mutableStateOf<MediaImagePickerSession?>(null) }
    var imageSelectionFeedback by remember {
        mutableStateOf<OperatorImageSelectionFeedback?>(null)
    }
    var imageFocusToken by remember { mutableLongStateOf(0L) }
    var imagePickerFocusRequest by remember {
        mutableStateOf<OperatorImagePickerFocusRequest?>(null)
    }

    fun finishImageSelection(
        slot: OperatorImageSlot,
        feedback: OperatorImageSelectionFeedback?,
    ) {
        imageSelectionFeedback = feedback
        imageFocusToken += 1L
        imagePickerFocusRequest = OperatorImagePickerFocusRequest(slot, imageFocusToken)
    }

    fun importSelectedImage(slot: OperatorImageSlot, uri: Uri) {
        coroutineScope.launch {
            val importResult = try {
                imageImporter.import(slot, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OperatorImageImportResult.Inaccessible
            }
            val finalResult = if (importResult == OperatorImageImportResult.Imported) {
                try {
                    when (slot) {
                        OperatorImageSlot.BACKGROUND -> {
                            operatorPreferencesRepository.setBackgroundStyleId(
                                CUSTOM_BACKGROUND_STYLE_ID,
                            )
                            customAssetVersion += 1L
                        }
                        OperatorImageSlot.DONATION -> {
                            operatorPreferencesRepository.setDonationImageStyleId(
                                CUSTOM_DONATION_IMAGE_STYLE_ID,
                            )
                            donationAssetVersion += 1L
                        }
                    }
                    importResult
                } catch (_: IOException) {
                    OperatorImageImportResult.StorageFailed
                }
            } else {
                importResult
            }
            finishImageSelection(
                slot,
                imageSelectionCoordinator.importResultFeedback(finalResult),
            )
        }
    }

    fun openMediaStore(slot: OperatorImageSlot) {
        mediaPickerSession = MediaImagePickerSession(slot = slot)
        coroutineScope.launch {
            try {
                val content = mediaPager.loadNext()
                if (mediaPickerSession?.slot == slot) {
                    mediaPickerSession = MediaImagePickerSession(
                        slot = slot,
                        content = content,
                        loading = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                mediaPickerSession = null
                finishImageSelection(slot, OperatorImageSelectionFeedback.PERMISSION_DENIED)
            } catch (_: Exception) {
                mediaPickerSession = null
                finishImageSelection(slot, OperatorImageSelectionFeedback.INACCESSIBLE)
            }
        }
    }

    fun handleExternalPickerResult(uri: Uri?) {
        val slot = pendingExternalSlot ?: return
        pendingExternalSlot = null
        if (uri == null) {
            finishImageSelection(slot, null)
        } else {
            importSelectedImage(slot, uri)
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        ::handleExternalPickerResult,
    )
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
        ::handleExternalPickerResult,
    )
    val mediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val slot = pendingPermissionSlot ?: return@rememberLauncherForActivityResult
        pendingPermissionSlot = null
        if (granted) {
            openMediaStore(slot)
        } else {
            finishImageSelection(
                slot,
                imageSelectionCoordinator.permissionResultFeedback(granted = false),
            )
        }
    }
    val beginImageSelection: (OperatorImageSlot) -> Unit = begin@{ slot ->
        imageSelectionFeedback = null
        val decision = try {
            imageSelectionCoordinator.decide(imageEnvironment.capabilities())
        } catch (_: Exception) {
            finishImageSelection(slot, OperatorImageSelectionFeedback.INACCESSIBLE)
            return@begin
        }
        when (decision) {
            OperatorImageSelectionDecision.OpenDocument -> {
                pendingExternalSlot = slot
                documentPicker.launch(OPERATOR_IMAGE_MIME_TYPES)
            }
            OperatorImageSelectionDecision.PhotoPicker -> {
                pendingExternalSlot = slot
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            OperatorImageSelectionDecision.MediaStore -> openMediaStore(slot)
            is OperatorImageSelectionDecision.RequestPermission -> {
                pendingPermissionSlot = slot
                mediaPermission.launch(imageEnvironment.permissionName(decision.permission))
            }
        }
    }
    LaunchedEffect(imageSelectionFeedback) {
        val shown = imageSelectionFeedback ?: return@LaunchedEffect
        delay(5_000L)
        if (imageSelectionFeedback == shown) imageSelectionFeedback = null
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
    val donationQrState = remember(preferences.donationConfiguration, qrCodeGenerator) {
        preferences.donationConfiguration
            .takeUnless { it.isEmpty }
            ?.toDonationCampaignInput()
            ?.let(campaignEngine::preview)
            ?.let { it as? CampaignPreview.Valid }
            ?.campaign
            ?.let { campaign ->
                runCatching {
                    campaign.toQrCampaignUiState(
                        qrCode = qrCodeGenerator.generate(campaign.httpsUrl),
                        preview = false,
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
                            donationQrState = donationQrState,
                            donationAssetVersion = donationAssetVersion,
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
                            onOpenDeviceSetup = deviceSetupController?.let {
                                {
                                    navController.navigate(DeviceSetupRoute) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            campaignPreview = campaignPreview,
                            schedule = availableSchedule,
                            preferences = preferences,
                            buildIdentity = currentAppBuildIdentity(),
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
                            onMosquePresentationIdentityChanged = { identity ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository
                                            .setMosquePresentationIdentity(identity)
                                    } catch (_: IOException) {
                                        // The last valid local display identity remains active.
                                    } catch (_: IllegalArgumentException) {
                                        // Invalid local labels never replace canonical fallbacks.
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
                            onIqamahConfigurationChanged = { configuration ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository
                                            .setIqamahConfiguration(configuration)
                                    } catch (_: IOException) {
                                        // The last valid local iqamah settings remain active.
                                    } catch (_: IllegalArgumentException) {
                                        // Invalid operator input is rejected without changing the display.
                                    }
                                }
                            },
                            onDonationConfigurationChanged = { configuration ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository
                                            .setDonationConfiguration(configuration)
                                    } catch (_: IOException) {
                                        // The last valid donation screen remains active.
                                    } catch (_: IllegalArgumentException) {
                                        // Invalid operator input is rejected without state change.
                                    }
                                }
                            },
                            onDonationDisplayModeChanged = { configuration, mode ->
                                coroutineScope.launch {
                                    try {
                                        operatorPreferencesRepository
                                            .setDonationConfiguration(configuration)
                                        operatorPreferencesRepository.setDisplayMode(mode)
                                    } catch (_: IOException) {
                                        // A partial write cannot enable an invalid donation screen.
                                    } catch (_: IllegalArgumentException) {
                                        // Invalid content cannot replace the prayer schedule.
                                    }
                                }
                            },
                            onPickCustomBackground = {
                                beginImageSelection(OperatorImageSlot.BACKGROUND)
                            },
                            onPickCustomDonationImage = {
                                beginImageSelection(OperatorImageSlot.DONATION)
                            },
                            customAssetVersion = customAssetVersion,
                            donationAssetVersion = donationAssetVersion,
                            imagePickerFocusRequest = imagePickerFocusRequest,
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
                    if (deviceSetupController != null) {
                        composable<DeviceSetupRoute> {
                            val setupState by deviceSetupController.state.collectAsStateWithLifecycle()
                            val backOrExit = {
                                if (setupState.step == DeviceSetupStep.SEARCH) {
                                    deviceSetupController.resetAfterExit()
                                    navController.popBackStack()
                                } else {
                                    deviceSetupController.backToSearch()
                                }
                                Unit
                            }
                            DeviceScheduleSetupScreen(
                                state = setupState,
                                activeSchedule = availableSchedule?.toActiveScheduleSummaryUi(),
                                onQueryChanged = deviceSetupController::onQueryChanged,
                                onRetrySearch = deviceSetupController::retrySearch,
                                onCitySelected = deviceSetupController::selectCity,
                                onBack = backOrExit,
                                onRetryScheduleChoices = deviceSetupController::retryScheduleChoices,
                                onScheduleChoiceSelected = deviceSetupController::selectScheduleChoice,
                                onRetryScheduleChoiceRequest = deviceSetupController::retryScheduleChoiceRequest,
                            )
                        }
                    }
                }
                mediaPickerSession?.let { session ->
                    MediaStoreImagePickerScreen(
                        content = session.content,
                        loading = session.loading,
                        onSelect = { image: OperatorMediaImage ->
                            mediaPickerSession = null
                            importSelectedImage(session.slot, Uri.parse(image.contentUri))
                        },
                        onLoadMore = {
                            if (!session.loading && session.content.nextOffset != null) {
                                mediaPickerSession = session.copy(loading = true)
                                coroutineScope.launch {
                                    try {
                                        val content = mediaPager.loadNext(session.content)
                                        if (mediaPickerSession?.slot == session.slot) {
                                            mediaPickerSession = session.copy(
                                                content = content,
                                                loading = false,
                                            )
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: SecurityException) {
                                        mediaPickerSession = null
                                        finishImageSelection(
                                            session.slot,
                                            OperatorImageSelectionFeedback.PERMISSION_DENIED,
                                        )
                                    } catch (_: Exception) {
                                        mediaPickerSession = null
                                        finishImageSelection(
                                            session.slot,
                                            OperatorImageSelectionFeedback.INACCESSIBLE,
                                        )
                                    }
                                }
                            }
                        },
                        onCancel = {
                            mediaPickerSession = null
                            finishImageSelection(session.slot, null)
                        },
                    )
                }
                imageSelectionFeedback?.let { feedback ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 30.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        OperatorImageFeedbackBanner(feedback)
                    }
                }
            }
        }
    }
}

private fun LocalPrayerSchedule.toActiveScheduleSummaryUi(): ActiveScheduleSummaryUi {
    val identity = toMosqueDisplayIdentity()
    return ActiveScheduleSummaryUi(
        cityName = identity.locality ?: locality ?: mosqueName,
        authorityName = attribution ?: authorityName,
        sourceName = sourceId,
        timezone = timezoneId,
    )
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
    donationQrState: QrCampaignUiState?,
    donationAssetVersion: Long,
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
            donationQrState = donationQrState,
            donationAssetVersion = donationAssetVersion,
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
    donationQrState: QrCampaignUiState? = null,
    donationAssetVersion: Long = 0L,
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
    val timeInput = remember(schedule, operatorPreferences.iqamahConfiguration, projectionInstant) {
        schedule.toTimeEngineInput(operatorPreferences.iqamahConfiguration, projectionInstant)
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
    val displayState = schedule.toPrayerDisplayUiState(
        resolution = resolution as PrayerTimeResolution.Available,
        strings = strings,
        displayIdentity = schedule.toMosqueDisplayIdentity(
            operatorPreferences.mosquePresentationIdentity,
        ),
    ).copy(supportCode = recoveryCode, campaign = campaign)
    if (operatorPreferences.displayMode == OperatorDisplayMode.DONATION && donationQrState != null) {
        DonationDisplayScreen(
            configuration = operatorPreferences.donationConfiguration,
            qrState = donationQrState,
            status = DonationStatusUiState(
                dateLabel = displayState.dateLabel,
                weekdayLabel = displayState.weekdayLabel,
                mosqueLocalTime = displayState.mosqueLocalTime.take(5),
                currentPrayerLabel = displayState.currentPrayerLabel,
            ),
            customAssetVersion = donationAssetVersion,
            retentionOffset = retentionOffset,
            onOpenSettings = onOpenSettings,
        )
    } else {
        MainPrayerDisplay(
            state = displayState,
            retentionOffset = retentionOffset,
            onOpenSettings = onOpenSettings,
        )
    }
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
