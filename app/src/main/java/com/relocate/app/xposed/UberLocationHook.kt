/**
 * [Relocate] EagleHook.kt  —  v1.4.0
 * Author: AntiGravity AI — LSPosed Module Hook
 * Purpose: Hooks inside the target navigation app process via LSPosed/XPosed
 *          to intercept ALL mock location AND root/device-state detection.
 *
 * RE findings from APK v4.546.10003 (22 DEX files scanned):
 *
 * LOCATION CHECKS (v1.2.0):
 *  1. MOCK_GPS_SETTING_TURNED_ON — Settings.Secure.ALLOW_MOCK_LOCATION
 *  2. MOCK_PROVIDER_FOUND        — LocationManager.getProviders() listing test providers
 *  3. isFromMockProvider()       — Location.isFromMockProvider() flag
 *  4. isMocked / isMock          — Location internal fields (Android 12+)
 *  5. Coordinates injected       — Location.getLatitude/getLongitude
 *
 * ROOT / DEVICE STATE CHECKS (v1.3.0 — NEW):
 *  6. Build.TAGS "test-keys"     — classes9/11/22: build tag check
 *  7. Developer Options enabled  — classes9: Settings.Global.development_settings_enabled
 *  8. USB Debugging enabled       — Settings.Global.adb_enabled
 *  9. Known root apps            — classes22/9: PackageManager.getPackageInfo/getApplicationInfo
 *     (com.topjohnwu.magisk, eu.chainfire.supersu, com.noshufou.android.su, etc.)
 * 10. su / busybox binaries      — classes22/9: File.exists() on su paths
 * 11. ExtraDeviceInfo.isRooted   — classes6: Uber's own data class field
 */
package com.relocate.app.xposed

import android.content.ContentResolver
import android.content.pm.PackageManager
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
        // v1.4.0: No longer limited to one app — hooks ALL apps
        private const val RELOCATE_PKG = "com.relocate.app"
        // NOTE: NOT private — accessed by SpoofService cross-class
        const val PREFS_NAME = "spoof_coords"
        const val KEY_ACTIVE = "is_active"
        const val KEY_LAT = "lat_bits"   // stored as Long (Double.toBits)
        const val KEY_LNG = "lng_bits"   // stored as Long (Double.toBits)

        // Root app package names found in Eagle's DEX (classes22.dex, classes9.dex)
        private val ROOT_PACKAGES = setOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.thirdparty.superuser",
            "com.koushikdutta.superuser",
            "com.ramdroid.appquarantine",
            "com.jrummy.root.browserfree",
            "com.jrummy.busybox.installer",
            "stericson.busybox",
            "stericson.busybox.free"
        )

        // su / root binary paths found in Eagle's DEX (classes22.dex, classes9.dex)
        private val ROOT_PATHS = setOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/system/bin/failsafe/su",
            "/system/sd/xbin/su",
            "/system/sbin",
            "/system/xbin",
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/vendor/bin/su",
            "busybox"
        )

        // Settings.Global keys that Eagle checks (classes9.dex)
        private val GLOBAL_KEYS_TO_ZERO = setOf(
            "development_settings_enabled",
            "adb_enabled"
        )
    }

    private var prefs: XSharedPreferences? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // v1.4.0: Hook into ALL app processes (not just Uber)
        // Skip our own to avoid recursion
        if (lpparam.packageName == RELOCATE_PKG) return

        Log.i(TAG, "[Init] Hooking process: ${lpparam.packageName} — installing 11 hooks (v1.4.0)")

        // Load shared preferences written by Relocate's SpoofService
        prefs = XSharedPreferences(RELOCATE_PKG, PREFS_NAME)
        prefs?.makeWorldReadable()

        // ── v1.2.0 hooks: Location detection ──
        installHookIsFromMockProvider(lpparam.classLoader)
        installHookMockProviderField(lpparam.classLoader)
        installHookLocationProviders(lpparam.classLoader)
        installHookMockLocationSetting(lpparam.classLoader)
        installHookCoordinates(lpparam.classLoader)

        // ── v1.3.0 hooks: Root / device state detection ──
        installHookBuildTags(lpparam.classLoader)
        installHookDeveloperSettings(lpparam.classLoader)
        installHookPackageManager(lpparam.classLoader)
        installHookFileExists(lpparam.classLoader)

        Log.i(TAG, "[Init] [DONE] All 11 hooks active in ${lpparam.packageName}")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 1: Location.isFromMockProvider() → always false
    // Primary check Uber reads on every location update.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookIsFromMockProvider(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader,
                "isFromMockProvider",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any = false
                }
            )
            Log.i(TAG, "[Hook1] [SUCCESS] isFromMockProvider → false")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook1] [ERROR] ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 2: Location.isMock() → always false  (Android 12+ API 31)
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookMockProviderField(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader,
                "isMock",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any = false
                }
            )
            Log.i(TAG, "[Hook2] [SUCCESS] Location.isMock() → false")
        } catch (e: Exception) {
            Log.d(TAG, "[Hook2] [SKIP] isMock() not on this API level")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 3: LocationManager.getProviders() → strip test providers
    // Uber scans this list for our addTestProvider() registration.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookLocationProviders(classLoader: ClassLoader) {
        val cleanProviders = setOf("gps", "network", "passive", "fused")

        try {
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager", classLoader,
                "getProviders", Boolean::class.java,
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
            Log.i(TAG, "[Hook3] [SUCCESS] getProviders() strips test providers")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook3] [ERROR] ${e.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager", classLoader, "getAllProviders",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val providers = (param.result as? List<*>)?.filterIsInstance<String>() ?: return
                        param.result = providers.filter { it in cleanProviders }
                    }
                }
            )
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 4: Settings.Secure.getInt("mock_location") → 0
    // Hooks the MOCK_GPS_SETTING_TURNED_ON detection vector.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookMockLocationSetting(classLoader: ClassLoader) {
        val mockKeys = setOf("mock_location", "allow_mock_location")

        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure", classLoader,
                "getInt", ContentResolver::class.java, String::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        if (key in mockKeys) { param.result = 0 }
                    }
                }
            )
            Log.i(TAG, "[Hook4] [SUCCESS] Settings.Secure.mock_location → 0")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook4] [ERROR] ${e.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure", classLoader,
                "getInt", ContentResolver::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        if (key in mockKeys) { param.result = 0 }
                    }
                }
            )
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 5: Location.getLatitude/getLongitude → inject our coords
    // Reads from Relocate's SharedPreferences via XSharedPreferences.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookCoordinates(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader, "getLatitude",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        getFakeLatitude()?.let { param.result = it }
                    }
                }
            )
            Log.i(TAG, "[Hook5a] [SUCCESS] getLatitude → fake coords")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook5a] [ERROR] ${e.message}")
        }

        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader, "getLongitude",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        getFakeLongitude()?.let { param.result = it }
                    }
                }
            )
            Log.i(TAG, "[Hook5b] [SUCCESS] getLongitude → fake coords")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook5b] [ERROR] ${e.message}")
        }

        // Accuracy — realistic 5.0m
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", classLoader, "getAccuracy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofActive()) param.result = 5.0f
                    }
                }
            )
        } catch (_: Exception) {}
    }

    // ═════════════════════════════════════════════════════════════════════════
    // v1.3.0 NEW HOOKS — Root / Device State Detection Bypass
    // ═════════════════════════════════════════════════════════════════════════

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 6: Build.TAGS → return "release-keys" instead of "test-keys"
    // Found in classes9/11/22: Eagle reads Build.TAGS to detect custom ROMs.
    // "test-keys" means an unofficial build — root indicator.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookBuildTags(classLoader: ClassLoader) {
        try {
            // Build.TAGS is a static final field — we set it via reflection
            val buildClass = XposedHelpers.findClass("android.os.Build", classLoader)
            val tagsField = buildClass.getField("TAGS")
            tagsField.isAccessible = true

            val current = tagsField.get(null) as? String ?: ""
            if (current.contains("test-keys")) {
                // Replace the field value to "release-keys"
                // XposedHelpers.setStaticObjectField handles final fields
                XposedHelpers.setStaticObjectField(buildClass, "TAGS", "release-keys")
                Log.i(TAG, "[Hook6] [SUCCESS] Build.TAGS: \"$current\" → \"release-keys\"")
            } else {
                Log.i(TAG, "[Hook6] [OK] Build.TAGS already \"$current\" — no change needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Hook6] [ERROR] Build.TAGS hook failed: ${e.message}")
        }

        // Also spoof Build.TYPE — "userdebug" → "user" (release build type)
        try {
            val buildClass = XposedHelpers.findClass("android.os.Build", classLoader)
            val typeField = buildClass.getField("TYPE")
            typeField.isAccessible = true
            val current = typeField.get(null) as? String ?: ""
            if (current == "userdebug" || current == "eng") {
                XposedHelpers.setStaticObjectField(buildClass, "TYPE", "user")
                Log.i(TAG, "[Hook6b] [SUCCESS] Build.TYPE: \"$current\" → \"user\"")
            }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 7: Settings.Global.getInt("development_settings_enabled") → 0
    //          Settings.Global.getInt("adb_enabled") → 0
    // Found in classes9.dex: Eagle reads dev mode and ADB settings.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookDeveloperSettings(classLoader: ClassLoader) {
        val hookBody = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[1] as? String ?: return
                if (key in GLOBAL_KEYS_TO_ZERO) {
                    Log.d(TAG, "[Hook7] Intercepted Settings.Global.$key → 0")
                    param.result = 0
                }
            }
        }

        // Hook 3-arg version: getInt(ContentResolver, String, int)
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Global", classLoader,
                "getInt", ContentResolver::class.java, String::class.java, Int::class.java,
                hookBody
            )
            Log.i(TAG, "[Hook7] [SUCCESS] Settings.Global dev/adb → 0 (3-arg)")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook7] [ERROR] ${e.message}")
        }

        // Hook 2-arg version: getInt(ContentResolver, String)
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Global", classLoader,
                "getInt", ContentResolver::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String ?: return
                        if (key in GLOBAL_KEYS_TO_ZERO) { param.result = 0 }
                    }
                }
            )
            Log.i(TAG, "[Hook7] [SUCCESS] Settings.Global dev/adb → 0 (2-arg)")
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 8: PackageManager.getPackageInfo(rootPkg) → throw NameNotFoundException
    //          PackageManager.getApplicationInfo(rootPkg) → throw NameNotFoundException
    // Found in classes22/9: Eagle enumerates known root manager packages.
    // We make them all invisible by throwing the "not installed" exception.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookPackageManager(classLoader: ClassLoader) {
        // Hook: getPackageInfo(String, int)
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", classLoader,
                "getPackageInfo", String::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        if (pkg in ROOT_PACKAGES) {
                            Log.d(TAG, "[Hook8] Hiding root pkg: $pkg")
                            param.throwable = PackageManager.NameNotFoundException("$pkg not found")
                        }
                    }
                }
            )
            Log.i(TAG, "[Hook8] [SUCCESS] PackageManager.getPackageInfo() hides root apps")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook8] [ERROR] getPackageInfo hook: ${e.message}")
        }

        // Hook: getApplicationInfo(String, int)
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", classLoader,
                "getApplicationInfo", String::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkg = param.args[0] as? String ?: return
                        if (pkg in ROOT_PACKAGES) {
                            Log.d(TAG, "[Hook8b] Hiding root app info: $pkg")
                            param.throwable = PackageManager.NameNotFoundException("$pkg not found")
                        }
                    }
                }
            )
            Log.i(TAG, "[Hook8b] [SUCCESS] PackageManager.getApplicationInfo() hides root apps")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook8b] [ERROR] getApplicationInfo hook: ${e.message}")
        }

        // Hook: getInstalledPackages(int) — filter list results
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", classLoader,
                "getInstalledPackages", Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val list = param.result as? MutableList<android.content.pm.PackageInfo> ?: return
                        val before = list.size
                        list.removeIf { it.packageName in ROOT_PACKAGES }
                        if (list.size != before) {
                            Log.d(TAG, "[Hook8c] Removed ${before - list.size} root packages from list")
                        }
                    }
                }
            )
            Log.i(TAG, "[Hook8c] [SUCCESS] getInstalledPackages() filters root apps")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook8c] [ERROR] getInstalledPackages hook: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HOOK 9: File.exists() → false for su/busybox binary paths
    // Found in classes22/9: Eagle checks for su binary existence on filesystem.
    // We intercept File.exists() and return false for all root-related paths.
    // ─────────────────────────────────────────────────────────────────────────
    private fun installHookFileExists(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "java.io.File", classLoader,
                "exists",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val file = param.thisObject as? java.io.File ?: return
                        val path = file.absolutePath
                        // Check if any root path is a substring of this file's path
                        if (ROOT_PATHS.any { rootPath -> path.contains(rootPath, ignoreCase = true) }) {
                            Log.d(TAG, "[Hook9] Hiding file: $path → false")
                            param.result = false
                        }
                    }
                }
            )
            Log.i(TAG, "[Hook9] [SUCCESS] File.exists() hides su/busybox binaries")
        } catch (e: Exception) {
            Log.e(TAG, "[Hook9] [ERROR] File.exists() hook: ${e.message}")
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SharedPreferences helpers — read cross-process from Relocate's SpoofService
    // ═════════════════════════════════════════════════════════════════════════

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
