package com.simbridge.host.audiolab

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.simbridge.host.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_RUNNING, PERMISSION_REQUIRED, READY }

/** Provider B experiment: run the same probes as the shell user through Shizuku (ADB-level, no root). */
@Singleton
class ShizukuLab @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun status(): ShizukuStatus = when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) || Shizuku.isPreV11() -> ShizukuStatus.NOT_RUNNING
        Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.PERMISSION_REQUIRED
        else -> ShizukuStatus.READY
    }

    fun serverUid(): Int? = runCatching { Shizuku.getUid() }.getOrNull()

    fun requestPermission(onResult: (Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    /** Binds the shell-side service, runs [block] off the main thread, then tears the process down. */
    suspend fun <T> withShellService(block: (IAudioProbeService) -> T): T? {
        if (status() != ShizukuStatus.READY) return null
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, AudioProbeUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("audioprobe")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        var connection: ServiceConnection? = null
        val service = withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine<IAudioProbeService> { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder != null && binder.pingBinder() && cont.isActive) cont.resume(IAudioProbeService.Stub.asInterface(binder))
                    }
                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                }
                connection = conn
                Shizuku.bindUserService(args, conn)
            }
        } ?: return null
        return try {
            block(service)
        } finally {
            connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }
        }
    }

    private companion object {
        const val REQUEST_CODE = 4_711
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
