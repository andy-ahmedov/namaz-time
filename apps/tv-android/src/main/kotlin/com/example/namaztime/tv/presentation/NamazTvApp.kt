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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.example.namaztime.tv.repository.OperatorPreferences
import com.example.namaztime.tv.repository.OperatorPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.launch

private const val DISPLAY_ROUTE = "display"
private const val SETTINGS_ROUTE = "settings"

@Composable
fun NamazTvApp(operatorPreferencesRepository: OperatorPreferencesRepository) {
    val preferences by operatorPreferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = OperatorPreferences(),
    )
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme {
        NavHost(
            navController = navController,
            startDestination = DISPLAY_ROUTE,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101A1D)),
        ) {
            composable(DISPLAY_ROUTE) {
                DisplayPlaceholderScreen(
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
private fun DisplayPlaceholderScreen(onOpenSettings: () -> Unit) {
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
            text = "Prayer schedule unavailable",
            modifier = Modifier.semantics { heading() },
            fontSize = 44.sp,
        )
        Text(
            text = "No local snapshot has been activated.",
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            fontSize = 24.sp,
            color = Color(0xFFD7E0E2),
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.focusRequester(settingsFocusRequester),
        ) {
            Text("Open settings")
        }
    }
}
