package com.simbridge.client.feature.onboarding

import androidx.lifecycle.ViewModel
import com.simbridge.client.session.ClientBridge
import com.simbridge.client.session.ClientState
import com.simbridge.core.crypto.PairingPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ClientOnboardingStep { WELCOME, SCAN }

data class ClientOnboardingUiState(
    val step: ClientOnboardingStep = ClientOnboardingStep.WELCOME,
    val scanned: PairingPayload? = null,
    val inputError: String? = null,
)

@HiltViewModel
class ClientOnboardingViewModel @Inject constructor(private val bridge: ClientBridge) : ViewModel() {
    private val _state = MutableStateFlow(ClientOnboardingUiState())
    val state: StateFlow<ClientOnboardingUiState> = _state.asStateFlow()
    val bridgeState: StateFlow<ClientState> = bridge.state

    fun start() = _state.update { it.copy(step = ClientOnboardingStep.SCAN) }

    fun back() = _state.update { ClientOnboardingUiState() }

    /** A scanned or pasted code is only parsed here; the user still taps Connect. */
    fun onCode(text: String) {
        PairingPayload.parse(text.trim())
            .onSuccess { payload -> _state.update { it.copy(scanned = payload, inputError = null) } }
            .onFailure { error -> _state.update { it.copy(scanned = null, inputError = error.message ?: "Invalid pairing code") } }
    }

    fun connect() {
        val payload = _state.value.scanned ?: return
        bridge.pair(payload)
    }

    fun clearScan() = _state.update { it.copy(scanned = null, inputError = null) }

    fun cancelPairing() {
        bridge.cancelPairing()
        _state.update { it.copy(scanned = null) }
    }
}
