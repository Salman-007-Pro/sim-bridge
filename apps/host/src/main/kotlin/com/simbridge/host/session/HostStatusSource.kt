package com.simbridge.host.session

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.telephony.TelephonyManager
import com.simbridge.core.protocol.HostStatus
import com.simbridge.host.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Polls what the OS exposes about SIM, signal and battery. Unknown values stay null. */
@Singleton
class HostStatusSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    val status: StateFlow<HostStatus> = flow {
        while (true) {
            emit(read())
            delay(POLL_MS)
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5_000), read())

    fun read(): HostStatus {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return HostStatus(
            simReady = tm?.simState == TelephonyManager.SIM_STATE_READY,
            carrierName = tm?.let { (it.networkOperatorName.ifBlank { it.simOperatorName }).ifBlank { null } },
            signalLevel = runCatching { tm?.signalStrength?.level }.getOrNull(),
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = if (plugged >= 0) plugged != 0 else null,
        )
    }

    private companion object {
        const val POLL_MS = 10_000L
    }
}
