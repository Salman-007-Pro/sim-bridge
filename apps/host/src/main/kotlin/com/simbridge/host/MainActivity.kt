package com.simbridge.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.simbridge.core.ui.theme.SimBridgeTheme
import com.simbridge.host.data.HostPrefs
import com.simbridge.host.feature.probe.CapabilityProbe
import com.simbridge.host.navigation.HostNavHost
import com.simbridge.host.navigation.HostTool
import com.simbridge.host.session.HostBridgeService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var probe: CapabilityProbe
    @Inject lateinit var prefs: HostPrefs
    @Inject lateinit var tools: Set<@JvmSuppressWildcards HostTool>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            if (prefs.isBridgeEnabled()) HostBridgeService.start(this@MainActivity)
        }
        val onboarding = prefs.isOnboardingDone.map<Boolean, Boolean?> { it }
        setContent {
            SimBridgeTheme {
                val done by onboarding.collectAsStateWithLifecycle(initialValue = null)
                done?.let { HostNavHost(onboardingDone = it, probe = probe, tools = tools.sortedBy(HostTool::label)) }
            }
        }
    }
}
