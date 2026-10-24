package com.simbridge.client.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.simbridge.client.session.ClientState
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

enum class HomeTab(val label: String, val icon: ImageVector) {
    DIAL("Dial", Icons.Filled.Call),
    MESSAGES("Messages", Icons.Filled.Email),
    CALLS("Calls", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: ClientState,
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onRetry: () -> Unit,
    onResume: () -> Unit,
    onPairAgain: () -> Unit,
    content: @Composable (HomeTab) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected.label) },
                actions = {
                    val host = state.hostName ?: "Host"
                    if (state.setup.isConnected) StatusChip(host, StatusTone.Positive) else StatusChip("$host offline", StatusTone.Warning)
                },
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ConnectionBanner(
                state = state,
                onRetry = onRetry,
                onResume = onResume,
                onPairAgain = onPairAgain,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            content(selected)
        }
    }
}
