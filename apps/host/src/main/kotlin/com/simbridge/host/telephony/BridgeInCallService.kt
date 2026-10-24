package com.simbridge.host.telephony

import android.telecom.Call
import android.telecom.InCallService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Bound by Telecom while SIM Bridge Host is the default phone app; the source of all call events. */
@AndroidEntryPoint
class BridgeInCallService : InCallService() {
    @Inject lateinit var registry: CallRegistry
    @Inject lateinit var notifier: HostCallNotifier

    override fun onCreate() {
        super.onCreate()
        registry.attach(this)
        notifier.start()
    }

    override fun onCallAdded(call: Call) {
        registry.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        registry.onCallRemoved(call)
    }

    override fun onDestroy() {
        notifier.stop()
        registry.detach(this)
        super.onDestroy()
    }
}
