// [Relocate] [SpoofConstants.kt] - Shared Preference Keys
// Author: AI
// These constants are used by BOTH the main app (AppFixerService) and the
// XPosed module (UberLocationHook).  They live in this file — which has ZERO
// XPosed imports — so that accessing them from the main app does NOT trigger
// class-loading of XPosed classes (which are compileOnly and absent at runtime).

package com.relocate.app

object SpoofConstants {
    const val PREFS_NAME = "spoof_coords"
    const val KEY_ACTIVE = "is_active"
    const val KEY_LAT = "lat_bits"           // stored as Long (Double.toBits)
    const val KEY_LNG = "lng_bits"           // stored as Long (Double.toBits)
    const val KEY_FAKE_DRM_ID = "fake_drm_id"         // hex string of spoofed Widevine DRM ID
    const val KEY_FAKE_ANDROID_ID = "fake_android_id"  // spoofed android_id
}
