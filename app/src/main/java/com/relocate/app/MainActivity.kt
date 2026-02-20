// [Relocate] [MainActivity.kt] - Single Activity Host
// Hosts Jetpack Compose navigation between Main and Settings screens.
// v1.8.2: Added runtime permission requests + hook log file preparation.

package com.relocate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.relocate.app.ui.screens.LogScreen
import com.relocate.app.data.PreferencesManager
import com.relocate.app.data.PresetStore
import com.relocate.app.data.RecentStore
import com.relocate.app.logging.AppLogger
import com.relocate.app.ui.screens.AppFixerScreen
import com.relocate.app.ui.screens.MainScreen
import com.relocate.app.ui.screens.SettingsScreen
import com.relocate.app.ui.theme.RelocateTheme
import com.relocate.app.util.RootUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var presetStore: PresetStore
    private lateinit var recentStore: RecentStore

    // ── Permission Launchers ─────────────────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val bgGranted = permissions[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
        AppLogger.i("Permissions", "Location: fine=$fineGranted, background=$bgGranted")
        if (fineGranted) {
            // Request background location separately on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !bgGranted) {
                requestBackgroundLocation()
            }
        } else {
            Toast.makeText(this, "⚠️ Location permission required for GPS spoofing", Toast.LENGTH_LONG).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLogger.i("Permissions", "Background location: $granted")
        if (!granted) {
            Toast.makeText(this, "⚠️ Background location needed for continuous spoofing", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppLogger.i("Permissions", "Notifications: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsManager = PreferencesManager(applicationContext)
        presetStore = PresetStore(applicationContext)
        recentStore = RecentStore(applicationContext)

        AppLogger.i("App", "Relocate v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) started")

        // ── Request all needed permissions ────────────────────────────────
        requestAppPermissions()

        // ── Prepare hook log file (needs root) ───────────────────────────
        CoroutineScope(Dispatchers.IO).launch {
            val prepared = RootUtils.prepareHookLogFile()
            if (prepared) {
                AppLogger.i("App", "✅ Hook log file ready — hooks can now write logs")
            } else {
                AppLogger.w("App", "⚠️ Hook log file NOT prepared — check root access")
            }
        }

        // ── Request All Files Access on Android 11+ ──────────────────────
        requestAllFilesAccess()

        setContent {
            val isDarkTheme by prefsManager.isDarkTheme.collectAsState(initial = true)

            RelocateTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main"
                    ) {
                        composable("main") {
                            MainScreen(
                                prefsManager = prefsManager,
                                presetStore = presetStore,
                                recentStore = recentStore,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToFeatures = {
                                    navController.navigate("features")
                                },
                                onNavigateToFixer = {
                                    navController.navigate("fixer")
                                }
                            )
                        }

                        composable("features") {
                            FeaturesScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToFixer = { navController.navigate("fixer") },
                                onNavigateToLogs = { navController.navigate("logs") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                prefsManager = prefsManager,
                                presetStore = presetStore,
                                recentStore = recentStore,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("fixer") {
                            AppFixerScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("logs") {
                            LogScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Permission Request Logic ──────────────────────────────────────────────

    private fun requestAppPermissions() {
        // 1. Location permissions
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Fine location already granted — check background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestBackgroundLocation()
                }
            }
        }

        // 2. Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        AppLogger.i("Permissions", "Permission check completed")
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                AppLogger.i("Permissions", "Requesting All Files Access (MANAGE_EXTERNAL_STORAGE)")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: open general settings
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (_: Exception) {
                        AppLogger.w("Permissions", "Cannot open All Files Access settings")
                    }
                }
            }
        }
    }
}
