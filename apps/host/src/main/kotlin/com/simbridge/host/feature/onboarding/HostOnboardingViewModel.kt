package com.simbridge.host.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simbridge.host.data.HostPrefs
import com.simbridge.host.feature.probe.CapabilityProbe
import com.simbridge.host.feature.probe.DeviceReport
import com.simbridge.host.session.HostBridge
import com.simbridge.host.session.HostBridgeService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HostOnboardingStep { WELCOME, CAPABILITIES, PERMISSIONS, NETWORK, PAIR }

data class HostOnboardingUiState(
    val step: HostOnboardingStep = HostOnboardingStep.WELCOME,
    val consentGiven: Boolean = false,
    val report: DeviceReport? = null,
    val addresses: List<String> = emptyList(),
)

@HiltViewModel
class HostOnboardingViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val probe: CapabilityProbe,
    private val bridge: HostBridge,
    private val prefs: HostPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(HostOnboardingUiState())
    val state: StateFlow<HostOnboardingUiState> = _state.asStateFlow()

    fun setConsent(given: Boolean) = _state.update { it.copy(consentGiven = given) }

    fun next() = _state.update { current ->
        val next = HostOnboardingStep.entries.getOrElse(current.step.ordinal + 1) { current.step }
        when (next) {
            HostOnboardingStep.CAPABILITIES -> current.copy(step = next, report = probe.run())
            HostOnboardingStep.NETWORK -> current.copy(step = next, addresses = bridge.state.value.addresses.ifEmpty { refreshAddresses() })
            HostOnboardingStep.PAIR -> {
                startBridge()
                current.copy(step = next)
            }
            else -> current.copy(step = next)
        }
    }

    fun back() = _state.update { current ->
        current.copy(step = HostOnboardingStep.entries.getOrElse(current.step.ordinal - 1) { current.step })
    }

    fun rescanNetwork() = _state.update { it.copy(addresses = refreshAddresses()) }

    fun finish() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }

    private fun startBridge() {
        viewModelScope.launch { prefs.setBridgeEnabled(true) }
        HostBridgeService.start(context)
    }

    private fun refreshAddresses(): List<String> {
        bridge.refreshAddresses()
        return com.simbridge.host.session.NetworkAddresses.localIpv4()
    }
}
