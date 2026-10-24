package com.simbridge.client.feature.calls

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.client.data.ContactNames
import com.simbridge.client.feature.messages.Avatar
import com.simbridge.client.session.ClientBridge
import com.simbridge.core.model.CallStatus
import com.simbridge.core.protocol.CallInfo
import com.simbridge.core.ui.components.RoundActionButton
import com.simbridge.core.ui.components.SecondaryButton
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.theme.SimBridgeTheme
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AnswerGreen = Color(0xFF1E9E5A)
private val HangupRed = Color(0xFFD32F2F)

/** Incoming / ongoing call screen. Shows only what the Host reports; it never pretends audio is here. */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {
    @Inject lateinit var active: ActiveCalls
    @Inject lateinit var controller: CallController
    @Inject lateinit var contacts: ContactNames
    @Inject lateinit var bridge: ClientBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent {
            SimBridgeTheme {
                val calls by active.calls.collectAsStateWithLifecycle()
                val dial by controller.dial.collectAsStateWithLifecycle()
                val bridgeState by bridge.state.collectAsStateWithLifecycle()
                val call = ActiveCalls.primary(calls.values)
                val nothingToShow = (call == null || call.status.isTerminal) && dial == null
                LaunchedEffect(nothingToShow) {
                    if (nothingToShow) {
                        delay(2_000)
                        finish()
                    }
                }
                InCallContent(
                    call = call,
                    dial = dial,
                    hostName = bridgeState.hostName ?: "your Host phone",
                    nameFor = contacts::displayName,
                    controller = controller,
                    onClose = {
                        controller.clearDial()
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun InCallContent(
    call: CallInfo?,
    dial: DialAttempt?,
    hostName: String,
    nameFor: (String?) -> String,
    controller: CallController,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val showDial = dial != null && (call == null || call.status.isTerminal)
    val title = nameFor(if (showDial) dial?.number else call?.number)

    fun run(block: suspend () -> String?) {
        busy = true
        scope.launch {
            error = block()
            busy = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            Avatar(title, Modifier)
            Spacer(Modifier.height(Spacing.lg))
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                text = if (showDial) dial?.let { dialLabel(it, hostName) }.orEmpty() else statusLabel(call, hostName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(Spacing.xl))
            StatusBanner(
                title = "Call audio stays on $hostName",
                message = "Speak and listen on the Host phone (or a headset paired to it). Audio on this phone is not supported yet.",
                tone = StatusTone.Info,
                icon = Icons.Filled.Info,
            )
            error?.let {
                Spacer(Modifier.height(Spacing.md))
                StatusBanner(title = "Command failed", message = it, tone = StatusTone.Negative)
            }
            Spacer(Modifier.weight(1f))
            when {
                showDial && dial?.state == DialState.UNKNOWN -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton("Check again with the Host", onClick = { run { controller.recheck(); null } })
                    SecondaryButton("Close", onClick = onClose)
                }
                showDial && dial?.state == DialState.FAILED -> SecondaryButton("Close", onClick = onClose)
                showDial -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("Waiting for the Host to start the call…", style = MaterialTheme.typography.bodyMedium)
                    SecondaryButton("Close this screen", onClick = onClose)
                }
                call == null -> Unit
                call.status == CallStatus.RINGING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundActionButton(Icons.Filled.Close, "Decline", { run { controller.reject(call.callId) } }, HangupRed, enabled = !busy)
                    RoundActionButton(Icons.Filled.Call, "Answer on Host", { run { controller.answer(call.callId) } }, AnswerGreen, enabled = !busy)
                }
                call.status == CallStatus.HOST_UNREACHABLE -> RoundActionButton(
                    Icons.Filled.Refresh, "Waiting for Host", {}, MaterialTheme.colorScheme.outline, enabled = false,
                )
                call.status.isLive -> RoundActionButton(Icons.Filled.Close, "Hang up", { run { controller.hangup(call.callId) } }, HangupRed, enabled = !busy)
                else -> SecondaryButton("Close", onClick = onClose)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun statusLabel(call: CallInfo?, hostName: String): String {
    if (call == null) return "No active call"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.status) {
        while (call.status == CallStatus.ACTIVE) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return when (call.status) {
        CallStatus.RINGING -> "Incoming call to $hostName"
        CallStatus.IDLE, CallStatus.CONNECTING, CallStatus.DIALING -> "Calling…"
        CallStatus.ACTIVE -> DateUtils.formatElapsedTime(((now - (call.connectedAtMs ?: now)) / 1000).coerceAtLeast(0))
        CallStatus.HOLD -> "On hold"
        CallStatus.ENDING -> "Ending…"
        CallStatus.ENDED -> when (call.disconnectReason) {
            "MISSED" -> "Missed call"
            "REJECTED" -> "Declined"
            "BUSY" -> "Busy"
            else -> "Call ended"
        }
        CallStatus.FAILED -> "Call failed"
        CallStatus.HOST_UNREACHABLE -> "Host unreachable. The call may still be active on the Host phone."
    }
}

private fun dialLabel(dial: DialAttempt, hostName: String): String = when (dial.state) {
    DialState.REQUESTING -> "Asking $hostName to call…"
    else -> dial.message ?: "Calling via $hostName…"
}
