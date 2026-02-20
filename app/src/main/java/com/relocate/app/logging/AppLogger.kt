// [Relocate] [AppLogger.kt] - In-App Log Viewer Utility
// Author: AI
// Thread-safe ring-buffer logger that captures log entries for the in-app log tab.
// All log calls also forward to Android's standard Log for ADB visibility.

package com.relocate.app.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogger {

    data class Entry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    ) {
        override fun toString(): String = "$timestamp [$level] $tag: $message"
    }

    private const val MAX_ENTRIES = 500
    private val entries = CopyOnWriteArrayList<Entry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Get a snapshot of all log entries. */
    fun getEntries(): List<Entry> = entries.toList()

    /** Clear all log entries. */
    fun clear() = entries.clear()

    /** Number of entries currently stored. */
    val size: Int get() = entries.size

    // ── Logging methods ─────────────────────────────────────────────────────

    fun d(tag: String, message: String) {
        add("D", tag, message)
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        add("I", tag, message)
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        add("W", tag, message)
        Log.w(tag, message)
    }

    fun e(tag: String, message: String) {
        add("E", tag, message)
        Log.e(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        val msg = if (throwable != null) "$message — ${throwable.message}" else message
        add("E", tag, msg)
        Log.e(tag, message, throwable)
    }

    private fun add(level: String, tag: String, message: String) {
        val entry = Entry(
            timestamp = dateFormat.format(System.currentTimeMillis()),
            level = level,
            tag = tag,
            message = message
        )
        entries.add(entry)
        // Trim old entries if over limit
        while (entries.size > MAX_ENTRIES) {
            try { entries.removeAt(0) } catch (_: Exception) { break }
        }
    }
}
