package com.simbridge.client.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simbridge.client.feature.home.HomeScreen
import com.simbridge.client.feature.home.HomeTab
import com.simbridge.client.feature.messages.ConversationScreen
import com.simbridge.client.feature.messages.MessagesTab
import com.simbridge.client.feature.messages.NewMessageScreen
import com.simbridge.client.feature.settings.SettingsScreen
import com.simbridge.client.session.ClientBridge
import com.simbridge.client.session.ClientState
import com.simbridge.core.ui.components.EmptyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class ConversationRoute(val threadKey: String)
@Serializable object NewMessageRoute

@Composable
fun ClientNavHost(
    state: ClientState,
    bridge: ClientBridge,
    scope: CoroutineScope,
    openThread: String?,
    onThreadOpened: () -> Unit,
) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(HomeTab.DIAL) }
    LaunchedEffect(openThread) {
        if (openThread != null) {
            tab = HomeTab.MESSAGES
            nav.navigate(ConversationRoute(openThread)) { launchSingleTop = true }
            onThreadOpened()
        }
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                state = state,
                selected = tab,
                onSelect = { tab = it },
                onRetry = bridge::reconnectNow,
                onResume = bridge::resume,
                onPairAgain = { scope.launch { bridge.forgetHost() } },
            ) { selected ->
                when (selected) {
                    HomeTab.DIAL -> EmptyState(Icons.Filled.Call, "Dial pad", "Calling arrives in the next update.")
                    HomeTab.MESSAGES -> MessagesTab(
                        onOpenThread = { nav.navigate(ConversationRoute(it)) },
                        onNewMessage = { nav.navigate(NewMessageRoute) },
                    )
                    HomeTab.CALLS -> EmptyState(Icons.Filled.Call, "Call history", "Call history arrives in the next update.")
                    HomeTab.SETTINGS -> SettingsScreen()
                }
            }
        }
        composable<ConversationRoute> { ConversationScreen(onBack = { nav.popBackStack() }) }
        composable<NewMessageRoute> {
            NewMessageScreen(
                onBack = { nav.popBackStack() },
                onSent = { threadKey ->
                    nav.navigate(ConversationRoute(threadKey)) { popUpTo(HomeRoute) }
                },
            )
        }
    }
}
