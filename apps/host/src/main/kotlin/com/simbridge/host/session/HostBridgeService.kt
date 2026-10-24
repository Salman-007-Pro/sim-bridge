package com.simbridge.host.session

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
import com.simbridge.host.MainActivity
import com.simbridge.host.R
import com.simbridge.host.data.HostPrefs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Keeps the bridge running with a visible notification whenever the Host provides access. */
@AndroidEntryPoint
class HostBridgeService : LifecycleService() {
    @Inject lateinit var bridge: HostBridge
    @Inject lateinit var prefs: HostPrefs

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(null, running = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        bridge.start()
        lifecycleScope.launch {
            bridge.state.collect { state ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(state.activeClient?.name, state.running))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            lifecycleScope.launch {
                prefs.setBridgeEnabled(false)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }

    private fun buildNotification(clientName: String?, running: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, HostBridgeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            !running -> "Starting bridge…"
            clientName != null -> "Connected to $clientName. Calls and SMS are shared with it."
            else -> "Waiting for your Client phone"
        }
        return NotificationCompat.Builder(this, HostNotifications.CHANNEL_BRIDGE)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("SIM Bridge is active")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop bridge", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.simbridge.host.action.STOP_BRIDGE"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HostBridgeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HostBridgeService::class.java))
        }
    }
}
