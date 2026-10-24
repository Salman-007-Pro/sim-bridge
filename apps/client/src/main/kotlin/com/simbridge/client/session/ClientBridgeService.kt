package com.simbridge.client.session

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.simbridge.client.MainActivity
import com.simbridge.client.R
import com.simbridge.core.model.SetupState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the Host link alive in the background so incoming calls and SMS reach this phone. */
@AndroidEntryPoint
class ClientBridgeService : LifecycleService() {
    @Inject lateinit var bridge: ClientBridge

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            build(bridge.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        lifecycleScope.launch {
            bridge.state
                .map { Triple(it.setup, it.hostName, it.paused) }
                .distinctUntilChanged()
                .collect { (setup, _, _) ->
                    if (setup == SetupState.NOT_PAIRED) {
                        stopSelf()
                    } else {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(bridge.state.value))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun build(state: ClientState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val host = state.hostName ?: "your Host phone"
        val text = when {
            state.paused -> "Paused. Calls and SMS will not arrive."
            state.setup.isConnected -> "Connected to $host"
            state.setup == SetupState.REVOKED -> "Access was removed on $host"
            else -> "Looking for $host…"
        }
        return NotificationCompat.Builder(this, ClientNotifications.CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_phone)
            .setContentTitle("SIM Bridge")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ClientBridgeService::class.java))
        }
    }
}
