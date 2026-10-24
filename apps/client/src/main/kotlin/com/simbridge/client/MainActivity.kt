package com.simbridge.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.client.feature.onboarding.ClientOnboardingScreen
import com.simbridge.client.navigation.ClientNavHost
import com.simbridge.client.session.ClientBridge
import com.simbridge.client.session.ClientBridgeService
import com.simbridge.core.crypto.PairingPayload
import com.simbridge.core.model.SetupState
import com.simbridge.core.ui.theme.SimBridgeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var bridge: ClientBridge

    private val pairingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pairingLink.value = linkFrom(intent)
        setContent {
            SimBridgeTheme {
                val state by bridge.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                LaunchedEffect(state.setup) {
                    if (state.setup != SetupState.NOT_PAIRED) ClientBridgeService.start(this@MainActivity)
                }
                val onboarding = state.setup == SetupState.NOT_PAIRED ||
                    state.setup == SetupState.PAIRING ||
                    state.setup == SetupState.PAIRING_CONFIRMATION
                if (onboarding) {
                    ClientOnboardingScreen(incomingLink = pairingLink.value)
                } else {
                    ClientNavHost(state = state, bridge = bridge, scope = scope)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { pairingLink.value = it }
    }

    private fun linkFrom(intent: Intent?): String? =
        intent?.data?.toString()?.takeIf { it.startsWith("${PairingPayload.SCHEME}://pair") }
}
