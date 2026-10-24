package com.simbridge.host.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.host.feature.dashboard.DashboardLink
import com.simbridge.host.feature.dashboard.DashboardScreen
import com.simbridge.host.feature.onboarding.HostOnboardingScreen
import com.simbridge.host.feature.pairing.PairingContent
import com.simbridge.host.feature.pairing.PairingViewModel
import com.simbridge.host.feature.permissions.PermissionList
import com.simbridge.host.feature.probe.CapabilityProbe
import com.simbridge.host.feature.probe.CapabilityProbeScreen
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object DashboardRoute
@Serializable object PairingRoute
@Serializable object PermissionsRoute
@Serializable object ProbeRoute

/** Extra dashboard tools contributed by later features (battery help, debug export, audio lab). */
data class HostTool(val label: String, val route: Any, val content: @Composable (onBack: () -> Unit) -> Unit)

@Composable
fun HostNavHost(
    onboardingDone: Boolean,
    probe: CapabilityProbe,
    tools: List<HostTool>,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = if (onboardingDone) DashboardRoute else OnboardingRoute) {
        composable<OnboardingRoute> {
            HostOnboardingScreen(onFinished = {
                nav.navigate(DashboardRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            })
        }
        composable<DashboardRoute> {
            DashboardScreen(
                onPairNew = { nav.navigate(PairingRoute) },
                onOpenPermissions = { nav.navigate(PermissionsRoute) },
                links = listOf(DashboardLink("Device capabilities") { nav.navigate(ProbeRoute) }) +
                    tools.map { tool -> DashboardLink(tool.label) { nav.navigate(tool.route) } },
            )
        }
        composable<PairingRoute> {
            val vm: PairingViewModel = hiltViewModel()
            val pairing by vm.pairing.collectAsStateWithLifecycle()
            val bridge by vm.bridgeState.collectAsStateWithLifecycle()
            DetailScaffold("Pair a new phone", onBack = { nav.popBackStack() }) {
                PairingContent(
                    pairing = pairing,
                    bridge = bridge,
                    onShowCode = vm::showCode,
                    onApprove = vm::approve,
                    onReject = vm::reject,
                    onDone = {
                        vm.reset()
                        nav.popBackStack()
                    },
                )
            }
        }
        composable<PermissionsRoute> {
            DetailScaffold("Permissions and roles", onBack = { nav.popBackStack() }) { PermissionList() }
        }
        composable<ProbeRoute> {
            CapabilityProbeScreen(probe = probe, onBack = { nav.popBackStack() })
        }
        tools.forEach { tool ->
            composable(route = tool.route::class) { tool.content { nav.popBackStack() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
        ) { content() }
    }
}
