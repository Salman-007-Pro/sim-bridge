package com.simbridge.client.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simbridge.client.di.ApplicationScope
import com.simbridge.core.storage.logging.BridgeLog
import com.simbridge.core.storage.pairing.PairedDeviceStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Reconnects to the paired Host after reboot or an app update. */
@AndroidEntryPoint
class ClientBootReceiver : BroadcastReceiver() {
    @Inject lateinit var pairedStore: PairedDeviceStore
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                if (pairedStore.current().isNotEmpty()) {
                    runCatching { ClientBridgeService.start(context) }
                        .onFailure { BridgeLog.w("Boot", "Could not restart connection service", it) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
