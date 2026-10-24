package com.simbridge.host.sms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.ui.theme.SimBridgeTheme
import com.simbridge.core.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** SENDTO / SEND handler required for the default SMS role: a simple thread view and composer on the Host. */
@AndroidEntryPoint
class HostComposeActivity : ComponentActivity() {
    @Inject lateinit var gateway: SmsGateway
    @Inject lateinit var repository: HostSmsRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialRecipient = intent.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        val initialText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body").orEmpty()
        setContent {
            SimBridgeTheme {
                val scope = rememberCoroutineScope()
                var recipient by remember { mutableStateOf(initialRecipient) }
                var text by remember { mutableStateOf(initialText) }
                var error by remember { mutableStateOf<String?>(null) }
                val thread by remember(recipient) {
                    if (recipient.isBlank()) flowOf(emptyList()) else repository.thread(recipient)
                }.collectAsState(initial = emptyList())

                Scaffold(topBar = { TopAppBar(title = { Text(recipient.ifBlank { "New message" }) }) }) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        if (initialRecipient.isBlank()) {
                            OutlinedTextField(
                                value = recipient,
                                onValueChange = { recipient = it },
                                label = { Text("To") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(thread, key = { it.id }) { sms ->
                                val inbound = sms.direction == SmsDirection.INBOUND
                                Text(
                                    text = (if (inbound) "← " else "→ ") + sms.body,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (inbound) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text("Message") },
                                modifier = Modifier.weight(1f),
                            )
                            FilledIconButton(
                                enabled = recipient.isNotBlank() && text.isNotBlank(),
                                onClick = {
                                    val body = text
                                    scope.launch {
                                        when (val result = gateway.send(null, recipient, body)) {
                                            is SmsSendResult.Refused -> error = result.message
                                            else -> {
                                                error = null
                                                text = ""
                                            }
                                        }
                                    }
                                },
                            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                        }
                    }
                }
            }
        }
    }
}
