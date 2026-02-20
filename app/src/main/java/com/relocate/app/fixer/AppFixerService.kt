// [Relocate] [AppFixerService.kt] - Device Identity Reset & App Cleanup Service
// Author: kashif0700444846
// v1.8.0: Selective identity vector regeneration with checkbox support.
// Reads current IDs, regenerates only user-selected vectors, writes to SharedPrefs for hooks.

package com.relocate.app.fixer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.relocate.app.SpoofConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

object AppFixerService {

    private const val TAG = "[AppFixerService]"

    // ── Identity Vectors — each maps to a checkbox in the UI ─────────────────
    enum class IdentityVector(val label: String, val description: String) {
        CLEAR_APP_DATA("Clear App Data", "Force-stop + pm clear (wipes login)"),
        ANDROID_ID("Android ID", "16-char hex device identifier"),
        DRM_ID("Widevine DRM ID", "x-uber-drm-id header (attestation)"),
        GAID("Google Ad ID", "Advertising ID used for device tracking"),
        FINGERPRINT("Build Fingerprint", "Build.FINGERPRINT + DISPLAY + HOST"),
        CHROME_COOKIES("Clear Uber Chrome Cookies", "Removes only uber.com cookies from Chrome"),
        GMS_CACHE("Clear Play Services Cache", "Resets Google Play Services app set ID"),
        APPOPS_RESET("Reset AppOps", "Resets runtime permission grants")
    }

    enum class StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

    data class FixResult(
        val steps: List<Pair<String, StepStatus>>,
        val newAndroidId: String?,
        val newDrmId: String?,
        val newGaid: String?,
        val newFingerprint: String?
    )

    // ── Current ID Reader ────────────────────────────────────────────────────
    // Reads all current identity values for display in the UI
    data class CurrentIds(
        val androidId: String,
        val drmId: String,
        val gaid: String,
        val fingerprint: String,
        val buildModel: String,
        val buildDevice: String
    )

    suspend fun readCurrentIds(context: Context): CurrentIds = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(SpoofConstants.PREFS_NAME, Context.MODE_WORLD_READABLE)

        // Read real Android ID from Settings.Secure
        val realAndroidId = try {
            val out = execRoot("settings get secure android_id")
            out.ifBlank { "unknown" }
        } catch (e: Exception) {
            "error"
        }

        // Read spoofed DRM ID from SharedPrefs (or "not set")
        val drmId = prefs.getString(SpoofConstants.KEY_FAKE_DRM_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: "not set (using real)"

        // Read GAID via root command
        val gaid = try {
            val out = execRoot("content query --uri content://com.google.android.gms.ads.identifier/ --projection adid 2>/dev/null")
            val match = Regex("adid=([a-f0-9\\-]+)").find(out)
            match?.groupValues?.get(1) ?: "unavailable"
        } catch (e: Exception) {
            "unavailable"
        }

        // Spoofed fingerprint from prefs, or real Build.FINGERPRINT
        val fingerprint = prefs.getString(SpoofConstants.KEY_FAKE_FINGERPRINT, null)
            ?.takeIf { it.isNotBlank() }
            ?: Build.FINGERPRINT

        CurrentIds(
            androidId = realAndroidId,
            drmId = drmId,
            gaid = gaid,
            fingerprint = fingerprint,
            buildModel = Build.MODEL,
            buildDevice = Build.DEVICE
        )
    }

    // ── Selective Fix ────────────────────────────────────────────────────────
    // Only runs the identity vectors the user checked in the UI.
    suspend fun fixApp(
        context: Context,
        packageName: String,
        selectedVectors: Set<IdentityVector>,
        onProgress: (String, StepStatus) -> Unit
    ): FixResult = withContext(Dispatchers.IO) {

        val results = mutableListOf<Pair<String, StepStatus>>()
        var newAndroidId: String? = null
        var newDrmId: String? = null
        var newGaid: String? = null
        var newFingerprint: String? = null

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

        fun skip(label: String) {
            onProgress(label, StepStatus.SKIPPED)
            results.add(label to StepStatus.SKIPPED)
        }

        // ── CLEAR APP DATA ──────────────────────────────────────────────────
        if (IdentityVector.CLEAR_APP_DATA in selectedVectors) {
            step("Force-stop $packageName") {
                execRoot("am force-stop $packageName")
            }
            step("Clear app data (pm clear)") {
                val out = execRoot("pm clear $packageName")
                if (!out.contains("Success", ignoreCase = true)) {
                    throw Exception("pm clear returned: $out")
                }
            }
        } else {
            skip("Clear App Data — skipped")
        }

        // ── ANDROID ID ──────────────────────────────────────────────────────
        if (IdentityVector.ANDROID_ID in selectedVectors) {
            newAndroidId = generateAndroidId()
            step("New Android ID → $newAndroidId") {
                writeSpoofPrefs(context) { prefs ->
                    prefs.putString(SpoofConstants.KEY_FAKE_ANDROID_ID, newAndroidId)
                }
                execRoot("settings put secure android_id $newAndroidId")
            }
        } else {
            skip("Android ID — skipped")
        }

        // ── DRM ID ──────────────────────────────────────────────────────────
        if (IdentityVector.DRM_ID in selectedVectors) {
            newDrmId = generateDrmIdHex()
            step("New DRM ID (Widevine)") {
                writeSpoofPrefs(context) { prefs ->
                    prefs.putString(SpoofConstants.KEY_FAKE_DRM_ID, newDrmId)
                }
            }
        } else {
            skip("DRM ID — skipped")
        }

        // ── GOOGLE ADVERTISING ID ───────────────────────────────────────────
        if (IdentityVector.GAID in selectedVectors) {
            newGaid = generateGaid()
            step("New Google Ad ID → ${newGaid!!.take(8)}...") {
                writeSpoofPrefs(context) { prefs ->
                    prefs.putString(SpoofConstants.KEY_FAKE_GAID, newGaid)
                }
                // Also reset GMS Ad ID storage
                execRoot("rm -rf /data/data/com.google.android.gms/shared_prefs/adid_settings.xml")
                execRoot("rm -rf /data/data/com.google.android.gms/databases/adid.db*")
            }
        } else {
            skip("Google Ad ID — skipped")
        }

        // ── BUILD FINGERPRINT ───────────────────────────────────────────────
        if (IdentityVector.FINGERPRINT in selectedVectors) {
            newFingerprint = generateFingerprint()
            step("Spoof Build.FINGERPRINT") {
                writeSpoofPrefs(context) { prefs ->
                    prefs.putString(SpoofConstants.KEY_FAKE_FINGERPRINT, newFingerprint)
                }
            }
        } else {
            skip("Build Fingerprint — skipped")
        }

        // ── CHROME COOKIES ──────────────────────────────────────────────────
        if (IdentityVector.CHROME_COOKIES in selectedVectors) {
            step("Clear Uber cookies from Chrome") {
                clearUberChromeCookies()
            }
        } else {
            skip("Chrome Cookies — skipped")
        }

        // ── GMS CACHE ───────────────────────────────────────────────────────
        if (IdentityVector.GMS_CACHE in selectedVectors) {
            step("Clear Google Play Services cache") {
                execRoot("pm clear com.google.android.gms")
            }
        } else {
            skip("GMS Cache — skipped")
        }

        // ── APPOPS RESET ────────────────────────────────────────────────────
        if (IdentityVector.APPOPS_RESET in selectedVectors) {
            step("Reset AppOps for $packageName") {
                execRoot("appops reset $packageName")
            }
        } else {
            skip("AppOps Reset — skipped")
        }

        Log.i(TAG, "[Fix] [DONE] Fixed $packageName — vectors: ${selectedVectors.map { it.name }}")

        FixResult(
            steps = results,
            newAndroidId = newAndroidId,
            newDrmId = newDrmId,
            newGaid = newGaid,
            newFingerprint = newFingerprint
        )
    }

    // ── Chrome Cookie Cleaner ────────────────────────────────────────────────
    // Deletes ONLY Uber-related cookies from Chrome's internal SQLite DB.
    // Does NOT touch browsing history, saved passwords, or non-Uber cookies.
    private fun clearUberChromeCookies() {
        val chromeDbPaths = listOf(
            "/data/data/com.android.chrome/app_chrome/Default/Cookies",
            "/data/data/com.chrome.beta/app_chrome/Default/Cookies",
            "/data/data/com.chrome.dev/app_chrome/Default/Cookies"
        )

        for (dbPath in chromeDbPaths) {
            // Check if the DB exists
            val exists = execRoot("ls $dbPath 2>/dev/null")
            if (exists.isBlank()) continue

            for (domain in SpoofConstants.UBER_COOKIE_DOMAINS) {
                val cmd = "sqlite3 $dbPath \"DELETE FROM cookies WHERE host_key LIKE '%$domain%';\""
                try {
                    execRoot(cmd)
                    Log.i(TAG, "[ChromeCookies] Deleted cookies for $domain from $dbPath")
                } catch (e: Exception) {
                    Log.w(TAG, "[ChromeCookies] Failed for $domain in $dbPath: ${e.message}")
                }
            }
        }
    }

    // ── ID Generators ────────────────────────────────────────────────────────

    private fun generateAndroidId(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateDrmIdHex(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateGaid(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Generates a realistic Build.FINGERPRINT string.
     * Format: brand/product/device:version/buildId/buildNumber:type/tags
     * Uses a pool of common stock fingerprints to look authentic.
     */
    private fun generateFingerprint(): String {
        val stockFingerprints = listOf(
            "google/raven/raven:14/UP1A.231005.007/10754064:user/release-keys",
            "google/oriole/oriole:14/UP1A.231005.007/10754064:user/release-keys",
            "samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXS7CXA1:user/release-keys",
            "samsung/b0qxxx/b0q:14/UP1A.231005.007/S908BXXS7DXA1:user/release-keys",
            "OnePlus/CPH2581/OP5961L1:14/UKQ1.231003.002/T.18c2727_1e1c75_1:user/release-keys",
            "motorola/amogus_g/amogus:14/U1TM34.107-34-2/a40e80:user/release-keys"
        )
        return stockFingerprints.random()
    }

    // ── SharedPreferences Writer ─────────────────────────────────────────────
    private fun writeSpoofPrefs(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        val prefs = context.getSharedPreferences(SpoofConstants.PREFS_NAME, Context.MODE_WORLD_READABLE)
        prefs.edit().apply {
            block(this)
            apply()
        }
    }

    // ── Root Command Executor ────────────────────────────────────────────────
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
