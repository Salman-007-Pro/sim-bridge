package com.simbridge.client.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.client.BuildConfig
import com.simbridge.client.session.ClientState
import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.CapabilityStatus
import com.simbridge.core.model.SetupState
import com.simbridge.core.ui.components.InfoRow
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.SecondaryButton
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

private enum class Confirm { WIPE, UNPAIR }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manual by viewModel.manualEndpoint.collectAsStateWithLifecycle()
    val hideContent by viewModel.hideContent.collectAsStateWithLifecycle()
    val endpointError by viewModel.endpointError.collectAsStateWithLifecycle()
    var editingEndpoint by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ConnectionCard(state, onRetry = viewModel::retry, onPause = viewModel::pause, onResume = viewModel::resume)
        HostCard(state)
        SectionCard(title = "Network") {
            InfoRow("Manual Host address", manual ?: "Automatic")
            Text(
                "Use only for troubleshooting if automatic discovery fails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton("Set Host address", onClick = { editingEndpoint = true })
        }
        SectionCard(title = "Privacy") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hide message content on lock screen", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Notifications show only \"New message\" while the phone is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = hideContent, onCheckedChange = viewModel::setHideContent)
            }
        }
        SectionCard(title = "Data and pairing") {
            SecondaryButton("Wipe messages and call history on this phone", onClick = { confirm = Confirm.WIPE })
            if (state.setup == SetupState.REVOKED) {
                PrimaryButton("Forget Host and pair again", onClick = viewModel::forgetHost)
            } else {
                SecondaryButton("Unpair from Host", onClick = { confirm = Confirm.UNPAIR }, destructive = true)
            }
        }
        Text(
            "SIM Bridge ${BuildConfig.VERSION_NAME} · local network only, no cloud account",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (editingEndpoint) {
        var text by remember { mutableStateOf(manual.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingEndpoint = false },
            title = { Text("Host address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Shown on the Host dashboard under Local address. Leave empty for automatic.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        isError = endpointError != null,
                        supportingText = endpointError?.let { { Text(it) } },
                        placeholder = { Text("192.168.43.1:47820") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveEndpoint(text) { editingEndpoint = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingEndpoint = false }) { Text("Cancel") } },
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == Confirm.WIPE) "Wipe local data?" else "Unpair from ${state.hostName ?: "Host"}?") },
            text = {
                Text(
                    if (action == Confirm.WIPE) {
                        "Messages and call history stored on this phone are deleted. They stay on the Host and sync back next time."
                    } else {
                        "The Host removes this phone, this phone's key is replaced and local data is deleted. You will need to pair again."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == Confirm.WIPE) viewModel.wipe() else viewModel.unpair()
                    confirm = null
                }) { Text(if (action == Confirm.WIPE) "Wipe" else "Unpair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConnectionCard(state: ClientState, onRetry: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    SectionCard(
        title = "Connection",
        trailing = {
            when {
                state.paused -> StatusChip("Paused", StatusTone.Neutral)
                state.setup == SetupState.CONNECTED_CONTROL_ONLY || state.setup == SetupState.CONNECTED_AUDIO_READY ->
                    StatusChip("Connected", StatusTone.Positive)
                state.setup == SetupState.DEGRADED -> StatusChip("Limited", StatusTone.Warning)
                state.setup == SetupState.REVOKED -> StatusChip("Revoked", StatusTone.Negative)
                else -> StatusChip("Offline", StatusTone.Warning)
            }
        },
    ) {
        InfoRow("Host", state.hostName ?: "—")
        InfoRow("Address", state.endpoint?.let { "${it.address}:${it.port}" } ?: "—")
        val stats = state.stats
        if (stats.sent > 0) InfoRow("Commands acknowledged", "${stats.acked + stats.rejected} / ${stats.sent}")
        state.message?.takeIf { !state.setup.isConnected }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            state.paused -> PrimaryButton("Resume", onClick = onResume)
            state.setup.isConnected -> SecondaryButton("Pause connection", onClick = onPause)
            state.setup != SetupState.REVOKED -> PrimaryButton("Retry now", onClick = onRetry)
        }
    }
}

@Composable
private fun HostCard(state: ClientState) {
    val status = state.hostStatus
    val caps = state.capabilities
    val live = state.setup.isConnected
    SectionCard(title = if (live) "Host phone" else "Host phone (last known)") {
        InfoRow("Host signal", status?.signalLevel?.let { "$it / 4" } ?: "Unknown")
        InfoRow("Carrier", status?.carrierName ?: "Unknown")
        InfoRow("SIM", if (status?.simReady == true) "Ready" else if (status == null) "Unknown" else "Not ready")
        InfoRow("Host battery", status?.batteryPercent?.let { "$it%" } ?: "Unknown")
        CapabilityRow("Calls", caps?.calls)
        CapabilityRow("SMS", caps?.sms)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Call audio", modifier = Modifier.weight(1f))
            if (caps?.audio == AudioStatus.VERIFIED) {
                StatusChip("Verified", StatusTone.Positive)
            } else {
                StatusChip("Unsupported on this Host", StatusTone.Neutral)
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, status: CapabilityStatus?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        when (status) {
            CapabilityStatus.SUPPORTED -> StatusChip("Ready", StatusTone.Positive)
            CapabilityStatus.NEEDS_SETUP -> StatusChip("Needs setup on Host", StatusTone.Warning)
            CapabilityStatus.UNSUPPORTED -> StatusChip("Unsupported", StatusTone.Negative)
            else -> StatusChip("Unknown", StatusTone.Neutral)
        }
    }
}
