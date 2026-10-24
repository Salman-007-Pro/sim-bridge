package com.simbridge.client.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.core.model.SetupState
import com.simbridge.core.ui.components.InfoRow
import com.simbridge.core.ui.components.OnboardingScaffold
import com.simbridge.core.ui.components.SasCodeDisplay
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

private const val TOTAL_STEPS = 4

@Composable
fun ClientOnboardingScreen(
    incomingLink: String?,
    viewModel: ClientOnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bridge by viewModel.bridgeState.collectAsStateWithLifecycle()
    LaunchedEffect(incomingLink) {
        if (incomingLink != null) {
            viewModel.start()
            viewModel.onCode(incomingLink)
        }
    }

    when {
        bridge.setup == SetupState.PAIRING || bridge.setup == SetupState.PAIRING_CONFIRMATION -> {
            BackHandler { viewModel.cancelPairing() }
            OnboardingScaffold(
                step = 3,
                totalSteps = TOTAL_STEPS,
                icon = Icons.Filled.Lock,
                title = "Confirm on both phones",
                description = "Check that this code matches the one on ${bridge.hostName ?: "your Host phone"}, then approve it there.",
                primaryLabel = "Waiting for approval…",
                primaryEnabled = false,
                primaryLoading = bridge.pairingCode == null,
                onPrimary = {},
                secondaryLabel = "Cancel",
                onSecondary = viewModel::cancelPairing,
            ) {
                val code = bridge.pairingCode
                if (code == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Connecting securely to ${bridge.hostName ?: "Host"}…")
                    }
                } else {
                    SasCodeDisplay(code)
                    Text(
                        "If the codes differ, cancel. Someone else may be trying to connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.step == ClientOnboardingStep.WELCOME -> OnboardingScaffold(
            step = 1,
            totalSteps = TOTAL_STEPS,
            icon = Icons.Filled.Phone,
            title = "Use your other phone's number",
            description = "Call and text from this phone using the SIM in your Host phone. This phone does not need a SIM.",
            primaryLabel = "Connect to your Host phone",
            onPrimary = viewModel::start,
        ) {
            SectionCard(title = "Before you start") {
                InfoRow("1", "Install SIM Bridge Host on the phone with the SIM")
                InfoRow("2", "Put both phones on the same Wi-Fi or the Host's hotspot")
                InfoRow("3", "Open pairing on the Host to show a QR code")
            }
            StatusBanner(
                title = "Call audio is not available yet",
                message = "You can see, answer and place calls, but the conversation itself stays on the Host phone.",
                tone = StatusTone.Info,
            )
        }

        else -> ScanStep(
            scannedHost = state.scanned?.hostName,
            scannedAddress = state.scanned?.addresses?.firstOrNull(),
            error = state.inputError ?: bridge.message,
            onCode = viewModel::onCode,
            onConnect = viewModel::connect,
            onRescan = viewModel::clearScan,
            onBack = viewModel::back,
        )
    }
}

@Composable
private fun ScanStep(
    scannedHost: String?,
    scannedAddress: String?,
    error: String?,
    onCode: (String) -> Unit,
    onConnect: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    var pasteMode by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)

    OnboardingScaffold(
        step = 2,
        totalSteps = TOTAL_STEPS,
        icon = Icons.Filled.Search,
        title = if (scannedHost == null) "Scan the Host's code" else "Host found",
        description = if (scannedHost == null) {
            "On the Host phone, open pairing and point this camera at the QR code."
        } else {
            "Connect to $scannedHost and confirm the code on both phones."
        },
        primaryLabel = if (scannedHost == null) (if (cameraGranted || pasteMode) "Waiting for a code…" else "Allow camera") else "Connect",
        primaryEnabled = scannedHost != null || (!cameraGranted && !pasteMode),
        onPrimary = {
            when {
                scannedHost != null -> onConnect()
                else -> cameraLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        secondaryLabel = when {
            scannedHost != null -> "Scan again"
            pasteMode -> "Use camera instead"
            else -> "Paste a pairing link instead"
        },
        onSecondary = {
            when {
                scannedHost != null -> onRescan()
                else -> pasteMode = !pasteMode
            }
        },
    ) {
        if (error != null) {
            StatusBanner(title = "Couldn't pair", message = error, tone = StatusTone.Negative, icon = Icons.Filled.Warning)
        }
        when {
            scannedHost != null -> SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    androidx.compose.material3.Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(scannedHost, style = MaterialTheme.typography.titleMedium)
                }
                scannedAddress?.let { InfoRow("Address", it) }
            }
            pasteMode -> SectionCard(title = "Pairing link") {
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("simbridge://pair?…") },
                    minLines = 3,
                )
                TextButton(onClick = { onCode(pasted) }, enabled = pasted.isNotBlank()) { Text("Check link") }
            }
            cameraGranted -> Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large),
            ) {
                Box(Modifier.fillMaxSize()) { QrScanner(onCode = onCode, modifier = Modifier.fillMaxSize()) }
            }
            else -> Text(
                "SIM Bridge uses the camera only to read the pairing code. Nothing is recorded or uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
