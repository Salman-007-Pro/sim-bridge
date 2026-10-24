package com.simbridge.client.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simbridge.client.session.ClientState
import com.simbridge.core.model.SetupState
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.theme.StatusTone

/** Shown on every tab whenever the link is not fully healthy. Never hides an offline Host. */
@Composable
fun ConnectionBanner(
    state: ClientState,
    onRetry: () -> Unit,
    onResume: () -> Unit,
    onPairAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = state.hostName ?: "Host phone"
    when {
        state.paused -> StatusBanner(
            title = "Bridge paused",
            message = "Calls and SMS from $host will not arrive.",
            tone = StatusTone.Neutral,
            icon = Icons.Filled.Info,
            actionLabel = "Resume",
            onAction = onResume,
            modifier = modifier,
        )
        state.setup == SetupState.REVOKED -> StatusBanner(
            title = "Access removed",
            message = state.message ?: "This phone was unpaired on $host.",
            tone = StatusTone.Negative,
            icon = Icons.Filled.Warning,
            actionLabel = "Pair again",
            onAction = onPairAgain,
            modifier = modifier,
        )
        state.setup == SetupState.HOST_OFFLINE -> StatusBanner(
            title = "$host is offline",
            message = state.message ?: "Retrying automatically. Keep both phones on the same network.",
            tone = StatusTone.Warning,
            icon = Icons.Filled.Warning,
            actionLabel = "Retry now",
            onAction = onRetry,
            modifier = modifier,
        )
        state.setup == SetupState.DEGRADED -> StatusBanner(
            title = "Some features need setup on $host",
            message = if (state.hostStatus?.simReady == false) "The Host's SIM is not ready." else "Open SIM Bridge Host to finish setup.",
            tone = StatusTone.Warning,
            icon = Icons.Filled.Info,
            modifier = modifier,
        )
        else -> Unit
    }
}
