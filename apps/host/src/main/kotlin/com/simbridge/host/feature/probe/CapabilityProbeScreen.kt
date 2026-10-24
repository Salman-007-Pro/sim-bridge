package com.simbridge.host.feature.probe

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.simbridge.core.model.CapabilityStatus
import com.simbridge.core.ui.components.InfoRow
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityProbeScreen(
    probe: CapabilityProbe,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(probe.run()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device capabilities") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { report = probe.run() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Run probe again")
                    }
                    IconButton(onClick = { shareReport(context, report) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share report")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            CapabilityReportContent(report, Modifier.padding(PaddingValues(Spacing.screen)))
        }
    }
}

@Composable
fun CapabilityReportContent(report: DeviceReport, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        SectionCard(title = "Bridge features") {
            CapabilityLine("Calls", report.callsCapability)
            CapabilityLine("SMS", report.smsCapability)
            InfoRow("Call audio", "Unsupported on this Host (not verified)")
        }
        SectionCard(title = "SIM and network") {
            InfoRow("SIM", report.telephony.simStateLabel)
            InfoRow("SIM operator", report.telephony.simOperator ?: "Unknown")
            InfoRow("Network", report.telephony.networkOperator ?: "Unknown")
            InfoRow("Signal level", report.telephony.signalLevel?.let { "$it / 4" } ?: "Not exposed by OS")
            InfoRow("Active modems", report.telephony.activeModems.toString())
        }
        SectionCard(title = "Android roles") {
            InfoRow("Phone (dialer) role", roleLabel(report.roles.dialerAvailable, report.roles.dialerHeld))
            InfoRow("SMS role", roleLabel(report.roles.smsAvailable, report.roles.smsHeld))
        }
        SectionCard(title = "Device") {
            InfoRow("Model", "${report.brand} ${report.model}")
            InfoRow("Manufacturer", report.manufacturer)
            InfoRow("SoC", report.soc)
            InfoRow("Android", "${report.androidRelease} (API ${report.apiLevel})")
            InfoRow("Security patch", report.securityPatch)
        }
        SectionCard(title = "Connectivity") {
            InfoRow("Wi-Fi", if (report.connectivity.wifi) "Available" else "Missing")
            InfoRow("Wi-Fi Direct", if (report.connectivity.wifiDirect) "Available" else "Missing")
            InfoRow("Bluetooth LE", if (report.connectivity.bluetoothLe) "Available" else "Missing")
        }
    }
}

@Composable
private fun CapabilityLine(label: String, status: CapabilityStatus) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        when (status) {
            CapabilityStatus.SUPPORTED -> StatusChip("Ready", StatusTone.Positive)
            CapabilityStatus.NEEDS_SETUP -> StatusChip("Needs setup", StatusTone.Warning)
            CapabilityStatus.UNSUPPORTED -> StatusChip("Unsupported", StatusTone.Negative)
            CapabilityStatus.UNKNOWN -> StatusChip("Unknown", StatusTone.Neutral)
        }
    }
}

private fun roleLabel(available: Boolean, held: Boolean) = when {
    !available -> "Not available on this phone"
    held -> "Granted"
    else -> "Available, not granted"
}

private fun shareReport(context: Context, report: DeviceReport) {
    val text = buildString {
        appendLine("SIM Bridge Host capability report")
        appendLine(report.toMarkdownRow())
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share report"))
}
