package com.simbridge.client.feature.calls

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simbridge.client.data.ContactNames
import com.simbridge.client.feature.calls.data.CallEntity
import com.simbridge.client.feature.calls.data.CallHistoryRepository
import com.simbridge.client.feature.messages.Avatar
import com.simbridge.core.model.CallDirection
import com.simbridge.core.model.CallStatus
import com.simbridge.core.ui.components.EmptyState
import com.simbridge.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CallRow(val call: CallEntity, val title: String)

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val repository: CallHistoryRepository,
    private val controller: CallController,
    contacts: ContactNames,
) : ViewModel() {
    val calls: StateFlow<List<CallRow>> = repository.history()
        .map { list -> list.map { CallRow(it, contacts.displayName(it.number)) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markSeen() {
        viewModelScope.launch { repository.markAllSeen() }
    }

    fun callBack(number: String) {
        viewModelScope.launch { controller.dial(number) }
    }
}

@Composable
fun CallHistoryScreen(viewModel: CallHistoryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.markSeen() }
    if (calls.isEmpty()) {
        EmptyState(Icons.Filled.Call, "No calls yet", "Calls to and from your Host phone's number appear here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(calls, key = { it.call.callId }) { row ->
            CallItem(row) { number ->
                context.startActivity(Intent(context, InCallActivity::class.java))
                viewModel.callBack(number)
            }
            HorizontalDivider(Modifier.padding(start = 76.dp))
        }
    }
}

@Composable
private fun CallItem(row: CallRow, onCall: (String) -> Unit) {
    val call = row.call
    val (label, isProblem) = when {
        call.missed -> "Missed" to true
        call.status == CallStatus.FAILED -> "Failed" to true
        call.disconnectReason == "REJECTED" -> "Declined" to false
        call.direction == CallDirection.INCOMING -> "Incoming" to false
        else -> "Outgoing" to false
    }
    val duration = if (call.connectedAtMs != null && call.endedAtMs != null) {
        " · " + DateUtils.formatElapsedTime((call.endedAtMs - call.connectedAtMs) / 1000)
    } else {
        ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(row.title)
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$label · ${DateUtils.getRelativeTimeSpanString(call.startedAtMs)}$duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val number = call.number
        if (number != null) {
            IconButton(onClick = { onCall(number) }) {
                Icon(Icons.Filled.Call, contentDescription = "Call ${row.title}", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
