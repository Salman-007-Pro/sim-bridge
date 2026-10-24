package com.simbridge.host.feature.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.simbridge.core.model.Redaction
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.power.BatteryGuideCard
import com.simbridge.core.ui.power.isIgnoringBatteryOptimizations
import com.simbridge.core.ui.report.DebugReport
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.host.BuildConfig
import com.simbridge.host.navigation.DetailScaffold
import com.simbridge.host.session.HostBridge
import com.simbridge.host.session.HostStatusSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable object BatteryRoute
@Serializable object DebugReportRoute

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    DetailScaffold("Battery and background", onBack = onBack) {
        BatteryGuideCard(appRole = "Host app")
    }
}

@HiltViewModel
class DebugReportViewModel @Inject constructor(
    private val bridge: HostBridge,
    private val status: HostStatusSource,
) : ViewModel() {
    fun report(exemptFromBattery: Boolean): String {
        val state = bridge.state.value
        val caps = bridge.capabilities()
        val host = status.read()
        return DebugReport.build(
            appName = "SIM Bridge Host",
            version = BuildConfig.VERSION_NAME,
            sections = mapOf(
                "Bridge" to listOf(
                    "running: ${state.running}",
                    "port: ${state.port}",
                    "addresses: ${state.addresses.size}",
                    "client: ${state.activeClient?.let { Redaction.fingerprint(it.fingerprint) } ?: "none"}",
                    "error: ${state.error ?: "none"}",
                ),
                "Capabilities" to listOf("calls: ${caps.calls}", "sms: ${caps.sms}", "audio: ${caps.audio}"),
                "SIM" to listOf(
                    "ready: ${host.simReady}",
                    "carrier present: ${host.carrierName != null}",
                    "signal: ${host.signalLevel}",
                    "battery: ${host.batteryPercent} charging=${host.charging}",
                ),
                "Power" to listOf("battery optimisation exempt: $exemptFromBattery"),
            ),
            log = BridgeLog.export(),
        )
    }
}

@Composable
fun DebugReportScreen(onBack: () -> Unit, viewModel: DebugReportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    DetailScaffold("Debug report", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SectionCard(title = "What is included") {
                Text(
                    "Bridge state, capability flags and the recent technical log. Phone numbers, message text, " +
                        "pairing codes and keys are removed before anything is shown or shared.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PrimaryButton("Share report", onClick = {
                    DebugReport.share(context, viewModel.report(isIgnoringBatteryOptimizations(context)))
                })
            }
        }
    }
}
