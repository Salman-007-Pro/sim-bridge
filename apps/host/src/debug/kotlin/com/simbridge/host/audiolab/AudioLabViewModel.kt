package com.simbridge.host.audiolab

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simbridge.core.protocol.CallInfo
import com.simbridge.host.telephony.CallRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LabEntry(val time: String, val probe: String, val result: String)

data class AudioLabState(
    val running: String? = null,
    val shizuku: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val shizukuUid: Int? = null,
    val entries: List<LabEntry> = emptyList(),
)

@HiltViewModel
class AudioLabViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shizuku: ShizukuLab,
    registry: CallRegistry,
) : ViewModel() {
    private val _state = MutableStateFlow(AudioLabState())
    val state: StateFlow<AudioLabState> = _state.asStateFlow()
    val calls: StateFlow<List<CallInfo>> = registry.calls

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        refreshShizuku()
    }

    fun refreshShizuku() = _state.update { it.copy(shizuku = shizuku.status(), shizukuUid = shizuku.serverUid()) }

    fun requestShizukuPermission() = shizuku.requestPermission { refreshShizuku() }

    fun runAppCaptures() = run("App capture probes") {
        AudioProbes.sources.forEach { source ->
            record("app · ${source.label}", AudioProbes.capture(context, source.id))
        }
    }

    fun runAppTone() = run("App telephony TX tone") {
        record("app · telephony TX tone", AudioProbes.injectTone(context))
    }

    fun runShellCaptures() = run("Shell capture probes") {
        val done = shizuku.withShellService { service ->
            record("shell · identity", service.identity())
            AudioProbes.sources.forEach { source ->
                record("shell · ${source.label}", service.probeCapture(source.id, AudioProbes.SAMPLE_RATE, 3_000))
            }
        }
        if (done == null) record("shell", "Shizuku is not ready (start Shizuku and grant permission)")
    }

    fun runShellTone() = run("Shell telephony TX tone") {
        val result = shizuku.withShellService { it.probeTelephonyTx(4_000) }
        record("shell · telephony TX tone", result ?: "Shizuku is not ready")
    }

    fun runHfp() = run("HFP client probe") {
        record("bluetooth · HFP client", AudioProbes.hfpClient(context))
    }

    fun report(): String = buildString {
        appendLine("SIM Bridge Audio Lab results")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("SoC: ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        appendLine("Patch: ${Build.VERSION.SECURITY_PATCH}")
        appendLine("Build: ${Build.FINGERPRINT}")
        appendLine("Shizuku: ${_state.value.shizuku} uid=${_state.value.shizukuUid}")
        appendLine()
        _state.value.entries.forEach { appendLine("${it.time}  ${it.probe}: ${it.result}") }
    }

    fun clear() = _state.update { it.copy(entries = emptyList()) }

    private fun run(name: String, block: suspend () -> Unit) {
        if (_state.value.running != null) return
        _state.update { it.copy(running = name) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } finally {
                _state.update { it.copy(running = null) }
                refreshShizuku()
            }
        }
    }

    private fun record(probe: String, result: String) {
        val entry = LabEntry(clock.format(Date()), probe, result)
        _state.update { it.copy(entries = it.entries + entry) }
    }
}
