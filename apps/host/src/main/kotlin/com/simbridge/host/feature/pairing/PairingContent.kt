package com.simbridge.host.feature.pairing

import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simbridge.core.crypto.PairingPayload
import com.simbridge.core.ui.components.IconBadge
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.SasCodeDisplay
import com.simbridge.core.ui.components.SecondaryButton
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusBanner
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone
import com.simbridge.host.session.HostBridgeState
import com.simbridge.host.session.PairingState
import kotlinx.coroutines.delay

/** QR pairing + explicit Host approval. Rendered inside onboarding and as a standalone screen. */
@Composable
fun PairingContent(
    pairing: PairingState,
    bridge: HostBridgeState,
    onShowCode: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureWindow()
    LaunchedEffect(bridge.running, pairing) {
        if (bridge.running && pairing is PairingState.Idle) onShowCode()
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        when (pairing) {
            PairingState.Idle -> WaitingCard(if (bridge.running) "Creating a pairing code…" else "Starting the bridge…")
            is PairingState.Showing -> QrCard(pairing.payload)
            is PairingState.AwaitingApproval -> {
                SectionCard(title = "Pairing request") {
                    Text(
                        "\"${pairing.request.clientName}\" wants to use this phone's SIM for calls and SMS.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    SasCodeDisplay(pairing.request.sas)
                    Text(
                        "Approve only if your other phone shows exactly the same code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton("Codes match — approve", onClick = onApprove)
                    SecondaryButton("Reject", onClick = onReject, destructive = true)
                }
            }
            is PairingState.Completed -> {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        IconBadge(Icons.Filled.CheckCircle, size = 48)
                        Column(Modifier.weight(1f)) {
                            Text("Paired with ${pairing.clientName}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Your Client phone can now reach this SIM while both are on the same network.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    PrimaryButton("Continue", onClick = onDone)
                }
            }
            is PairingState.Failed -> {
                StatusBanner(title = "Pairing did not complete", message = pairing.message, tone = StatusTone.Negative, icon = Icons.Filled.Warning)
                PrimaryButton("Create a new code", onClick = onShowCode)
            }
        }
    }
}

@Composable
private fun QrCard(payload: PairingPayload) {
    val context = LocalContext.current
    val uri = remember(payload) { payload.toUri() }
    val image = remember(uri) { QrCode.render(uri) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(payload) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingSec = ((payload.expiresAtMs - now) / 1000).coerceAtLeast(0)

    SectionCard(title = "Scan with your Client phone") {
        Surface(
            color = Color.White,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = "Pairing QR code" },
        ) {
            Image(bitmap = image, contentDescription = null, modifier = Modifier.padding(Spacing.lg))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Waiting for your other phone…", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Code expires in %d:%02d · %s".format(remainingSec / 60, remainingSec % 60, payload.addresses.first()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, uri)
            context.startActivity(Intent.createChooser(send, "Share pairing link"))
        }) { Text("No camera? Share the pairing link instead") }
    }
}

@Composable
private fun WaitingCard(text: String) {
    SectionCard {
        Box(Modifier.fillMaxWidth().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(text)
            }
        }
    }
}

/** The pairing code is a short-lived secret: block screenshots and screen recording while it is shown. */
@Composable
private fun SecureWindow() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
