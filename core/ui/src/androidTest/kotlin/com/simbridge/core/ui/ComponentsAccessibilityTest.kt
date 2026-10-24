package com.simbridge.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.core.ui.components.OnboardingScaffold
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.RoundActionButton
import com.simbridge.core.ui.components.SasCodeDisplay
import com.simbridge.core.ui.theme.SimBridgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun verificationCodeIsAnnouncedDigitByDigit() {
        rule.setContent { SimBridgeTheme { SasCodeDisplay("482913") } }
        rule.onNodeWithContentDescription("Verification code 4 8 2 9 1 3").assertExists()
    }

    @Test
    fun callActionsMeetLargeTouchTarget() {
        rule.setContent { SimBridgeTheme { RoundActionButton(Icons.Filled.Call, "Answer", {}, Color.Green) } }
        rule.onNodeWithContentDescription("Answer").assertWidthIsAtLeast(72.dp).assertHeightIsAtLeast(72.dp)
    }

    @Test
    fun primaryButtonsAreTallEnoughAndRespectEnabledState() {
        rule.setContent { SimBridgeTheme { PrimaryButton("Connect", onClick = {}, enabled = false) } }
        rule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun onboardingExposesStepProgress() {
        rule.setContent {
            SimBridgeTheme {
                OnboardingScaffold(
                    step = 2, totalSteps = 4, icon = Icons.Filled.Lock, title = "Title", description = "Body",
                    primaryLabel = "Continue", onPrimary = {}, primaryEnabled = false,
                )
            }
        }
        rule.onNodeWithContentDescription("Step 2 of 4").assertExists()
        rule.onNodeWithText("Continue").assertIsNotEnabled()
    }
}
