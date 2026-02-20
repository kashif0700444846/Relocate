// [Relocate] [AppLogger.kt] - In-App Log Viewer + Cross-Process Hook Log Reader
// Author: AI
// Thread-safe ring-buffer logger that captures log entries for the in-app log tab.
// Also reads the shared hook log file written by XPosed hooks in target app processes.
// All log calls also forward to Android's standard Log for ADB visibility.

package com.relocate.app.logging

import android.os.Environment
import android.util.Log
import com.relocate.app.SpoofConstants
import java.io.File
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

    /**
     * Represents a log entry from an XPosed hook running in another process.
     * Written by UberLocationHook, read by Relocate's Live Console.
     */
    data class HookEntry(
        val timestamp: String,
        val processName: String,
        val hookId: String,
        val details: String
    ) {
        override fun toString(): String = "$timestamp [$hookId] $processName: $details"
    }

    private const val MAX_ENTRIES = 1000
    private val entries = CopyOnWriteArrayList<Entry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Get a snapshot of all app log entries. */
    fun getEntries(): List<Entry> = entries.toList()

    /** Clear all app log entries. */
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
        while (entries.size > MAX_ENTRIES) {
            try { entries.removeAt(0) } catch (_: Exception) { break }
        }
    }

    // ── Cross-Process Hook Log Reader ────────────────────────────────────────
    // Reads the shared log file written by XPosed hooks running in target processes.
    // Format per line: "timestamp|processName|hookId|details"

    private var lastReadLineCount: Int = 0

    /**
     * Read new hook log entries since last read.
     * Returns only the NEW entries since the last call (delta).
     * Call this on a background thread (does file I/O).
     */
    fun readNewHookEntries(): List<HookEntry> {
        try {
            val logFile = getHookLogFile()
            if (!logFile.exists() || logFile.length() == 0L) return emptyList()

            val allLines = logFile.readLines()
            if (allLines.size <= lastReadLineCount) return emptyList()

            val newLines = allLines.drop(lastReadLineCount)
            lastReadLineCount = allLines.size

            return newLines.mapNotNull { parseHookLogLine(it) }
        } catch (e: Exception) {
            Log.w("AppLogger", "[HookLog] Read failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Read ALL hook log entries (for initial load or refresh).
     */
    fun readAllHookEntries(): List<HookEntry> {
        try {
            val logFile = getHookLogFile()
            if (!logFile.exists() || logFile.length() == 0L) return emptyList()

            val allLines = logFile.readLines()
            lastReadLineCount = allLines.size

            return allLines.mapNotNull { parseHookLogLine(it) }
        } catch (e: Exception) {
            Log.w("AppLogger", "[HookLog] Read all failed: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Clear the hook log file.
     */
    fun clearHookLog() {
        try {
            val logFile = getHookLogFile()
            if (logFile.exists()) {
                logFile.writeText("")
                lastReadLineCount = 0
            }
        } catch (e: Exception) {
            Log.w("AppLogger", "[HookLog] Clear failed: ${e.message}")
        }
    }

    private fun getHookLogFile(): File {
        val logDir = File(Environment.getExternalStorageDirectory(), "Relocate")
        return File(logDir, SpoofConstants.HOOK_LOG_FILENAME)
    }

    private fun parseHookLogLine(line: String): HookEntry? {
        if (line.isBlank()) return null
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        return HookEntry(
            timestamp = parts[0].trim(),
            processName = parts[1].trim(),
            hookId = parts[2].trim(),
            details = parts[3].trim()
        )
    }
}
