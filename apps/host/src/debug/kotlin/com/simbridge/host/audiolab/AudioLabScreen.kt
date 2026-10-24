package com.simbridge.host.audiolab

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.core.model.CallStatus
import com.simbridge.core.ui.components.InfoRow
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.SecondaryButton
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.report.DebugReport
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone
import com.simbridge.host.navigation.DetailScaffold
import kotlinx.serialization.Serializable

@Serializable object AudioLabRoute

/** Debug-build-only harness for the PRD 6.4 experiments. Never part of the production UI. */
@Composable
fun AudioLabScreen(onBack: () -> Unit, viewModel: AudioLabViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshShizuku()
        onPauseOrDispose { }
    }
    val busy = state.running != null
    val activeCall = calls.firstOrNull { it.status == CallStatus.ACTIVE }

    DetailScaffold("Audio lab", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            StatusBanner(
                title = "Lab only",
                message = "Results here never enable call audio. Run each probe during a real call with a consenting " +
                    "third phone and record findings in docs/audio-rnd.",
                tone = StatusTone.Warning,
                icon = Icons.Filled.Warning,
            )
            SectionCard(
                title = "Call",
                trailing = { if (activeCall != null) StatusChip("Active call", StatusTone.Positive) else StatusChip("No active call", StatusTone.Neutral) },
            ) {
                Text(
                    "Start a cellular call to the third phone and keep it active. Downlink probes need the far end speaking; " +
                        "for uplink tests keep the Host microphone covered or distant.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SecondaryButton("Grant microphone and Bluetooth", onClick = {
                    permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT))
                })
            }
            if (busy) {
                Text("Running: ${state.running}", style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            SectionCard(title = "A · This app (no privileges)") {
                Text("Expected to fail for call sources: protected audio needs CAPTURE_AUDIO_OUTPUT.", style = MaterialTheme.typography.bodySmall)
                PrimaryButton("Run capture probes", onClick = viewModel::runAppCaptures, enabled = !busy)
                SecondaryButton("Play tone to telephony TX", onClick = viewModel::runAppTone, enabled = !busy)
            }
            SectionCard(
                title = "B · Shizuku shell (ADB-level)",
                trailing = {
                    when (state.shizuku) {
                        ShizukuStatus.READY -> StatusChip("Ready · uid ${state.shizukuUid}", StatusTone.Positive)
                        ShizukuStatus.PERMISSION_REQUIRED -> StatusChip("Permission needed", StatusTone.Warning)
                        ShizukuStatus.NOT_RUNNING -> StatusChip("Not running", StatusTone.Neutral)
                    }
                },
            ) {
                Text(
                    "Install Shizuku, start it with Wireless debugging, then grant access. This setup does not survive " +
                        "reboot without re-activation — record that in the results.",
                    style = MaterialTheme.typography.bodySmall,
                )
                when (state.shizuku) {
                    ShizukuStatus.PERMISSION_REQUIRED -> PrimaryButton("Grant Shizuku access", onClick = viewModel::requestShizukuPermission)
                    ShizukuStatus.READY -> {
                        PrimaryButton("Run shell capture probes", onClick = viewModel::runShellCaptures, enabled = !busy)
                        SecondaryButton("Play tone to telephony TX (shell)", onClick = viewModel::runShellTone, enabled = !busy)
                    }
                    ShizukuStatus.NOT_RUNNING -> SecondaryButton("Check again", onClick = viewModel::refreshShizuku)
                }
            }
            SectionCard(title = "D · Bluetooth HFP client") {
                SecondaryButton("Probe HFP client role", onClick = viewModel::runHfp, enabled = !busy)
            }
            SectionCard(title = "Results") {
                if (state.entries.isEmpty()) {
                    Text("No probes run yet.", style = MaterialTheme.typography.bodyMedium)
                }
                state.entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    InfoRow("${entry.time} ${entry.probe}", entry.result)
                }
                if (state.entries.isNotEmpty()) {
                    PrimaryButton("Share results", onClick = { DebugReport.share(context, viewModel.report()) })
                    SecondaryButton("Clear", onClick = viewModel::clear)
                }
            }
        }
    }
}
