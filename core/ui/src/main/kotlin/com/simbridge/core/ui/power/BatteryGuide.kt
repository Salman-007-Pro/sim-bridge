package com.simbridge.core.ui.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.simbridge.core.ui.components.PrimaryButton
import com.simbridge.core.ui.components.SecondaryButton
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

/** Manufacturer-specific steps that keep a long-running bridge alive. */
object OemBackgroundTips {
    fun forManufacturer(manufacturer: String = Build.MANUFACTURER): List<String> = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> listOf(
            "Settings › Apps › SIM Bridge › Autostart: on",
            "Battery saver: No restrictions",
            "Recent apps: long-press SIM Bridge and tap the lock icon",
        )
        "samsung" -> listOf(
            "Settings › Battery › Background usage limits › Never sleeping apps: add SIM Bridge",
            "App battery usage: Unrestricted",
        )
        "oppo", "realme", "oneplus" -> listOf(
            "Settings › Apps › SIM Bridge › Battery usage: Allow background activity",
            "Allow auto launch and Allow launch by other apps",
        )
        "vivo", "iqoo" -> listOf(
            "Settings › Battery › Background power consumption: allow SIM Bridge",
            "i Manager › App manager › Autostart: on",
        )
        "huawei", "honor" -> listOf(
            "Settings › Battery › App launch › SIM Bridge: Manage manually, enable all three switches",
        )
        "tecno", "infinix", "itel" -> listOf(
            "Phone Master › App management › Auto-start: on for SIM Bridge",
            "Settings › Battery › App power saving: off for SIM Bridge",
        )
        else -> listOf("Settings › Apps › SIM Bridge › Battery: Unrestricted")
    }
}

fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Live status plus one-tap fixes. Re-checks whenever the screen resumes. */
@SuppressLint("BatteryLife")
@Composable
fun BatteryGuideCard(appRole: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val exempt = remember(refresh) { isIgnoringBatteryOptimizations(context) }
    SectionCard(
        title = "Battery and background",
        modifier = modifier,
        trailing = { if (exempt) StatusChip("Unrestricted", StatusTone.Positive) else StatusChip("May be stopped", StatusTone.Warning) },
    ) {
        Text(
            "The $appRole must keep running in the background or calls and SMS will be missed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!exempt) {
            PrimaryButton("Allow running in background", onClick = {
                val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(request) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            })
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("On this ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} phone also check:", style = MaterialTheme.typography.titleSmall)
            OemBackgroundTips.forManufacturer().forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("•")
                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SecondaryButton("Open app settings", onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        })
    }
}
