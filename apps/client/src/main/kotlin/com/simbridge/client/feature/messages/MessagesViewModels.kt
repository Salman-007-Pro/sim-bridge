package com.simbridge.client.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.simbridge.client.data.ContactNames
import com.simbridge.client.feature.messages.data.MessageEntity
import com.simbridge.client.feature.messages.data.MessagesRepository
import com.simbridge.client.feature.messages.data.ThreadSummary
import com.simbridge.client.navigation.ConversationRoute
import com.simbridge.client.session.ClientBridge
import com.simbridge.core.model.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThreadRow(val summary: ThreadSummary, val title: String)

@HiltViewModel
class ThreadsViewModel @Inject constructor(
    repository: MessagesRepository,
    contacts: ContactNames,
) : ViewModel() {
    val threads: StateFlow<List<ThreadRow>?> = repository.threads()
        .map { list -> list.map { ThreadRow(it, contacts.displayName(it.address)) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MessagesRepository,
    private val notifier: MessageNotifier,
    contacts: ContactNames,
    bridge: ClientBridge,
) : ViewModel() {
    val threadKey: String = savedState.toRoute<ConversationRoute>().threadKey
    val title: String = contacts.displayName(threadKey)
    val connected = bridge.state.map { it.setup.isConnected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val messages: StateFlow<List<MessageEntity>> = repository.thread(threadKey)
        .onEach { list ->
            if (list.any { !it.read }) {
                repository.markRead(threadKey)
                notifier.clear(threadKey)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch { repository.send(threadKey, body) }
    }

    fun retry(message: MessageEntity) {
        viewModelScope.launch { repository.retry(message) }
    }
}

@HiltViewModel
class NewMessageViewModel @Inject constructor(private val repository: MessagesRepository) : ViewModel() {
    fun isValidRecipient(to: String) = PhoneNumbers.isDialable(to)

    fun send(to: String, text: String, onSent: (String) -> Unit) {
        viewModelScope.launch { onSent(repository.send(to, text.trim())) }
    }
}
