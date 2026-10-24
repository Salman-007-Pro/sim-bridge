package com.simbridge.host.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simbridge.core.protocol.Capabilities
import com.simbridge.core.protocol.HostStatus
import com.simbridge.core.storage.pairing.PairedDevice
import com.simbridge.host.data.HostPrefs
import com.simbridge.host.session.HostBridge
import com.simbridge.host.session.HostBridgeService
import com.simbridge.host.session.HostBridgeState
import com.simbridge.host.session.HostStatusSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val bridge: HostBridgeState = HostBridgeState(),
    val status: HostStatus? = null,
    val capabilities: Capabilities? = null,
    val pairedClients: List<PairedDevice> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bridge: HostBridge,
    statusSource: HostStatusSource,
    private val prefs: HostPrefs,
) : ViewModel() {
    private val capabilities = MutableStateFlow(bridge.capabilities())

    val state: StateFlow<DashboardUiState> = combine(
        bridge.state,
        statusSource.status,
        capabilities,
        bridge.pairedClients,
    ) { bridgeState, status, caps, clients ->
        DashboardUiState(bridgeState, status, caps, clients.sortedByDescending { it.lastSeenAtMs ?: it.pairedAtMs })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun refresh() {
        capabilities.value = bridge.capabilities()
        bridge.refreshAddresses()
        bridge.refreshCapabilities()
    }

    fun startBridge() {
        viewModelScope.launch { prefs.setBridgeEnabled(true) }
        HostBridgeService.start(context)
    }

    fun stopBridge() {
        viewModelScope.launch { prefs.setBridgeEnabled(false) }
        HostBridgeService.stop(context)
    }

    fun unpair(fingerprint: String) {
        viewModelScope.launch { bridge.unpair(fingerprint) }
    }
}
