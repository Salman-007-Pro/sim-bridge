package com.simbridge.host.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.simbridge.host.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** "Respond via message" from the incoming-call screen; required for the default SMS role. */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {
    @Inject lateinit var gateway: SmsGateway
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (number.isNullOrBlank() || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            gateway.send(clientMessageId = null, to = number, text = text)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
