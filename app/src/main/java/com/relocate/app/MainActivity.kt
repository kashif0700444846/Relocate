// [Relocate] [MainActivity.kt] - Single Activity Host
// Hosts Jetpack Compose navigation between Main and Settings screens.

package com.relocate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.relocate.app.data.PreferencesManager
import com.relocate.app.data.PresetStore
import com.relocate.app.data.RecentStore
import com.relocate.app.ui.screens.AppFixerScreen
import com.relocate.app.ui.screens.MainScreen
import com.relocate.app.ui.screens.SettingsScreen
import com.relocate.app.ui.theme.RelocateTheme

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var presetStore: PresetStore
    private lateinit var recentStore: RecentStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefsManager = PreferencesManager(applicationContext)
        presetStore = PresetStore(applicationContext)
        recentStore = RecentStore(applicationContext)

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
                                onNavigateToFixer = {
                                    navController.navigate("fixer")
                                }
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
                    }
                }
            }
        }
    }
}
