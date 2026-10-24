package com.simbridge.host.feature.probe

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import com.simbridge.core.model.AudioStatus
import com.simbridge.core.model.CapabilityStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val soc: String,
    val androidRelease: String,
    val apiLevel: Int,
    val securityPatch: String,
    val buildFingerprint: String,
    val telephony: TelephonyReport,
    val roles: RoleReport,
    val connectivity: ConnectivityReport,
    val audio: AudioStatus,
) {
    val callsCapability: CapabilityStatus
        get() = when {
            !telephony.hasTelephony || !roles.dialerAvailable -> CapabilityStatus.UNSUPPORTED
            !roles.dialerHeld -> CapabilityStatus.NEEDS_SETUP
            else -> CapabilityStatus.SUPPORTED
        }

    val smsCapability: CapabilityStatus
        get() = when {
            !telephony.hasMessaging || !roles.smsAvailable -> CapabilityStatus.UNSUPPORTED
            !roles.smsHeld -> CapabilityStatus.NEEDS_SETUP
            else -> CapabilityStatus.SUPPORTED
        }

    fun toMarkdownRow(): String = listOf(
        "$brand $model", manufacturer, soc, "Android $androidRelease (API $apiLevel)", securityPatch,
        telephony.simStateLabel, telephony.simOperator ?: "-", "${telephony.activeModems}",
        yesNo(roles.dialerAvailable), yesNo(roles.smsAvailable),
        yesNo(connectivity.wifiDirect), yesNo(connectivity.bluetoothLe), audio.name, buildFingerprint,
    ).joinToString(prefix = "| ", separator = " | ", postfix = " |")

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}

data class TelephonyReport(
    val hasTelephony: Boolean,
    val hasCalling: Boolean,
    val hasMessaging: Boolean,
    val simStateLabel: String,
    val simReady: Boolean,
    val simOperator: String?,
    val networkOperator: String?,
    val activeModems: Int,
    val signalLevel: Int?,
)

data class RoleReport(
    val dialerAvailable: Boolean,
    val dialerHeld: Boolean,
    val smsAvailable: Boolean,
    val smsHeld: Boolean,
)

data class ConnectivityReport(
    val wifi: Boolean,
    val wifiDirect: Boolean,
    val bluetooth: Boolean,
    val bluetoothLe: Boolean,
)

/** Reads only what the OS exposes; values that cannot be read are reported as unknown, never invented. */
@Singleton
class CapabilityProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun run(): DeviceReport {
        val pm = context.packageManager
        return DeviceReport(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "unknown",
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildFingerprint = Build.FINGERPRINT,
            telephony = telephony(pm),
            roles = roles(),
            connectivity = ConnectivityReport(
                wifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI),
                wifiDirect = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT),
                bluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
                bluetoothLe = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            ),
            audio = AudioStatus.UNSUPPORTED,
        )
    }

    @SuppressLint("MissingPermission")
    private fun telephony(pm: PackageManager): TelephonyReport {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val hasCalling = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING)
        } else {
            hasTelephony
        }
        val hasMessaging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
        } else {
            hasTelephony
        }
        if (tm == null || !hasTelephony) {
            return TelephonyReport(hasTelephony, hasCalling, hasMessaging, "No telephony", false, null, null, 0, null)
        }
        val simState = tm.simState
        return TelephonyReport(
            hasTelephony = true,
            hasCalling = hasCalling,
            hasMessaging = hasMessaging,
            simStateLabel = simStateLabel(simState),
            simReady = simState == TelephonyManager.SIM_STATE_READY,
            simOperator = tm.simOperatorName.takeIf { it.isNotBlank() },
            networkOperator = tm.networkOperatorName.takeIf { it.isNotBlank() },
            activeModems = tm.activeModemCount,
            signalLevel = runCatching { tm.signalStrength?.level }.getOrNull(),
        )
    }

    private fun roles(): RoleReport {
        val rm = context.getSystemService(RoleManager::class.java)
        return RoleReport(
            dialerAvailable = rm?.isRoleAvailable(RoleManager.ROLE_DIALER) == true,
            dialerHeld = rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true,
            smsAvailable = rm?.isRoleAvailable(RoleManager.ROLE_SMS) == true,
            smsHeld = rm?.isRoleHeld(RoleManager.ROLE_SMS) == true,
        )
    }

    private fun simStateLabel(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
        TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "Disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card error"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restricted"
        else -> "Unknown"
    }
}
