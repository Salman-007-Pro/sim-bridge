package com.simbridge.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import com.simbridge.core.ui.components.OnboardingScaffold
import com.simbridge.core.ui.theme.SimBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimBridgeTheme {
                OnboardingScaffold(
                    step = 1,
                    totalSteps = 4,
                    icon = Icons.Filled.Phone,
                    title = "Use your other phone's number",
                    description = "Connect to your Host phone to call and text with its SIM.",
                    primaryLabel = "Connect to your Host phone",
                    onPrimary = {},
                )
            }
        }
    }
}
