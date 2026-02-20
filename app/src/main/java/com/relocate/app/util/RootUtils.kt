// [Relocate] [RootUtils.kt] - Root Command Utilities
// Author: AI
// Purpose: Execute root (su) commands and prepare system files.
// Used by AppFixerService, SpoofService, and hook log preparation.

package com.relocate.app.util

import android.util.Log
import com.relocate.app.SpoofConstants
import com.relocate.app.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootUtils {

    private const val TAG = "RootUtils"
    private val HOOK_LOG_PATH = "/data/local/tmp/${SpoofConstants.HOOK_LOG_FILENAME}"

    /**
     * Run a shell command as root via `su`.
     * Returns true if exit code is 0.
     */
    fun execRoot(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "[ExecRoot] Failed: ${e.message}")
            false
        }
    }

    /**
     * Run a shell command as root and capture stdout.
     */
    fun execRootWithOutput(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "[ExecRootOutput] Failed: ${e.message}")
            ""
        }
    }

    /**
     * Prepare the hook log file so XPosed hooks (running in any app's process)
     * can write to it. On Android, SELinux blocks most apps from writing to
     * /data/local/tmp/. This function uses root to:
     * 1. Create the file if it doesn't exist
     * 2. Set permissions to 666 (world-readable/writable)
     * 3. Set SELinux context to allow all apps to write
     *
     * Must be called once on app startup (before opening any hooked apps).
     */
    suspend fun prepareHookLogFile(): Boolean = withContext(Dispatchers.IO) {
        try {
            val success = execRoot(
                "touch $HOOK_LOG_PATH && " +
                "chmod 666 $HOOK_LOG_PATH && " +
                "chcon u:object_r:app_data_file:s0 $HOOK_LOG_PATH"
            )
            if (success) {
                AppLogger.i(TAG, "[HookLog] Log file prepared: $HOOK_LOG_PATH (chmod 666)")
                Log.i(TAG, "[HookLog] Log file ready at $HOOK_LOG_PATH")
            } else {
                AppLogger.w(TAG, "[HookLog] Failed to prepare log file (no root?)")
                Log.w(TAG, "[HookLog] chmod/chcon failed — root may not be available")
            }
            success
        } catch (e: Exception) {
            AppLogger.e(TAG, "[HookLog] Prepare failed: ${e.message}")
            Log.e(TAG, "[HookLog] Exception: ${e.message}")
            false
        }
    }

    /**
     * Check if the device has root access (Magisk/SuperSU).
     */
    fun hasRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
}
