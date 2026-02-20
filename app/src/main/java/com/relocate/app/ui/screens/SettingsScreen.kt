// [Relocate] [SettingsScreen.kt] - Settings & Route Simulation
// Features: Display toggles, preset manager, route simulation with OSRM,
//           speed/mode/direction controls, and update checker.

package com.relocate.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.relocate.app.network.NominatimApi
import com.relocate.app.network.OsrmApi
import com.relocate.app.network.LatLng
import com.relocate.app.network.SearchResult
import com.relocate.app.spoofing.SpoofService
import com.relocate.app.ui.components.OsmMapView
import com.relocate.app.ui.components.SearchBar
import com.relocate.app.ui.theme.*
import com.relocate.app.updater.UpdateService
import com.relocate.app.updater.UpdateInfo
import com.relocate.app.updater.UpdateService.DownloadStatus
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    presetStore: PresetStore,
    recentStore: RecentStore,
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Display Toggles ──
    val showCoords by prefsManager.showCoords.collectAsState(initial = true)
    val showPresets by prefsManager.showPresets.collectAsState(initial = true)
    val showRecent by prefsManager.showRecent.collectAsState(initial = true)
    val spoofMode by prefsManager.spoofMode.collectAsState(initial = SpoofMode.MOCK)

    // ── Presets ──
    val presets by presetStore.presets.collectAsState()
    var newPresetName by remember { mutableStateOf("") }
    var newPresetLat by remember { mutableStateOf("") }
    var newPresetLng by remember { mutableStateOf("") }

    // ── Route Simulation State ──
    var routeWaypoints by remember { mutableStateOf(listOf(
        WaypointState("A", ""),
        WaypointState("B", "")
    )) }
    var routeMode by remember { mutableStateOf("driving") }
    var routeDirection by remember { mutableStateOf("forward") }
    var routeSpeedKmh by remember { mutableFloatStateOf(50f) }
    var routeStatus by remember { mutableStateOf("Add at least 2 waypoints to begin") }
    var routeProgress by remember { mutableFloatStateOf(0f) }
    var isRouteRunning by remember { mutableStateOf(false) }
    var isRoutePaused by remember { mutableStateOf(false) }
    var routeJob by remember { mutableStateOf<Job?>(null) }

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

            // ════════════════════════════════════════
            // SECTION: ROUTE SIMULATION
            // ════════════════════════════════════════
            SectionHeader("🛣️ Route Simulation")

            // Waypoints
            routeWaypoints.forEachIndexed { idx, wp ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (idx) {
                            0 -> Green
                            routeWaypoints.size - 1 -> Red
                            else -> Blue
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                ('A' + idx).toString(),
                                color = MaterialTheme.colorScheme.surface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Search field for this waypoint
                    var wpQuery by remember { mutableStateOf(wp.name) }
                    OutlinedTextField(
                        value = wpQuery,
                        onValueChange = { wpQuery = it },
                        placeholder = { Text("🔍 Search...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Search button for this waypoint
                    IconButton(
                        onClick = {
                            scope.launch {
                                val results = NominatimApi.search(wpQuery, 1)
                                if (results.isNotEmpty()) {
                                    val result = results[0]
                                    val updated = routeWaypoints.toMutableList()
                                    updated[idx] = WaypointState(
                                        label = ('A' + idx).toString(),
                                        name = result.name,
                                        lat = result.lat,
                                        lng = result.lng
                                    )
                                    routeWaypoints = updated
                                    wpQuery = result.name
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Search, "Search", modifier = Modifier.size(16.dp))
                    }

                    // Remove button (only if more than 2 waypoints)
                    if (routeWaypoints.size > 2) {
                        IconButton(
                            onClick = {
                                routeWaypoints = routeWaypoints.toMutableList().apply { removeAt(idx) }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp), tint = Red)
                        }
                    }
                }
            }

            // Add Waypoint button
            OutlinedButton(
                onClick = {
                    val newLabel = ('A' + routeWaypoints.size).toString()
                    routeWaypoints = routeWaypoints + WaypointState(newLabel, "")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("➕ Add Waypoint")
            }

            // Mode selector
            Text("Travel Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("driving" to "🚗 Driving", "walking" to "🚶 Walking", "custom" to "⚙️ Custom").forEach { (mode, label) ->
                    FilterChip(
                        selected = routeMode == mode,
                        onClick = {
                            routeMode = mode
                            when (mode) {
                                "driving" -> routeSpeedKmh = 50f
                                "walking" -> routeSpeedKmh = 5f
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Direction selector
            Text("Direction", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("forward" to "➡️ Forward", "backward" to "⬅️ Backward", "loop" to "🔄 Loop").forEach { (dir, label) ->
                    FilterChip(
                        selected = routeDirection == dir,
                        onClick = { routeDirection = dir },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Speed slider
            Text(
                "Speed: ${routeSpeedKmh.toInt()} km/h",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Slider(
                value = routeSpeedKmh,
                onValueChange = {
                    routeSpeedKmh = it
                    if (routeMode != "custom") routeMode = "custom"
                },
                valueRange = 1f..200f,
                colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber)
            )

            // Progress bar
            Column {
                LinearProgressIndicator(
                    progress = routeProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = Amber,
                    trackColor = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    routeStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start / Resume
                Button(
                    onClick = {
                        val validWps = routeWaypoints.filter { it.lat != null && it.lng != null }
                        if (validWps.size < 2) {
                            Toast.makeText(context, "Need at least 2 waypoints", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        scope.launch {
                            routeStatus = "Fetching route..."
                            isRouteRunning = true

                            var ordered = validWps.map { LatLng(it.lat!!, it.lng!!) }
                            if (routeDirection == "backward") ordered = ordered.reversed()

                            val routePoints = OsrmApi.getRoute(ordered, routeMode) ?: run {
                                routeStatus = "❌ Could not find route"
                                isRouteRunning = false
                                return@launch
                            }

                            if (routePoints.size < 2) {
                                routeStatus = "❌ Route too short"
                                isRouteRunning = false
                                return@launch
                            }

                            routeStatus = "Simulating..."
                            var routeIdx = 0
                            var goingForward = true
                            val metersPerSecond = (routeSpeedKmh * 1000) / 3600

                            routeJob = scope.launch {
                                while (isActive) {
                                    delay(1000)

                                    if (routeDirection == "loop") {
                                        if (goingForward && routeIdx >= routePoints.size - 1) goingForward = false
                                        if (!goingForward && routeIdx <= 0) goingForward = true
                                    } else if (routeIdx >= routePoints.size - 1) {
                                        routeStatus = "✅ Route complete!"
                                        routeProgress = 1f
                                        isRouteRunning = false
                                        break
                                    }

                                    var distToTravel = metersPerSecond
                                    val step = if (goingForward) 1 else -1

                                    while (distToTravel > 0) {
                                        val nextIdx = routeIdx + step
                                        if (nextIdx < 0 || nextIdx >= routePoints.size) break

                                        val curr = routePoints[routeIdx]
                                        val next = routePoints[nextIdx]
                                        val segDist = haversineMeters(curr.lat, curr.lng, next.lat, next.lng)

                                        if (segDist <= distToTravel) {
                                            distToTravel -= segDist.toFloat()
                                            routeIdx = nextIdx
                                        } else {
                                            distToTravel = 0f
                                        }
                                    }

                                    val pos = routePoints[routeIdx]
                                    routeProgress = routeIdx.toFloat() / (routePoints.size - 1).toFloat()

                                    // Update spoofed position
                                    prefsManager.setSpoofEnabled(true)
                                    prefsManager.setLocation(pos.lat, pos.lng, 10f, "🛣️ Route Simulation")
                                    SpoofService.updateSpoof(context, pos.lat, pos.lng, 10f)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isRouteRunning || isRoutePaused,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Text(if (isRoutePaused) "▶️ Resume" else "▶️ Start")
                }

                // Pause
                Button(
                    onClick = {
                        routeJob?.cancel()
                        isRoutePaused = true
                        routeStatus = "Paused"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isRouteRunning && !isRoutePaused,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber)
                ) {
                    Text("⏸️ Pause")
                }

                // Stop
                Button(
                    onClick = {
                        routeJob?.cancel()
                        isRouteRunning = false
                        isRoutePaused = false
                        routeProgress = 0f
                        routeStatus = "Stopped"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isRouteRunning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) {
                    Text("⏹️ Stop")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ════════════════════════════════════════
            // LOGS SECTION
            // ════════════════════════════════════════
            SectionHeader(text = "📋  Logs")
            Button(
                onClick = onNavigateToLogs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("📋 View App Logs")
            }
            Text(
                text = "View internal logs for debugging. Copy & share with developer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

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

// ── Helper Data Class ──
private data class WaypointState(
    val label: String,
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null
)

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

// ── Haversine Distance ──
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
