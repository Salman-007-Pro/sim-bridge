package com.simbridge.host.feature.permissions

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.simbridge.core.ui.components.SectionCard
import com.simbridge.core.ui.components.StatusChip
import com.simbridge.core.ui.theme.Spacing
import com.simbridge.core.ui.theme.StatusTone

/** Lists every permission / role with live status. Re-checks whenever the screen resumes. */
@Composable
fun PermissionList(modifier: Modifier = Modifier, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val items = remember(refresh) { HostPermissions.items(context) }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        onChanged()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh++
        onChanged()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        items.forEach { item ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    when {
                        item.granted -> StatusChip("Allowed", StatusTone.Positive)
                        item.required -> StatusChip("Needed", StatusTone.Warning)
                        else -> StatusChip("Optional", StatusTone.Neutral)
                    }
                }
                Text(
                    item.rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.granted) {
                    FilledTonalButton(onClick = {
                        when (val request = item.request) {
                            is PermissionRequest.Runtime -> runtimeLauncher.launch(request.permissions.toTypedArray())
                            is PermissionRequest.Role -> {
                                val rm = context.getSystemService(RoleManager::class.java)
                                roleLauncher.launch(rm.createRequestRoleIntent(request.role))
                            }
                        }
                    }) { Text("Allow") }
                }
            }
        }
    }
}
