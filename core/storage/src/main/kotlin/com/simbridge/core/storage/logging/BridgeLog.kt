package com.simbridge.core.storage.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured, in-memory ring-buffer log for the redacted debug export.
 * Callers must pass values through [com.simbridge.core.model.Redaction]; nothing here is persisted.
 */
object BridgeLog {
    private const val CAPACITY = 600
    private val buffer = ArrayDeque<String>(CAPACITY)
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun i(tag: String, message: String) = record('I', tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = record('W', tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = record('E', tag, message, error)

    @Synchronized
    fun export(): String = buffer.joinToString("\n")

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    private fun record(level: Char, tag: String, message: String, error: Throwable?) {
        val errorPart = error?.let { " [${it::class.java.simpleName}]" }.orEmpty()
        val line = "${format.format(Date())} $level/$tag: $message$errorPart"
        if (buffer.size == CAPACITY) buffer.removeFirst()
        buffer.addLast(line)
        when (level) {
            'E' -> Log.e(TAG_PREFIX + tag, message + errorPart)
            'W' -> Log.w(TAG_PREFIX + tag, message + errorPart)
            else -> Log.i(TAG_PREFIX + tag, message)
        }
    }

    private const val TAG_PREFIX = "SB/"
}
