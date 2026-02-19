/**
 * [Relocate] EagleHook.kt
 * Author: AntiGravity AI — LSPosed Module Hook
 * Purpose: Hooks inside the target navigation app process via LSPosed/XPosed
 *          to intercept ALL mock location detection calls before Eagle sees them.
 *
 * Detection vectors bypassed (from APK reverse engineering of target app v4.546.10003):
 *  1. MOCK_GPS_SETTING_TURNED_ON — Settings.Secure.ALLOW_MOCK_LOCATION
 *  2. MOCK_PROVIDER_FOUND       — LocationManager.getProviders() listing test providers
 *  3. isFromMockProvider()      — Location.isFromMockProvider() flag
 *  4. isMocked / isMock         — Location internal fields (Android 12+)
 *  5. Coordinates are injected  — Location.getLatitude/getLongitude
 */
package com.relocate.app.xposed

import android.content.ContentResolver
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class UberLocationHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "[EagleHook]"
        private const val TARGET_PKG = "com.ubercab.driver"
        private const val RELOCATE_PKG = "com.relocate.app"
        // NOTE: NOT private — accessed by SpoofService cross-class
        const val PREFS_NAME = "spoof_coords"
        const val KEY_ACTIVE = "is_active"
        const val KEY_LAT = "lat_bits"   // stored as Long (Double.toBits)
        const val KEY_LNG = "lng_bits"   // stored as Long (Double.toBits)
    }

    private var prefs: XSharedPreferences? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // ONLY activate inside Uber Driver — never affect other apps
        if (lpparam.packageName != TARGET_PKG) return

        Log.i(TAG, "[Init] [SUCCESS] Eagle process detected — installing 5 hooks")

        // Load shared preferences written by Relocate's SpoofService
        prefs = XSharedPreferences(RELOCATE_PKG, PREFS_NAME)
        prefs?.makeWorldReadable()

        installHookIsFromMockProvider(lpparam.classLoader)
        installHookMockProviderField(lpparam.classLoader)
        installHookLocationProviders(lpparam.classLoader)
        installHookMockLocationSetting(lpparam.classLoader)
        installHookCoordinates(lpparam.classLoader)

        Log.i(TAG, "[Init] [DONE] All hooks installed — Eagle will now see clean location data")
    }

    // ─────────────────────────────────────────────────────────────
    // HOOK 1: Location.isFromMockProvider() → always false
    // This is the primary check Uber reads on every location update.
    // ─────────────────────────────────────────────────────────────
    private fun installHookIsFromMockProvider(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "isFromMockProvider",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return false
                    }
                }
            )
            Log.i(TAG, "[Hook1] [SUCCESS] isFromMockProvider → always false")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook1] [ERROR] isFromMockProvider hook failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HOOK 2: Location.isMock (Android 12+ field) → always false
    // Android 12 added Location.isMock() as the new standard API.
    // Uber reads this on API 31+.
    // ─────────────────────────────────────────────────────────────
    private fun installHookMockProviderField(classLoader: ClassLoader) {
        // API 31+ method: Location.isMock()
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "isMock",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return false
                    }
                }
            )
            Log.i(TAG, "[Hook2] [SUCCESS] Location.isMock() → always false (Android 12+)")
        } catch (e: Exception) {
            // Not available on < Android 12 — that's expected, not an error
            Log.d(TAG, "[Hook2] [SKIP] isMock() not found (Android < 12) — expected")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HOOK 3: LocationManager.getProviders() → strip test providers
    // Uber calls getProviders(true) and inspects the list.
    // Our test provider "gps" (registered via addTestProvider) stays in
    // this list and triggers MOCK_PROVIDER_FOUND in Uber's detector.
    // We filter it out so Uber only sees ["gps", "network", "fused"].
    // ─────────────────────────────────────────────────────────────
    private fun installHookLocationProviders(classLoader: ClassLoader) {
        val cleanProviders = setOf("gps", "network", "passive", "fused")

        // Hook: getProviders(boolean enabledOnly)
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                classLoader,
                "getProviders",
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val providers = (param.result as? List<*>)?.filterIsInstance<String>() ?: return
                        val filtered = providers.filter { it in cleanProviders }
                        if (filtered.size != providers.size) {
                            Log.d(TAG, "[Hook3] Stripped test providers: ${providers - filtered.toSet()}")
                            param.result = filtered
                        }
                    }
                }
            )
            Log.i(TAG, "[Hook3] [SUCCESS] getProviders() — test providers stripped")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook3] [ERROR] getProviders hook failed: ${e.message}")
        }

        // Also hook: getAllProviders() — no-arg version
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                classLoader,
                "getAllProviders",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val providers = (param.result as? List<*>)?.filterIsInstance<String>() ?: return
                        val filtered = providers.filter { it in cleanProviders }
                        param.result = filtered
                    }
                }
            )
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // HOOK 4: Settings.Secure.getInt("mock_location") → return 0
    // Uber reads ALLOW_MOCK_LOCATION setting to detect if developer
    // options "enable mock locations" is turned on. We force it to
    // return 0 (disabled) so Uber thinks mock mode is OFF.
    // Found as: MOCK_GPS_SETTING_TURNED_ON in Uber's detection enum.
    // ─────────────────────────────────────────────────────────────
    private fun installHookMockLocationSetting(classLoader: ClassLoader) {
        // Hook Settings.Secure.getInt(ContentResolver, String, int)
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure",
                classLoader,
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        if (key == "mock_location" || key == "allow_mock_location") {
                            Log.d(TAG, "[Hook4] Intercepted Settings.Secure.$key → returning 0")
                            param.result = 0
                        }
                    }
                }
            )
            Log.i(TAG, "[Hook4] [SUCCESS] Settings.Secure mock_location → 0")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook4] [ERROR] Settings.Secure hook failed: ${e.message}")
        }

        // Also hook: Settings.Secure.getInt(ContentResolver, String) — 2-arg version
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure",
                classLoader,
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        if (key == "mock_location" || key == "allow_mock_location") {
                            param.result = 0
                        }
                    }
                }
            )
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // HOOK 5: Location.getLatitude() / getLongitude() → our fake coords
    // Reads coordinates from Relocate's SharedPreferences (written by
    // SpoofService) and injects them into every Location object that
    // Uber reads. This ensures Uber's map shows our spoofed position.
    //
    // Coordinates are stored as Long (Double.toBits) for full precision.
    // Spoofing is only active when KEY_ACTIVE=true in prefs.
    // ─────────────────────────────────────────────────────────────
    private fun installHookCoordinates(classLoader: ClassLoader) {
        // Hook: Location.getLatitude()
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getLatitude",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fakeLat = getFakeLatitude()
                        if (fakeLat != null) param.result = fakeLat
                    }
                }
            )
            Log.i(TAG, "[Hook5a] [SUCCESS] Location.getLatitude() → fake coords")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook5a] [ERROR] getLatitude hook failed: ${e.message}")
        }

        // Hook: Location.getLongitude()
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getLongitude",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fakeLng = getFakeLongitude()
                        if (fakeLng != null) param.result = fakeLng
                    }
                }
            )
            Log.i(TAG, "[Hook5b] [SUCCESS] Location.getLongitude() → fake coords")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook5b] [ERROR] getLongitude hook failed: ${e.message}")
        }

        // Hook: Location.getAccuracy() → return realistic 5.0f (±5 meters)
        // A perfect GPS signal. Too high means suspiciously good, 5.0 is normal.
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                classLoader,
                "getAccuracy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofActive()) param.result = 5.0f
                    }
                }
            )
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // Shared Preferences helpers — read cross-process from Relocate
    // ─────────────────────────────────────────────────────────────

    private fun isSpoofActive(): Boolean {
        prefs?.reload()
        return prefs?.getBoolean(KEY_ACTIVE, false) ?: false
    }

    private fun getFakeLatitude(): Double? {
        prefs?.reload()
        if (prefs?.getBoolean(KEY_ACTIVE, false) != true) return null
        val bits = prefs?.getLong(KEY_LAT, Long.MIN_VALUE)
        if (bits == null || bits == Long.MIN_VALUE) return null
        val lat = Double.fromBits(bits)
        return if (lat != 0.0) lat else null
    }

    private fun getFakeLongitude(): Double? {
        prefs?.reload()
        if (prefs?.getBoolean(KEY_ACTIVE, false) != true) return null
        val bits = prefs?.getLong(KEY_LNG, Long.MIN_VALUE)
        if (bits == null || bits == Long.MIN_VALUE) return null
        val lng = Double.fromBits(bits)
        return if (lng != 0.0) lng else null
    }
}
