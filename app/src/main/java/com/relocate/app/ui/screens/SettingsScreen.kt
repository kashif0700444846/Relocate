// [Relocate] [SettingsScreen.kt] - App Settings & Configuration
// Features: Display toggles, preset manager, and update checker.
// Route Simulation and Logs have moved to FeaturesScreen.

package com.relocate.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.data.*
import com.relocate.app.ui.components.SearchBar
import com.relocate.app.ui.theme.*
import com.relocate.app.updater.UpdateService
import com.relocate.app.updater.UpdateInfo
import com.relocate.app.updater.UpdateService.DownloadStatus
import kotlinx.coroutines.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    presetStore: PresetStore,
    recentStore: RecentStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Display Toggles ──
    val showCoords by prefsManager.showCoords.collectAsState(initial = true)
    val showPresets by prefsManager.showPresets.collectAsState(initial = true)
    val showRecent by prefsManager.showRecent.collectAsState(initial = true)

    // ── Presets ──
    val presets by presetStore.presets.collectAsState()
    var newPresetName by remember { mutableStateOf("") }
    var newPresetLat by remember { mutableStateOf("") }
    var newPresetLng by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙️", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Settings", fontWeight = FontWeight.Bold)
                            Text(
                                "Relocate — Location Changer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ════════════════════════════════════════
            // SECTION: DISPLAY PREFERENCES
            // ════════════════════════════════════════
            SectionHeader("Display Preferences")

            SettingsToggle("Show Coordinates", "Lat/Lng inputs and accuracy slider", showCoords) {
                scope.launch { prefsManager.setShowCoords(it) }
            }
            SettingsToggle("Show Presets", "Quick preset location buttons", showPresets) {
                scope.launch { prefsManager.setShowPresets(it) }
            }
            SettingsToggle("Show Recent", "Recently used locations list", showRecent) {
                scope.launch { prefsManager.setShowRecent(it) }
            }

            // Route Simulation toggle
            val showRouteSim by prefsManager.showRouteSim.collectAsState(initial = false)
            SettingsToggle("Show Route Simulation", "Route simulation controls on main screen", showRouteSim) {
                scope.launch { prefsManager.setShowRouteSim(it) }
            }

            Divider()

            // ════════════════════════════════════════
            // SECTION: PRESETS MANAGER
            // ════════════════════════════════════════
            SectionHeader("Presets Manager")

            // Existing presets table
            if (presets.isEmpty()) {
                Text(
                    "No presets yet. Add your first one below!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                presets.forEachIndexed { idx, preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📌", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "%.4f, %.4f".format(preset.lat, preset.lng),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { presetStore.delete(idx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("🗑️", fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Add Preset Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Add New Preset", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Search to fill fields
                    SearchBar(
                        onLocationSelected = { result ->
                            newPresetName = result.name
                            newPresetLat = String.format(Locale.US, "%.6f", result.lat)
                            newPresetLng = String.format(Locale.US, "%.6f", result.lng)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newPresetLat,
                            onValueChange = { newPresetLat = it },
                            label = { Text("Lat") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = newPresetLng,
                            onValueChange = { newPresetLng = it },
                            label = { Text("Lng") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val lat = newPresetLat.replace(",", ".").trim().toDoubleOrNull()
                            val lng = newPresetLng.replace(",", ".").trim().toDoubleOrNull()
                            if (newPresetName.isBlank()) {
                                Toast.makeText(context, "Enter a name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (lat == null || lat < -90 || lat > 90) {
                                Toast.makeText(context, "Invalid latitude", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (lng == null || lng < -180 || lng > 180) {
                                Toast.makeText(context, "Invalid longitude", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            presetStore.add(Preset(newPresetName, lat, lng))
                            newPresetName = ""
                            newPresetLat = ""
                            newPresetLng = ""
                            Toast.makeText(context, "✅ Preset added!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber)
                    ) {
                        Text("➕ Add Preset", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Divider()

            Spacer(modifier = Modifier.height(8.dp))

            // ════════════════════════════════════════
            // APP UPDATE SECTION
            // ════════════════════════════════════════
            SectionHeader(text = "📦  App Update")

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var downloadStatus by remember { mutableStateOf<DownloadStatus?>(null) }
            var isDownloading by remember { mutableStateOf(false) }

            // Current version display
            val currentVersion = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                } catch (_: Exception) { "?" }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Version badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Current Version:", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "v$currentVersion",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Check button
                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            updateInfo = null
                            downloadStatus = null
                            scope.launch {
                                val result = UpdateService.checkForUpdate(context)
                                updateInfo = result
                                isCheckingUpdate = false
                            }
                        },
                        enabled = !isCheckingUpdate && !isDownloading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Text("🔄 Check for Updates")
                        }
                    }

                    // Update result
                    val info = updateInfo
                    if (info != null) {
                        Spacer(Modifier.height(12.dp))

                        if (info.isUpdateAvailable) {
                            // New version available
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "🎉 New version available!",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        "v${info.latestVersion}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    if (!info.releaseName.isNullOrBlank()) {
                                        Text(
                                            info.releaseName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!info.releaseNotes.isNullOrBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            info.releaseNotes.take(300),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 6
                                        )
                                    }
                                    if (info.apkSize > 0) {
                                        Text(
                                            "Size: ${"%.1f".format(info.apkSize / 1_048_576f)} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Download status
                                    val status = downloadStatus
                                    when {
                                        status is DownloadStatus.Downloading -> {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            Spacer(Modifier.height(4.dp))
                                            Text("Downloading...", style = MaterialTheme.typography.labelSmall)
                                        }
                                        status is DownloadStatus.Installing -> {
                                            Text("📲 Opening installer...", fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2196F3))
                                        }
                                        status is DownloadStatus.Done -> {
                                            Text("✅ Install started!", fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50))
                                        }
                                        status is DownloadStatus.Failed -> {
                                            Text("❌ ${status.error}", color = Color(0xFFF44336),
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        else -> {
                                            // Download button
                                            if (info.apkUrl != null) {
                                                Button(
                                                    onClick = {
                                                        isDownloading = true
                                                        scope.launch {
                                                            UpdateService.downloadAndInstall(
                                                                context = context,
                                                                apkUrl = info.apkUrl,
                                                                version = info.latestVersion,
                                                                onProgress = { s ->
                                                                    downloadStatus = s
                                                                    if (s is DownloadStatus.Done ||
                                                                        s is DownloadStatus.Failed) {
                                                                        isDownloading = false
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF4CAF50)
                                                    )
                                                ) {
                                                    Text("⬇️ Download & Install v${info.latestVersion}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Already up to date
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✅", fontSize = 20.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("You're up to date!", fontWeight = FontWeight.Bold)
                                        Text(
                                            "v$currentVersion is the latest version",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


// ── UI Components ──
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Amber,
                checkedTrackColor = AmberDim
            )
        )
    }
}
