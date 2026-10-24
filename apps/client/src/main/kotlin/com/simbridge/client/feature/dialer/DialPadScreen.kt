package com.simbridge.client.feature.dialer

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simbridge.client.feature.calls.CallController
import com.simbridge.client.feature.calls.DialState
import com.simbridge.client.feature.calls.InCallActivity
import com.simbridge.client.session.ClientBridge
import com.simbridge.core.ui.components.RoundActionButton
import com.simbridge.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DialPadViewModel @Inject constructor(
    private val controller: CallController,
    bridge: ClientBridge,
) : ViewModel() {
    val state = bridge.state

    fun validate(number: String) = controller.validate(number)

    fun call(number: String, context: Context, onError: (String) -> Unit) {
        viewModelScope.launch {
            context.startActivity(Intent(context, InCallActivity::class.java))
            val attempt = controller.dial(number)
            if (attempt.state == DialState.FAILED) attempt.message?.let(onError)
        }
    }
}

private val keys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

@Composable
fun DialPadScreen(viewModel: DialPadViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var number by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val connected = state.setup.isConnected
    val hint = when {
        !connected -> "Your Host phone is offline. Calls need the Host to be reachable."
        error != null -> error
        else -> "Calls go out from ${state.hostName ?: "your Host phone"}" +
            (state.hostStatus?.carrierName?.let { " · $it" } ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = number.ifEmpty { " " },
            fontSize = 36.sp,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = if (number.isEmpty()) "No number entered" else "Number $number" },
        )
        Text(
            hint.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null || !connected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg),
        )
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    DialKey(
                        digit = digit,
                        letters = letters,
                        onClick = {
                            number += digit
                            error = null
                        },
                        onLongClick = if (digit == "0") ({ number += "+" }) else null,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        Spacer(Modifier.height(Spacing.md))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RoundActionButton(
                icon = Icons.Filled.Call,
                label = "Call",
                onClick = {
                    val problem = viewModel.validate(number)
                    if (problem != null) {
                        error = problem
                    } else {
                        viewModel.call(number, context) { error = it }
                    }
                },
                container = Color(0xFF1E9E5A),
                enabled = connected && number.isNotEmpty(),
            )
            if (number.isNotEmpty()) {
                IconButton(
                    onClick = { number = number.dropLast(1) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = Spacing.lg)
                        .size(56.dp),
                ) { Text("⌫", fontSize = 26.sp, modifier = Modifier.semantics { contentDescription = "Delete digit" }) }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(76.dp)
            .semantics { role = Role.Button }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(digit, fontSize = 30.sp)
            if (letters.isNotEmpty()) Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
