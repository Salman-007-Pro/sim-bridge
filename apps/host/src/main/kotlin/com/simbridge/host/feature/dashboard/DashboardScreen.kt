package com.simbridge.host.feature.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.CapabilityStatus
import com.simbridge.core.storage.pairing.PairedDevice
import com.simbridge.core.ui.components.InfoRow
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.SecondaryButton
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

data class DashboardLink(val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onPairNew: () -> Unit,
    onOpenPermissions: () -> Unit,
    links: List<DashboardLink>,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmUnpair by remember { mutableStateOf<PairedDevice?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("SIM Bridge Host") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            BridgeCard(state, viewModel::startBridge, viewModel::stopBridge)
            SimCard(state)
            FeaturesCard(state, onOpenPermissions)
            PairedCard(
                clients = state.pairedClients,
                activeFingerprint = state.bridge.activeClient?.fingerprint,
                onPairNew = onPairNew,
                onUnpair = { confirmUnpair = it },
                onResetKeys = { confirmReset = true },
            )
            SectionCard(title = "Tools") {
                links.forEachIndexed { index, link ->
                    if (index > 0) HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(link.label) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = { TextButton(onClick = link.onClick) { Text("Open") } },
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Remove all phones and reset keys?") },
            text = {
                Text(
                    "Every paired phone loses access immediately and this Host gets a new security key. " +
                        "Use this if a paired phone was lost or stolen. You will need to pair again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetIdentity()
                    confirmReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    confirmUnpair?.let { device ->
        AlertDialog(
            onDismissRequest = { confirmUnpair = null },
            title = { Text("Unpair ${device.name}?") },
            text = { Text("It will immediately lose access to calls and SMS on this SIM. You can pair it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unpair(device.fingerprint)
                    confirmUnpair = null
                }) { Text("Unpair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BridgeCard(state: DashboardUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val bridge = state.bridge
    SectionCard(
        title = "Bridge",
        trailing = {
            when {
                !bridge.running -> StatusChip("Stopped", StatusTone.Neutral)
                bridge.activeClient != null -> StatusChip("Connected", StatusTone.Positive)
                else -> StatusChip("Waiting", StatusTone.Info)
            }
        },
    ) {
        bridge.error?.let { StatusBanner(title = "Bridge problem", message = it, tone = StatusTone.Negative, icon = Icons.Filled.Warning) }
        val client = bridge.activeClient
        if (client != null) {
            InfoRow("Client", client.name)
            InfoRow("Connected", DateUtils.getRelativeTimeSpanString(client.connectedAtMs).toString())
        } else if (bridge.running) {
            Text(
                "Waiting for your Client phone on the same Wi-Fi or hotspot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bridge.running) {
            InfoRow("Local address", bridge.addresses.joinToString().ifEmpty { "No Wi-Fi / hotspot" } + (bridge.port?.let { " : $it" } ?: ""))
            SecondaryButton("Stop bridge", onClick = onStop, destructive = true)
        } else {
            PrimaryButton("Start bridge", onClick = onStart)
        }
    }
}

@Composable
private fun SimCard(state: DashboardUiState) {
    val status = state.status
    SectionCard(title = "SIM") {
        InfoRow("SIM", if (status?.simReady == true) "Ready" else "Not ready")
        InfoRow("Carrier", status?.carrierName ?: "Unknown")
        InfoRow("Host signal", status?.signalLevel?.let { "$it / 4" } ?: "Not exposed by OS")
        InfoRow(
            "Battery",
            status?.batteryPercent?.let { "$it%" + if (status.charging == true) " · charging" else "" } ?: "Unknown",
        )
    }
}

@Composable
private fun FeaturesCard(state: DashboardUiState, onOpenPermissions: () -> Unit) {
    val caps = state.capabilities
    SectionCard(title = "Features") {
        FeatureRow("Calls", caps?.calls)
        FeatureRow("SMS", caps?.sms)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Call audio", modifier = Modifier.weight(1f))
            when (caps?.audio) {
                AudioStatus.VERIFIED -> StatusChip("Verified", StatusTone.Positive)
                AudioStatus.LAB_ONLY -> StatusChip("Lab only", StatusTone.Warning)
                else -> StatusChip("Unsupported on this Host", StatusTone.Neutral)
            }
        }
        if (caps?.calls != CapabilityStatus.SUPPORTED || caps.sms != CapabilityStatus.SUPPORTED) {
            SecondaryButton("Finish setup", onClick = onOpenPermissions)
        }
    }
}

@Composable
private fun FeatureRow(label: String, status: CapabilityStatus?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        when (status) {
            CapabilityStatus.SUPPORTED -> StatusChip("Ready", StatusTone.Positive)
            CapabilityStatus.NEEDS_SETUP -> StatusChip("Needs setup", StatusTone.Warning)
            CapabilityStatus.UNSUPPORTED -> StatusChip("Unsupported", StatusTone.Negative)
            else -> StatusChip("Unknown", StatusTone.Neutral)
        }
    }
}

@Composable
private fun PairedCard(
    clients: List<PairedDevice>,
    activeFingerprint: String?,
    onPairNew: () -> Unit,
    onUnpair: (PairedDevice) -> Unit,
    onResetKeys: () -> Unit,
) {
    SectionCard(title = "Paired phones") {
        if (clients.isEmpty()) {
            Text(
                "No phone is paired yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        clients.forEach { device ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleSmall)
                    val seen = device.lastSeenAtMs?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "never"
                    Text(
                        if (device.fingerprint == activeFingerprint) "Connected now" else "Last connected $seen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onUnpair(device) }) { Text("Unpair", color = MaterialTheme.colorScheme.error) }
            }
        }
        PrimaryButton("Pair a new phone", onClick = onPairNew)
        if (clients.isNotEmpty()) {
            SecondaryButton("Remove all phones and reset keys", onClick = onResetKeys, destructive = true)
        }
    }
}
