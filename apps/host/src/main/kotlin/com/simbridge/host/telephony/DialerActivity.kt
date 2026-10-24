package com.simbridge.host.telephony

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.theme.SimBridgeTheme
import com.simbridge.core.ui.theme.Spacing

/**
 * ACTION_DIAL handler required of the default phone app. This is the Host's own dialer, so
 * emergency numbers are NOT blocked here; only remote dials from the Client are restricted.
 */
class DialerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent.data?.schemeSpecificPart.orEmpty()
        setContent {
            SimBridgeTheme {
                var number by remember { mutableStateOf(initial) }
                var error by remember { mutableStateOf<String?>(null) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .imePadding()
                            .padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        Text("Phone", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "SIM Bridge Host is the default phone app so it can share calls with your Client. Emergency calls work normally from here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = { Text("Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        PrimaryButton("Call", enabled = number.isNotBlank(), onClick = { error = placeCall(number) })
                    }
                }
            }
        }
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun placeCall(number: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.CALL_PHONE)
            return "Allow the phone permission, then tap Call again."
        }
        return runCatching {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle())
            startActivity(Intent(this, HostInCallActivity::class.java))
            finish()
        }.exceptionOrNull()?.let { "Could not place the call" }
    }
}
