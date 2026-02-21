// [Relocate] [DeviceStatusUtils.kt] - Device Permission & Status Checks
// Author: AI
// Purpose: Centralized utilities to check mock location, root access,
// and LSPosed hook status. Used by FeaturesScreen to gate feature access.

package com.relocate.app.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.relocate.app.SpoofConstants
import java.io.File

object DeviceStatusUtils {

    private const val TAG = "DeviceStatus"

    // ── Mock Location Status ────────────────────────────────────────────────

    enum class MockLocationStatus {
        ENABLED,   // Relocate is selected as mock location provider
        DISABLED,  // Mock location not enabled or different app selected
        UNKNOWN    // Cannot determine (API level or permission issue)
    }

    /**
     * Check if this app is selected as the mock location provider.
     * On API 23+, uses AppOpsManager. Below API 23, reads Settings.Secure.
     */
    fun getMockLocationStatus(context: Context): MockLocationStatus {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // API 23+: Use AppOpsManager to check MOCK_LOCATION
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                    ?: return MockLocationStatus.UNKNOWN

                @Suppress("DEPRECATION")
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    MockLocationStatus.ENABLED
                } else {
                    MockLocationStatus.DISABLED
                }
            } else {
                // API < 23: Check Settings.Secure
                @Suppress("DEPRECATION")
                val mockSetting = Settings.Secure.getString(
                    context.contentResolver, "mock_location"
                )
                if (mockSetting == "1") MockLocationStatus.ENABLED else MockLocationStatus.DISABLED
            }
        } catch (e: Exception) {
            Log.w(TAG, "[MockLocation] Check failed: ${e.message}")
            MockLocationStatus.UNKNOWN
        }
    }

    // ── Root Access ─────────────────────────────────────────────────────────

    /**
     * Check if root (su) is available on this device.
     * Wraps RootUtils.hasRoot() for consistency.
     */
    fun isRootAvailable(): Boolean {
        return try {
            RootUtils.hasRoot()
        } catch (e: Exception) {
            Log.w(TAG, "[Root] Check failed: ${e.message}")
            false
        }
    }

    // ── LSPosed / Hook Status ───────────────────────────────────────────────

    enum class LSPosedStatus {
        ACTIVE,    // Hook log file exists and has recent entries
        INACTIVE,  // No hook log or empty
        UNKNOWN
    }

    /**
     * Check if LSPosed hooks appear to be active.
     * We detect this by checking if the hook log file exists and has content,
     * which means hooks have been injected and are writing logs.
     */
    fun getLSPosedStatus(): LSPosedStatus {
        return try {
            val hookLogFile = File("/data/local/tmp", SpoofConstants.HOOK_LOG_FILENAME)
            if (hookLogFile.exists() && hookLogFile.length() > 0) {
                // Check if any entries are recent (within last 24 hours)
                val lastModified = hookLogFile.lastModified()
                val ageMs = System.currentTimeMillis() - lastModified
                val oneDayMs = 24 * 60 * 60 * 1000L
                if (ageMs < oneDayMs) {
                    LSPosedStatus.ACTIVE
                } else {
                    LSPosedStatus.INACTIVE // File exists but stale
                }
            } else {
                LSPosedStatus.INACTIVE
            }
        } catch (e: Exception) {
            Log.w(TAG, "[LSPosed] Check failed: ${e.message}")
            LSPosedStatus.UNKNOWN
        }
    }

    // ── Location Permission ─────────────────────────────────────────────────

    /**
     * Check if fine location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return try {
            val result = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            result == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}
