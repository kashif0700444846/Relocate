// [Relocate] [AppFixerService.kt] - Device Identity Reset & App Cleanup Service
// Author: kashif0700444846
// Runs root-powered cleanup steps to fix ride-hailing app authentication issues.
// Writes spoofed DRM ID and Android ID to SharedPreferences for Hook 12 & 13.

package com.relocate.app.fixer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.relocate.app.SpoofConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

object AppFixerService {

    private const val TAG = "[AppFixerService]"

    data class FixStep(
        val label: String,
        val status: StepStatus = StepStatus.PENDING
    )

    enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

    data class FixResult(
        val steps: List<Pair<String, StepStatus>>,
        val newAndroidId: String,
        val newDrmId: String
    )

    /**
     * Full fix for a selected app package.
     * 1. Force-stop the app
     * 2. Clear app data (pm clear)
     * 3. Generate + save new spoofed Android ID (written to SharedPreferences → Hook 13)
     * 4. Generate + save new spoofed DRM ID (written to SharedPreferences → Hook 12)
     * 5. Reset real Android ID in system settings (root)
     * 6. Clear Google Play Services app set ID cache
     */
    suspend fun fixApp(
        context: Context,
        packageName: String,
        onProgress: (String, StepStatus) -> Unit
    ): FixResult = withContext(Dispatchers.IO) {

        val results = mutableListOf<Pair<String, StepStatus>>()

        fun step(label: String, block: () -> Unit): StepStatus {
            onProgress(label, StepStatus.RUNNING)
            return try {
                block()
                onProgress(label, StepStatus.SUCCESS)
                results.add(label to StepStatus.SUCCESS)
                StepStatus.SUCCESS
            } catch (e: Exception) {
                Log.e(TAG, "[$label] FAILED: ${e.message}")
                onProgress(label, StepStatus.FAILED)
                results.add(label to StepStatus.FAILED)
                StepStatus.FAILED
            }
        }

        // Step 1: Force stop
        step("Force-stop $packageName") {
            execRoot("am force-stop $packageName")
        }

        // Step 2: Clear app data
        step("Clear app data (pm clear)") {
            val out = execRoot("pm clear $packageName")
            if (!out.contains("Success", ignoreCase = true)) {
                throw Exception("pm clear returned: $out")
            }
        }

        // Step 3a: Generate new fake Android ID → write to SharedPreferences for Hook 13
        val newAndroidId = generateAndroidId()
        step("Generate new Android ID → $newAndroidId") {
            writeSpoofPrefs(context) { prefs ->
                prefs.putString(SpoofConstants.KEY_FAKE_ANDROID_ID, newAndroidId)
            }
            // Also write to real system settings (root)
            execRoot("settings put secure android_id $newAndroidId")
        }

        // Step 3b: Generate new fake DRM ID → write to SharedPreferences for Hook 12
        val newDrmId = generateDrmIdHex()
        step("Spoof Widevine DRM ID (x-uber-drm-id)") {
            writeSpoofPrefs(context) { prefs ->
                prefs.putString(SpoofConstants.KEY_FAKE_DRM_ID, newDrmId)
            }
        }

        // Step 4: Clear Google Play Services App Set ID cache
        step("Clear Google Play Services cache") {
            execRoot("pm clear com.google.android.gms")
        }

        // Step 5: Reset AppOps for the target package
        step("Reset AppOps permissions") {
            execRoot("appops reset $packageName")
        }

        Log.i(TAG, "[Fix] [DONE] Fixed $packageName — new androidId=$newAndroidId")

        FixResult(
            steps = results,
            newAndroidId = newAndroidId,
            newDrmId = newDrmId
        )
    }

    /**
     * Generates a realistic 16-char hex Android ID.
     */
    private fun generateAndroidId(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a 32-byte (64 hex char) fake DRM ID.
     */
    private fun generateDrmIdHex(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Writes to the world-readable SharedPreferences file that XSharedPreferences reads.
     */
    private fun writeSpoofPrefs(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        val prefs = context.getSharedPreferences(SpoofConstants.PREFS_NAME, Context.MODE_WORLD_READABLE)
        prefs.edit().apply {
            block(this)
            apply()
        }
    }

    /**
     * Execute a command as root.
     */
    private fun execRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (error.isNotBlank()) Log.w(TAG, "[ExecRoot] $command → $error")
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "[ExecRoot] ERROR $command: ${e.message}")
            ""
        }
    }
}
