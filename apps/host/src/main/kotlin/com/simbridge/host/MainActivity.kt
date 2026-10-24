package com.simbridge.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.simbridge.core.ui.theme.SimBridgeTheme
import com.simbridge.host.feature.probe.CapabilityProbe
import com.simbridge.host.feature.probe.CapabilityProbeScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var probe: CapabilityProbe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimBridgeTheme {
                CapabilityProbeScreen(probe = probe, onBack = null)
            }
        }
    }
}
