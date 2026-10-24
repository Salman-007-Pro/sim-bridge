package com.simbridge.host.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.core.ui.components.InfoRow
import com.simbridge.core.ui.components.OnboardingScaffold
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone
import com.simbridge.host.feature.pairing.PairingContent
import com.simbridge.host.feature.pairing.PairingViewModel
import com.simbridge.host.feature.permissions.PermissionList
import com.simbridge.host.feature.probe.CapabilityReportContent
import com.simbridge.host.session.PairingState

private const val TOTAL_STEPS = 6

@Composable
fun HostOnboardingScreen(
    onFinished: () -> Unit,
    viewModel: HostOnboardingViewModel = hiltViewModel(),
    pairingViewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != HostOnboardingStep.WELCOME) { viewModel.back() }

    when (state.step) {
        HostOnboardingStep.WELCOME -> OnboardingScaffold(
            step = 1,
            totalSteps = TOTAL_STEPS,
            icon = Icons.Filled.Lock,
            title = "This phone holds your SIM",
            description = "SIM Bridge lets your other phone use this phone's number for calls and SMS over your local Wi-Fi.",
            primaryLabel = "Continue",
            primaryEnabled = state.consentGiven,
            onPrimary = viewModel::next,
        ) {
            SectionCard(title = "What is shared") {
                Bullet("Incoming call alerts and call controls (answer, reject, hang up, dial)")
                Bullet("SMS you send and receive on this phone's number")
                Bullet("SIM status, carrier and signal")
            }
            SectionCard(title = "What is not possible") {
                Bullet("The SIM, its IMEI or registration never move to the other phone")
                Bullet("Call audio stays on this phone until it is verified for your model")
                Bullet("Emergency calls are not routed through the bridge")
            }
            Row(
                modifier = Modifier.toggleable(
                    value = state.consentGiven,
                    role = Role.Checkbox,
                    onValueChange = viewModel::setConsent,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.consentGiven, onCheckedChange = null)
                Text("I own both phones and allow my paired phone to access calls and SMS on this SIM.")
            }
        }

        HostOnboardingStep.CAPABILITIES -> OnboardingScaffold(
            step = 2,
            totalSteps = TOTAL_STEPS,
            icon = Icons.Filled.Build,
            title = "Capability check",
            description = "Here is what this phone supports. Nothing below is guessed; unknown values are shown as unknown.",
            primaryLabel = "Continue",
            onPrimary = viewModel::next,
            secondaryLabel = "Back",
            onSecondary = viewModel::back,
        ) {
            state.report?.let { CapabilityReportContent(it) }
        }

        HostOnboardingStep.PERMISSIONS -> OnboardingScaffold(
            step = 3,
            totalSteps = TOTAL_STEPS,
            icon = Icons.Filled.Check,
            title = "Permissions",
            description = "Allow only what you need now. You can change any of these later from the dashboard.",
            primaryLabel = "Continue",
            onPrimary = viewModel::next,
            secondaryLabel = "Back",
            onSecondary = viewModel::back,
        ) {
            PermissionList()
        }

        HostOnboardingStep.NETWORK -> OnboardingScaffold(
            step = 4,
            totalSteps = TOTAL_STEPS,
            icon = Icons.Filled.Share,
            title = "Choose a local network",
            description = "Put both phones on the same Wi-Fi, or turn on this phone's hotspot and connect your other phone to it. No internet is needed.",
            primaryLabel = "Start bridge",
            primaryEnabled = state.addresses.isNotEmpty(),
            onPrimary = viewModel::next,
            secondaryLabel = "Check again",
            onSecondary = viewModel::rescanNetwork,
        ) {
            if (state.addresses.isEmpty()) {
                StatusBanner(
                    title = "No local network found",
                    message = "Connect this phone to Wi-Fi or enable its hotspot, then tap Check again.",
                    tone = StatusTone.Warning,
                    icon = Icons.Filled.Warning,
                )
            } else {
                SectionCard(title = "Ready on") {
                    state.addresses.forEach { InfoRow("Address", it) }
                }
            }
        }

        HostOnboardingStep.PAIR -> {
            val pairing by pairingViewModel.pairing.collectAsStateWithLifecycle()
            val bridge by pairingViewModel.bridgeState.collectAsStateWithLifecycle()
            val done = pairing is PairingState.Completed
            OnboardingScaffold(
                step = 5,
                totalSteps = TOTAL_STEPS,
                icon = Icons.Filled.Lock,
                title = "Pair your Client phone",
                description = "Open SIM Bridge on your other phone and scan this code. You will confirm a 6-digit code on both phones.",
                primaryLabel = if (done) "Go to dashboard" else "Pair later",
                onPrimary = {
                    viewModel.finish()
                    onFinished()
                },
            ) {
                PairingContent(
                    pairing = pairing,
                    bridge = bridge,
                    onShowCode = pairingViewModel::showCode,
                    onApprove = pairingViewModel::approve,
                    onReject = pairingViewModel::reject,
                    onDone = {
                        viewModel.finish()
                        onFinished()
                    },
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
