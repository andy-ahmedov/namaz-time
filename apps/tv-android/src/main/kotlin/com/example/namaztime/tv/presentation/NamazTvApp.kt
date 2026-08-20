package com.example.namaztime.tv.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.namaztime.tv.R
import com.example.namaztime.tv.data.snapshot.SnapshotBootstrapState
import com.example.namaztime.tv.domain.PrayerTimeEngine
import com.example.namaztime.tv.domain.PrayerTimeResolution
import com.example.namaztime.tv.repository.CorruptLocalSnapshotException
import com.example.namaztime.tv.repository.EmptyPrayerScheduleRepository
import com.example.namaztime.tv.repository.LocalPrayerSchedule
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorPreferencesRepository
import com.example.namaztime.tv.repository.PrayerScheduleRepository
import com.example.namaztime.tv.repository.toTimeEngineInput
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private const val DISPLAY_ROUTE = "display"
private const val SETTINGS_ROUTE = "settings"

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
    val observedSchedule by prayerScheduleRepository.observeForDisplay()
        .collectAsStateWithLifecycle(initialValue = DisplayScheduleState.Unavailable)
    val bootstrap by bootstrapState.collectAsStateWithLifecycle(
        initialValue = SnapshotBootstrapState.Pending,
    )

    MaterialTheme {
        NavHost(
            navController = navController,
            startDestination = DISPLAY_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101A1D)),
        ) {
            composable(DISPLAY_ROUTE) {
                DisplayRoute(
                    schedule = (observedSchedule as? DisplayScheduleState.Available)?.schedule,
                    localDiagnostic =
                        (observedSchedule as? DisplayScheduleState.Diagnostic)?.supportCode,
                    bootstrapState = bootstrap,
                    clock = clock,
                    tickIntervalMillis = tickIntervalMillis,
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
                                // Focus remains usable when non-critical preferences cannot persist.
                            }
                        }
                    },
                    onExit = { navController.popBackStack() },
                )
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
    onOpenSettings: () -> Unit,
) {
    if (schedule != null) {
        val engine = remember { PrayerTimeEngine() }
        val timeInput = remember(schedule) { schedule.toTimeEngineInput() }
        val validationCode = remember(timeInput) { engine.validate(timeInput) }
        var currentInstant by remember(schedule.snapshotId, clock) {
            mutableStateOf(clock.instant())
        }
        LaunchedEffect(schedule.snapshotId, clock, tickIntervalMillis) {
            if (tickIntervalMillis != null) {
                while (true) {
                    delay(tickIntervalMillis)
                    currentInstant = clock.instant()
                }
            }
        }
        if (validationCode != null) {
            DisplayUnavailableScreen(
                localDiagnostic = validationCode,
                bootstrapState = bootstrapState,
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
                onOpenSettings = onOpenSettings,
            )
            return
        }
        val recoveryCode = (bootstrapState as? SnapshotBootstrapState.Ready)?.recoveryCode
        MainPrayerDisplay(
            state = schedule.toPrayerDisplayUiState(
                resolution = resolution as PrayerTimeResolution.Available,
            ).copy(supportCode = recoveryCode),
            onOpenSettings = onOpenSettings,
        )
        return
    }
    DisplayUnavailableScreen(
        localDiagnostic = localDiagnostic,
        bootstrapState = bootstrapState,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun DisplayUnavailableScreen(
    localDiagnostic: String?,
    bootstrapState: SnapshotBootstrapState,
    onOpenSettings: () -> Unit,
) {
    val settingsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        settingsFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when {
                localDiagnostic != null -> "Prayer schedule unavailable"
                else -> when (bootstrapState) {
                    SnapshotBootstrapState.Pending -> "Loading local schedule"
                    is SnapshotBootstrapState.Ready -> "Prayer schedule unavailable"
                    is SnapshotBootstrapState.Diagnostic -> "Prayer schedule unavailable"
                }
            },
            modifier = Modifier.semantics { heading() },
            fontSize = 44.sp,
        )
        Text(
            text = when {
                localDiagnostic != null -> "Support code: $localDiagnostic"
                else -> when (bootstrapState) {
                    SnapshotBootstrapState.Pending -> "Validating bundled snapshot…"
                    is SnapshotBootstrapState.Ready -> "No current local schedule is available."
                    is SnapshotBootstrapState.Diagnostic ->
                        "Support code: ${bootstrapState.supportCode}"
                }
            },
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            fontSize = 24.sp,
            color = Color(0xFFD7E0E2),
        )
        if (bootstrapState is SnapshotBootstrapState.Ready && bootstrapState.recoveryCode != null) {
            Text(
                text = "Support code: ${bootstrapState.recoveryCode}",
                modifier = Modifier.padding(bottom = 24.dp),
                fontSize = 20.sp,
                color = Color(0xFFFFD166),
            )
        }
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.focusRequester(settingsFocusRequester),
        ) {
            Text(stringResource(R.string.open_settings))
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
