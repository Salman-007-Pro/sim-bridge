package com.simbridge.host.feature.permissions

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

sealed interface PermissionRequest {
    data class Runtime(val permissions: List<String>) : PermissionRequest
    data class Role(val role: String) : PermissionRequest
}

data class PermissionItem(
    val id: String,
    val title: String,
    val rationale: String,
    val granted: Boolean,
    val required: Boolean,
    val request: PermissionRequest,
)

/** Everything the Host needs, requested only with a plain explanation of why. */
object HostPermissions {
    fun items(context: Context): List<PermissionItem> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionItem(
                    id = "notifications",
                    title = "Notifications",
                    rationale = "Shows that the bridge is active and alerts you to pairing requests.",
                    granted = granted(context, Manifest.permission.POST_NOTIFICATIONS),
                    required = true,
                    request = PermissionRequest.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS)),
                ),
            )
        }
        add(
            PermissionItem(
                id = "phone_state",
                title = "Phone status",
                rationale = "Reads SIM state, carrier name and signal so your Client sees the real Host status.",
                granted = granted(context, Manifest.permission.READ_PHONE_STATE),
                required = false,
                request = PermissionRequest.Runtime(listOf(Manifest.permission.READ_PHONE_STATE)),
            ),
        )
        addAll(roleItems(context))
    }

    fun isRoleHeld(context: Context, role: String): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(role) == true

    private fun roleItems(context: Context): List<PermissionItem> {
        val rm = context.getSystemService(RoleManager::class.java) ?: return emptyList()
        return buildList {
            if (rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                add(
                    PermissionItem(
                        id = "role_sms",
                        title = "Default SMS app",
                        rationale = "Needed to send and receive SMS for your Client. While SIM Bridge is the default, " +
                            "your previous SMS app stops receiving new messages; you can switch back any time.",
                        granted = rm.isRoleHeld(RoleManager.ROLE_SMS),
                        required = true,
                        request = PermissionRequest.Role(RoleManager.ROLE_SMS),
                    ),
                )
            }
        }
    }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
