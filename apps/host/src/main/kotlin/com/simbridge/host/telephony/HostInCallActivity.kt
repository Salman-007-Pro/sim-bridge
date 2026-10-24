package com.simbridge.host.telephony

import android.os.Bundle
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.core.model.CallStatus
import com.simbridge.core.ui.components.IconBadge
import com.simbridge.core.ui.components.RoundActionButton
import com.simbridge.core.ui.theme.SimBridgeTheme
import com.simbridge.core.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

/** The Host's own in-call screen (required of the default phone app). Audio stays on this phone. */
@AndroidEntryPoint
class HostInCallActivity : ComponentActivity() {
    @Inject lateinit var registry: CallRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent {
            SimBridgeTheme {
                val calls by registry.calls.collectAsStateWithLifecycle()
                LaunchedEffect(calls.isEmpty()) {
                    if (calls.isEmpty()) {
                        delay(1_200)
                        finish()
                    }
                }
                val call = calls.firstOrNull { it.status == CallStatus.RINGING } ?: calls.lastOrNull()
                var muted by remember { mutableStateOf(false) }
                var speaker by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(Spacing.xxl))
                        IconBadge(Icons.Filled.Call, size = 96)
                        Spacer(Modifier.height(Spacing.lg))
                        Text(call?.number ?: "Private number", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(
                            call?.status?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Call ended",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Your paired Client can also control this call.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        Spacer(Modifier.weight(1f))
                        if (call != null && call.status.isLive && call.status != CallStatus.RINGING) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                FilterChip(selected = muted, onClick = {
                                    muted = !muted
                                    registry.setMuted(muted)
                                }, label = { Text(if (muted) "Muted" else "Mute") })
                                FilterChip(selected = speaker, onClick = {
                                    speaker = !speaker
                                    registry.setSpeaker(speaker)
                                }, label = { Text("Speaker") })
                            }
                            Spacer(Modifier.height(Spacing.xl))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            when {
                                call == null -> Unit
                                call.status == CallStatus.RINGING -> {
                                    RoundActionButton(Icons.Filled.Close, "Decline", { registry.reject(call.callId) }, Color(0xFFD32F2F))
                                    RoundActionButton(Icons.Filled.Call, "Answer", { registry.answer(call.callId) }, Color(0xFF1E9E5A))
                                }
                                else -> RoundActionButton(Icons.Filled.Close, "Hang up", { registry.hangup(call.callId) }, Color(0xFFD32F2F))
                            }
                        }
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }
}
