package com.simbridge.host.sms

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simbridge.core.model.SmsDirection
import com.simbridge.core.ui.components.EmptyState
import com.simbridge.host.navigation.DetailScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable object HostMessagesRoute

@HiltViewModel
class HostMessagesViewModel @Inject constructor(repository: HostSmsRepository) : ViewModel() {
    val messages: StateFlow<List<SmsEntity>> =
        repository.recent(100).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Minimal message list on the Host itself, so the Host stays usable while it is the default SMS app. */
@Composable
fun HostMessagesScreen(onBack: () -> Unit, viewModel: HostMessagesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    DetailScaffold("Messages on this phone", onBack = onBack) {
        if (messages.isEmpty()) {
            EmptyState(Icons.Filled.Email, "No messages yet", "SMS sent and received through SIM Bridge appear here.")
            return@DetailScaffold
        }
        Column {
            messages.forEach { sms ->
                val direction = if (sms.direction == SmsDirection.INBOUND) "Received" else "Sent · ${sms.status.name.lowercase()}"
                ListItem(
                    overlineContent = { Text("$direction · ${DateUtils.getRelativeTimeSpanString(sms.timestampMs)}") },
                    headlineContent = { Text(sms.address) },
                    supportingContent = { Text(sms.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(context, HostComposeActivity::class.java).setData(Uri.parse("smsto:${sms.address}")),
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
