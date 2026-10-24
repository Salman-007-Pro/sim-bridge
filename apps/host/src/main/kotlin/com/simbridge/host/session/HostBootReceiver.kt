package com.simbridge.host.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.host.data.HostPrefs
import com.simbridge.host.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Restarts the bridge after reboot or an app update, but only if the user had it switched on. */
@AndroidEntryPoint
class HostBootReceiver : BroadcastReceiver() {
    @Inject lateinit var prefs: HostPrefs
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                if (prefs.isBridgeEnabled()) {
                    runCatching { HostBridgeService.start(context) }
                        .onFailure { BridgeLog.w("Boot", "Could not restart bridge service", it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
