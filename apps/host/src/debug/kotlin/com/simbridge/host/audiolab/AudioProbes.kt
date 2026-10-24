package com.simbridge.host.audiolab

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.simbridge.core.media.PcmStats

data class CaptureSource(val id: Int, val label: String)

/**
 * PRD 6.4 lab probes. They run in the app process or, through Shizuku, in the shell process.
 * Every result is a plain description of what happened; nothing here can mark audio as supported.
 */
object AudioProbes {
    val sources = listOf(
        CaptureSource(MediaRecorder.AudioSource.VOICE_DOWNLINK, "VOICE_DOWNLINK (far end)"),
        CaptureSource(MediaRecorder.AudioSource.VOICE_UPLINK, "VOICE_UPLINK (near end)"),
        CaptureSource(MediaRecorder.AudioSource.VOICE_CALL, "VOICE_CALL (both)"),
        CaptureSource(MediaRecorder.AudioSource.REMOTE_SUBMIX, "REMOTE_SUBMIX"),
        CaptureSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION, "VOICE_COMMUNICATION (mic, control)"),
        CaptureSource(MediaRecorder.AudioSource.MIC, "MIC (baseline, room sound)"),
    )

    const val SAMPLE_RATE = 16_000

    @SuppressLint("MissingPermission")
    fun capture(context: Context?, source: Int, sampleRateHz: Int = SAMPLE_RATE, durationMs: Int = 3_000): String {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return "getMinBufferSize failed ($minBuffer)"
        val record = try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuffer * 4)
                .apply { if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setContext(context) }
                .build()
        } catch (t: Throwable) {
            return "construct refused: ${t.javaClass.simpleName}: ${t.message}"
        }
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) return "not initialised (blocked by permission or audio policy)"
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return "startRecording refused"
            val total = sampleRateHz * durationMs / 1000
            val pcm = ShortArray(total)
            var read = 0
            while (read < total) {
                val n = record.read(pcm, read, minOf(1_024, total - read))
                if (n <= 0) return "read error $n after $read samples"
                read += n
            }
            val stats = PcmStats.of(pcm, sampleRateHz, read)
            val verdict = if (stats.looksLive) "SIGNAL" else "silence"
            return "captured $read samples · rms %.1f · peak %d · active %.0f%% → %s".format(
                stats.rms, stats.peak, stats.activeRatio * 100, verdict,
            )
        } catch (t: Throwable) {
            return "capture failed: ${t.javaClass.simpleName}: ${t.message}"
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /** Plays a 1 kHz tone towards the telephony TX device if the platform exposes one. */
    fun injectTone(context: Context, durationMs: Int = 4_000): String {
        val audio = context.getSystemService(AudioManager::class.java) ?: return "No AudioManager"
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val telephony = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
            ?: return "No TYPE_TELEPHONY output exposed. Outputs: ${outputs.joinToString { typeName(it.type) }}"
        val tone = PcmStats.tone(1_000.0, SAMPLE_RATE, durationMs)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(tone.size * 2)
                .build()
        } catch (t: Throwable) {
            return "AudioTrack refused: ${t.javaClass.simpleName}: ${t.message}"
        }
        return try {
            track.write(tone, 0, tone.size)
            val preferred = track.setPreferredDevice(telephony)
            track.play()
            Thread.sleep(300)
            val routed = track.routedDevice?.type
            Thread.sleep((durationMs - 300).toLong().coerceAtLeast(0))
            "preferred=$preferred, routed to ${routed?.let(::typeName) ?: "unknown"}. " +
                "Ask the remote party whether they heard a 1 kHz beep (yes = uplink injection candidate)."
        } catch (t: Throwable) {
            "tone failed: ${t.javaClass.simpleName}: ${t.message}"
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Provider D: can an ordinary app reach the Bluetooth HFP client role at all? */
    @SuppressLint("MissingPermission")
    fun hfpClient(context: Context): String {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return "No Bluetooth adapter"
        return runCatching {
            val accepted = adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    adapter.closeProfileProxy(profile, proxy)
                }
                override fun onServiceDisconnected(profile: Int) = Unit
            }, HEADSET_CLIENT)
            if (accepted) "HEADSET_CLIENT proxy request accepted: profile may exist (needs pairing test)" else "HEADSET_CLIENT not available to apps"
        }.getOrElse { "HEADSET_CLIENT probe failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired-headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired-headphones"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb-headset"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote-submix"
        else -> "type-$type"
    }

    /** android.bluetooth.BluetoothProfile.HEADSET_CLIENT is hidden; its value is stable. */
    private const val HEADSET_CLIENT = 16
}
