// [Relocate] [SpoofConstants.kt] - Shared Preference Keys & Constants
// Author: AI
// These constants are used by BOTH the main app (AppFixerService) and the
// XPosed module (UberLocationHook).  They live in this file — which has ZERO
// XPosed imports — so that accessing them from the main app does NOT trigger
// class-loading of XPosed classes (which are compileOnly and absent at runtime).

package com.relocate.app

object SpoofConstants {
    // ── SharedPreferences file name ──────────────────────────────────────────
    const val PREFS_NAME = "spoof_coords"

    // ── Location spoofing keys ───────────────────────────────────────────────
    const val KEY_ACTIVE = "is_active"
    const val KEY_LAT = "lat_bits"           // stored as Long (Double.toBits)
    const val KEY_LNG = "lng_bits"           // stored as Long (Double.toBits)

    // ── Device identity spoofing keys ────────────────────────────────────────
    const val KEY_FAKE_DRM_ID = "fake_drm_id"               // 64 hex char Widevine DRM ID
    const val KEY_FAKE_ANDROID_ID = "fake_android_id"        // 16 hex char android_id
    const val KEY_FAKE_GAID = "fake_gaid"                    // UUID-format Google Advertising ID
    const val KEY_FAKE_FINGERPRINT = "fake_fingerprint"      // spoofed Build.FINGERPRINT string

    // ── Cross-process hook logging ───────────────────────────────────────────
    // XPosed hooks write to this file; Relocate UI reads it for the live console.
    // Path is relative to external storage — both processes can access it.
    const val HOOK_LOG_FILENAME = "relocate_hook_log.txt"
    const val HOOK_LOG_MAX_LINES = 2000

    // ── Uber-related Chrome cookie domains ───────────────────────────────────
    // Only these domains are cleared from Chrome's cookie DB (not all cookies).
    val UBER_COOKIE_DOMAINS = listOf(
        "uber.com",
        "auth.uber.com",
        "partners.uber.com",
        "login.uber.com",
        "m.uber.com",
        "driver.uber.com",
        "riders.uber.com",
        "cn-geo1.uber.com"
    )
}
