package com.simbridge.client.feature.messages

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simbridge.client.feature.messages.data.MessageEntity
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.model.SmsStatus
import com.simbridge.core.ui.components.EmptyState
import com.simbridge.core.ui.theme.Spacing

@Composable
fun MessagesTab(onOpenThread: (String) -> Unit, onNewMessage: () -> Unit, viewModel: ThreadsViewModel = hiltViewModel()) {
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        val list = threads
        when {
            list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            list.isEmpty() -> EmptyState(
                Icons.Filled.Email,
                "No messages yet",
                "SMS to and from your Host phone's number appear here.",
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.summary.threadKey }) { row ->
                    ThreadItem(row, onClick = { onOpenThread(row.summary.threadKey) })
                    HorizontalDivider(Modifier.padding(start = 76.dp))
                }
                item { Box(Modifier.size(88.dp)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewMessage,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New message") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg),
        )
    }
}

@Composable
private fun ThreadItem(row: ThreadRow, onClick: () -> Unit) {
    val s = row.summary
    val unread = s.unread > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(row.title)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(s.timestampMs).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val prefix = when {
                    s.direction == SmsDirection.INBOUND -> ""
                    s.status == SmsStatus.FAILED -> "Failed · "
                    else -> "You: "
                }
                Text(
                    prefix + s.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.status == SmsStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) Badge { Text(s.unread.toString()) }
            }
        }
    }
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier) {
    val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
    Box(
        modifier = modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(onBack: () -> Unit, viewModel: ConversationViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (viewModel.title != viewModel.threadKey) {
                            Text(viewModel.threadKey, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
            ) {
                items(messages.asReversed(), key = { it.localId }) { message ->
                    Bubble(message, connected, onRetry = { viewModel.retry(message) })
                }
            }
            Composer(
                draft = draft,
                onDraft = { draft = it },
                hint = if (connected) "Text message" else "Text message · sends when Host is reachable",
                onSend = {
                    viewModel.send(draft)
                    draft = ""
                },
            )
        }
    }
}

@Composable
private fun Bubble(message: MessageEntity, connected: Boolean, onRetry: () -> Unit) {
    val outbound = message.direction == SmsDirection.OUTBOUND
    val failed = message.status == SmsStatus.FAILED
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (outbound) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (outbound) 20.dp else 6.dp,
                bottomEnd = if (outbound) 6.dp else 20.dp,
            ),
            color = when {
                failed -> MaterialTheme.colorScheme.errorContainer
                outbound -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = when {
                failed -> MaterialTheme.colorScheme.onErrorContainer
                outbound -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier),
        ) {
            Text(message.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        val time = DateUtils.formatDateTime(null, message.timestampMs, DateUtils.FORMAT_SHOW_TIME)
        val label = if (outbound) "$time · ${statusLabel(message, connected)}" else time
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Spacing.xs, vertical = 2.dp)
                .semantics { contentDescription = label },
        )
    }
}

private fun statusLabel(message: MessageEntity, connected: Boolean): String = when (message.status) {
    SmsStatus.QUEUED -> if (connected) "Sending to Host…" else "Waiting for Host"
    SmsStatus.SENT_TO_HOST -> "Sent to Host"
    SmsStatus.SUBMITTED_TO_MODEM -> "Sending…"
    SmsStatus.SENT -> "Sent"
    SmsStatus.DELIVERED -> "Delivered"
    SmsStatus.FAILED -> "Failed" + (message.errorText?.let { ": $it" } ?: "") + " · Tap to retry"
    SmsStatus.RECEIVED -> ""
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, hint: String, onSend: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                placeholder = { Text(hint) },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
            )
            FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onBack: () -> Unit,
    onSent: (String) -> Unit,
    viewModel: NewMessageViewModel = hiltViewModel(),
) {
    var to by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val valid = viewModel.isValidRecipient(to)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To") },
                placeholder = { Text("Phone number") },
                singleLine = true,
                isError = to.isNotBlank() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            )
            Box(Modifier.weight(1f))
            Composer(
                draft = text,
                onDraft = { text = it },
                hint = "Text message",
                onSend = { if (valid) viewModel.send(to, text, onSent) },
            )
        }
    }
}
