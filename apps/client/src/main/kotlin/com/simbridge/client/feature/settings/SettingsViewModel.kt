package com.simbridge.client.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simbridge.client.BuildConfig
import com.simbridge.client.data.ClientPrefs
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.core.ui.power.isIgnoringBatteryOptimizations
import com.simbridge.core.ui.report.DebugReport
import com.simbridge.client.session.ClientBridge
import com.simbridge.client.session.ClientState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val bridge: ClientBridge,
    private val prefs: ClientPrefs,
) : ViewModel() {
    val state: StateFlow<ClientState> = bridge.state
    val manualEndpoint: StateFlow<String?> = prefs.manualEndpointText.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hideContent: StateFlow<Boolean> = prefs.hideContentOnLockScreen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _endpointError = MutableStateFlow<String?>(null)
    val endpointError: StateFlow<String?> = _endpointError.asStateFlow()

    fun retry() = bridge.reconnectNow()
    fun pause() = bridge.pause()
    fun resume() = bridge.resume()

    fun saveEndpoint(text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (bridge.setManualEndpoint(text)) {
                _endpointError.value = null
                onDone()
            } else {
                _endpointError.value = "Enter an IPv4 address like 192.168.43.1 or 192.168.43.1:47820"
            }
        }
    }

    fun setHideContent(hide: Boolean) {
        viewModelScope.launch { prefs.setHideContent(hide) }
    }

    fun wipe() {
        viewModelScope.launch { bridge.wipeLocalData() }
    }

    fun unpair() {
        viewModelScope.launch { bridge.unpair() }
    }

    fun forgetHost() {
        viewModelScope.launch { bridge.forgetHost() }
    }

    fun debugReport(context: Context): String {
        val s = state.value
        return DebugReport.build(
            appName = "SIM Bridge Client",
            version = BuildConfig.VERSION_NAME,
            sections = mapOf(
                "Connection" to listOf(
                    "setup: ${s.setup}",
                    "paused: ${s.paused}",
                    "endpoint set: ${s.endpoint != null}",
                    "manual endpoint: ${manualEndpoint.value != null}",
                    "message: ${s.message ?: "none"}",
                ),
                "Commands" to listOf(
                    "sent: ${s.stats.sent}",
                    "acked: ${s.stats.acked}",
                    "rejected: ${s.stats.rejected}",
                    "timed out: ${s.stats.timedOut}",
                ),
                "Host" to listOf(
                    "calls: ${s.capabilities?.calls}",
                    "sms: ${s.capabilities?.sms}",
                    "audio: ${s.capabilities?.audio}",
                    "sim ready: ${s.hostStatus?.simReady}",
                    "signal: ${s.hostStatus?.signalLevel}",
                ),
                "Power" to listOf("battery optimisation exempt: ${isIgnoringBatteryOptimizations(context)}"),
            ),
            log = BridgeLog.export(),
        )
    }
}
