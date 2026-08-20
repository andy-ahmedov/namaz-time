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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

const val SETTINGS_PAGE_ACTION_TEST_TAG = "settings-page-primary-action"

@Composable
fun SettingsShell(
    initialDestination: SettingsDestination,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    campaignPreview: QrCampaignUiState? = null,
) {
    val navigationRequesters = remember {
        SettingsDestination.entries.associateWith { FocusRequester() }
    }
    val pageActionRequester = remember { FocusRequester() }
    var selectedRoute by rememberSaveable { mutableStateOf(initialDestination.route) }
    val selectedDestination = SettingsDestination.fromRoute(selectedRoute)

    LaunchedEffect(initialDestination) {
        selectedRoute = initialDestination.route
        navigationRequesters.getValue(initialDestination).requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
    ) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Settings",
                modifier = Modifier
                    .semantics { heading() }
                    .padding(bottom = 12.dp),
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            SettingsDestination.entries.forEach { destination ->
                var focused by remember(destination) { mutableStateOf(false) }
                val isSelected = selectedRoute == destination.route
                Button(
                    onClick = {
                        selectedRoute = destination.route
                        onDestinationChanged(destination)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(destination.navigationTestTag)
                        .semantics { selected = isSelected }
                        .focusRequester(navigationRequesters.getValue(destination))
                        .focusProperties {
                            destination.previous?.let { previous ->
                                up = navigationRequesters.getValue(previous)
                            }
                            destination.next?.let { next ->
                                down = navigationRequesters.getValue(next)
                            }
                            right = pageActionRequester
                        }
                        .onFocusChanged { focusState ->
                            focused = focusState.isFocused
                            if (focusState.isFocused && selectedRoute != destination.route) {
                                selectedRoute = destination.route
                                onDestinationChanged(destination)
                            }
                        }
                        .border(
                            width = when {
                                focused -> 4.dp
                                isSelected -> 2.dp
                                else -> 1.dp
                            },
                            color = when {
                                focused -> Color.White
                                isSelected -> Color(0xFFFFD180)
                                else -> Color.Transparent
                            },
                            shape = MaterialTheme.shapes.medium,
                        ),
                ) {
                    Text(destination.title)
                }
            }
        }

        Spacer(Modifier.width(56.dp))

        SettingsPage(
            destination = selectedDestination,
            navigationRequester = navigationRequesters.getValue(selectedDestination),
            pageActionRequester = pageActionRequester,
            onExit = onExit,
            campaignPreview = campaignPreview,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsPage(
    destination: SettingsDestination,
    navigationRequester: FocusRequester,
    pageActionRequester: FocusRequester,
    onExit: () -> Unit,
    campaignPreview: QrCampaignUiState?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxHeight(),
    ) {
        val compactPreview = maxHeight < 600.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = destination.title,
                modifier = Modifier.semantics { heading() },
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = destination.description,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            )
            if (destination == SettingsDestination.CAMPAIGNS && campaignPreview != null) {
                QrCampaignPanel(
                    state = campaignPreview,
                    qrSize = if (compactPreview) 96.dp else 160.dp,
                    compact = compactPreview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                Text(
                    text = "Technical shell — no production schedule loaded",
                    color = Color(0xFFFFD180),
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = onExit,
                modifier = Modifier
                    .testTag(SETTINGS_PAGE_ACTION_TEST_TAG)
                    .focusRequester(pageActionRequester)
                    .focusProperties { left = navigationRequester },
            ) {
                Text("Return to display")
            }
        }
    }
}
