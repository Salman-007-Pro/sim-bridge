package com.simbridge.host.audiolab

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Process
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import kotlin.system.exitProcess

/**
 * Runs inside the shell process started by Shizuku (uid 2000). Audio attribution must name the
 * shell package, otherwise AppOps rejects the uid / package mismatch.
 */
class ShellContext(base: Context) : ContextWrapper(base) {
    override fun getPackageName(): String = SHELL_PACKAGE
    override fun getOpPackageName(): String = SHELL_PACKAGE
    override fun getApplicationContext(): Context = this

    @RequiresApi(Build.VERSION_CODES.S)
    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid()).setPackageName(SHELL_PACKAGE).build()

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"
    }
}

/** Shizuku user service. Constructors must stay public: Shizuku instantiates this class by reflection. */
@Keep
class AudioProbeUserService() : IAudioProbeService.Stub() {
    private var context: Context? = null

    @Keep
    constructor(context: Context) : this() {
        this.context = context
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun identity(): String = "uid=${Process.myUid()} pid=${Process.myPid()} context=${context != null}"

    override fun probeCapture(source: Int, sampleRateHz: Int, durationMs: Int): String =
        AudioProbes.capture(context?.let(::ShellContext), source, sampleRateHz, durationMs)

    override fun probeTelephonyTx(durationMs: Int): String =
        context?.let { AudioProbes.injectTone(ShellContext(it), durationMs) } ?: "No context in shell process"
}
