package com.simbridge.core.ui.report

import android.content.Context
import android.content.Intent
import android.os.Build
import com.simbridge.core.model.ReportScrubber

/**
 * Builds a redacted, plain-text report the user can choose to share. Nothing is uploaded automatically.
 * [sections] are app-specific "key: value" lines; [log] is the in-memory bridge log.
 */
object DebugReport {
    fun build(appName: String, version: String, sections: Map<String, List<String>>, log: String): String = buildString {
        appendLine("$appName $version debug report")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Build: ${Build.FINGERPRINT}")
        sections.forEach { (title, lines) ->
            appendLine()
            appendLine("== $title ==")
            lines.forEach(::appendLine)
        }
        appendLine()
        appendLine("== Log (redacted) ==")
        append(log)
    }.let(ReportScrubber::scrub)

    fun share(context: Context, report: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "SIM Bridge debug report")
            .putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(send, "Share debug report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
