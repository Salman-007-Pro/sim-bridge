package com.simbridge.host.feature.pairing

import androidx.lifecycle.ViewModel
import com.simbridge.host.session.HostBridge
import com.simbridge.host.session.HostBridgeState
import com.simbridge.host.session.PairingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PairingViewModel @Inject constructor(private val bridge: HostBridge) : ViewModel() {
    val pairing: StateFlow<PairingState> = bridge.pairing
    val bridgeState: StateFlow<HostBridgeState> = bridge.state

    fun showCode() {
        bridge.startPairing()
    }

    fun approve() = bridge.respondToPairing(true)

    fun reject() = bridge.respondToPairing(false)

    fun reset() = bridge.cancelPairing()

    override fun onCleared() {
        if (pairing.value !is PairingState.AwaitingApproval) bridge.cancelPairing()
    }
}
