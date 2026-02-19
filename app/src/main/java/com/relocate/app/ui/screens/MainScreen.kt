// [Relocate] [MainScreen.kt] - Primary App Screen
// Features: Map, coordinates, accuracy, mode selector, search, presets,
//           recent locations, apply/reset, theme toggle — mirrors Chrome extension popup.

package com.relocate.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.relocate.app.data.*
import com.relocate.app.network.LatLng
import com.relocate.app.network.NominatimApi
import com.relocate.app.network.OsrmApi
import com.relocate.app.spoofing.MockLocationEngine
import org.osmdroid.util.GeoPoint
import com.relocate.app.spoofing.RootSpoofEngine
import com.relocate.app.spoofing.SpoofService
import com.relocate.app.ui.components.*
import com.relocate.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefsManager: PreferencesManager,
    presetStore: PresetStore,
    recentStore: RecentStore,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State from DataStore ──
    val isSpoofEnabled by prefsManager.isSpoofEnabled.collectAsState(initial = false)
    val savedLat by prefsManager.latitude.collectAsState(initial = 0.0)
    val savedLng by prefsManager.longitude.collectAsState(initial = 0.0)
    val savedAccuracy by prefsManager.accuracy.collectAsState(initial = 10f)
    val savedMode by prefsManager.spoofMode.collectAsState(initial = SpoofMode.MOCK)
    val isDark by prefsManager.isDarkTheme.collectAsState(initial = true)
    val activePresetName by prefsManager.presetName.collectAsState(initial = "")
    val showCoords by prefsManager.showCoords.collectAsState(initial = true)
    val showPresets by prefsManager.showPresets.collectAsState(initial = true)
    val showRecent by prefsManager.showRecent.collectAsState(initial = true)

    // ── Local UI state ──
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var accuracy by remember { mutableFloatStateOf(10f) }
    var spoofMode by remember { mutableStateOf(SpoofMode.MOCK) }
    var selectedPresetIndex by remember { mutableStateOf<Int?>(null) }
    var hasLoadedGps by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(false) }

    // ── Route Simulation State ──
    var routeStartQuery by remember { mutableStateOf("") }
    var routeEndQuery by remember { mutableStateOf("") }
    var routeStartLatLng by remember { mutableStateOf<LatLng?>(null) }
    var routeEndLatLng by remember { mutableStateOf<LatLng?>(null) }
    var routeMode by remember { mutableStateOf("driving") }
    var routeSpeedKmh by remember { mutableFloatStateOf(50f) }
    var routeDirection by remember { mutableStateOf("forward") }
    var routeProgress by remember { mutableFloatStateOf(0f) }
    var routeStatus by remember { mutableStateOf("Ready") }
    var isRouteRunning by remember { mutableStateOf(false) }
    var isRoutePaused by remember { mutableStateOf(false) }
    var routeJob by remember { mutableStateOf<Job?>(null) }
    // Route data for MapView (polyline + arrow)
    var routeGeoPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var simulationBearing by remember { mutableFloatStateOf(0f) }

    // ── Location Permission Launcher ──
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions.values.any { it }
    }

    // Check location permission on launch
    LaunchedEffect(Unit) {
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── Get current GPS location when spoofing is OFF ──
    LaunchedEffect(locationPermissionGranted, isSpoofEnabled) {
        if (locationPermissionGranted && !isSpoofEnabled && !hasLoadedGps) {
            try {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? LocationManager
                if (locationManager != null) {
                    @Suppress("MissingPermission")
                    val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (lastKnown != null) {
                        latitude = lastKnown.latitude
                        longitude = lastKnown.longitude
                        hasLoadedGps = true
                    }
                }
            } catch (e: Exception) {
                // Silently fail — will use default coordinates
            }
        }
    }

    // Sync when DataStore values change (only if GPS hasn't been loaded or spoofing is active)
    LaunchedEffect(savedLat, savedLng, isSpoofEnabled) {
        if (isSpoofEnabled || (savedLat != 0.0 && savedLng != 0.0 && !hasLoadedGps)) {
            latitude = savedLat
            longitude = savedLng
        }
    }
    LaunchedEffect(savedAccuracy) { accuracy = savedAccuracy }
    LaunchedEffect(savedMode) { spoofMode = savedMode }

    // ── Root availability check ──
    var isRootAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isRootAvailable = RootSpoofEngine(context).isAvailable()
        }
    }

    // ── Presets & Recent ──
    val presets by presetStore.presets.collectAsState()
    val recentLocations by recentStore.locations.collectAsState()

    // ── Status colors ──
    val statusBg by animateColorAsState(
        if (isSpoofEnabled) Green.copy(alpha = 0.15f) else Red.copy(alpha = 0.1f),
        label = "statusBg"
    )
    val statusDotColor by animateColorAsState(
        if (isSpoofEnabled) Green else Red,
        label = "statusDot"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ════════════════════════════════════════
        // HEADER
        // ════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Relocate",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Location Changer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Theme toggle
                IconButton(onClick = {
                    scope.launch { prefsManager.setDarkTheme(!isDark) }
                }) {
                    Text(if (isDark) "🌙" else "☀️", fontSize = 20.sp)
                }

                // Master toggle
                Switch(
                    checked = isSpoofEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            try {
                                prefsManager.setSpoofEnabled(enabled)
                                if (enabled) {
                                    SpoofService.startSpoof(context, latitude, longitude, accuracy, spoofMode)
                                } else {
                                    SpoofService.stopSpoof(context)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Amber,
                        checkedTrackColor = AmberDim
                    )
                )
            }
        }

        // ════════════════════════════════════════
        // STATUS BADGE
        // ════════════════════════════════════════
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = statusBg)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isSpoofEnabled) {
                        "Spoofing ON — ${activePresetName.ifBlank { "Custom Location" }}"
                    } else {
                        "Spoofing OFF — Using real location"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ════════════════════════════════════════
        // SCROLLABLE CONTENT
        // ════════════════════════════════════════
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── MAP ──
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                OsmMapView(
                    modifier = Modifier.fillMaxSize(),
                    latitude = latitude,
                    longitude = longitude,
                    onMapClick = { lat, lng ->
                        latitude = lat
                        longitude = lng
                        selectedPresetIndex = null
                    },
                    onMarkerDrag = { lat, lng ->
                        latitude = lat
                        longitude = lng
                        selectedPresetIndex = null
                    },
                    routePath = routeGeoPoints.ifEmpty { null },
                    simulationBearing = simulationBearing,
                    isSimulating = isRouteRunning
                )
            }

            // ── COORDINATES ──
            if (showCoords) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = String.format(Locale.US, "%.6f", latitude),
                        onValueChange = { v ->
                            v.replace(",", ".").toDoubleOrNull()?.let { latitude = it }
                        },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = String.format(Locale.US, "%.6f", longitude),
                        onValueChange = { v ->
                            v.replace(",", ".").toDoubleOrNull()?.let { longitude = it }
                        },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Accuracy slider
                Column {
                    Text(
                        text = "Accuracy: ${accuracy.toInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = accuracy,
                        onValueChange = { accuracy = it },
                        valueRange = 1f..100f,
                        steps = 98,
                        colors = SliderDefaults.colors(
                            thumbColor = Amber,
                            activeTrackColor = Amber
                        )
                    )
                }
            }

            // ── SEARCH BAR ──
            SearchBar(
                onLocationSelected = { result ->
                    latitude = result.lat
                    longitude = result.lng
                    selectedPresetIndex = null
                }
            )

            // ── MODE SELECTOR ──
            ModeSelector(
                currentMode = spoofMode,
                onModeChange = { newMode ->
                    spoofMode = newMode
                    scope.launch { prefsManager.setSpoofMode(newMode) }
                },
                isRootAvailable = isRootAvailable,
                onRootCheckRequested = {
                    // User clicked Root but no SU — re-check and inform
                    scope.launch {
                        val rootAvailable = withContext(Dispatchers.IO) {
                            RootSpoofEngine(context).isAvailable()
                        }
                        isRootAvailable = rootAvailable
                        if (!rootAvailable) {
                            Toast.makeText(
                                context,
                                "❌ Root (SU) not available. Device must be rooted.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "✅ Root access granted!", Toast.LENGTH_SHORT).show()
                            spoofMode = SpoofMode.ROOT
                            prefsManager.setSpoofMode(SpoofMode.ROOT)
                        }
                    }
                }
            )

            // ── PRESETS ──
            if (showPresets) {
                PresetsList(
                    presets = presets,
                    selectedIndex = selectedPresetIndex,
                    onPresetClick = { idx, preset ->
                        latitude = preset.lat
                        longitude = preset.lng
                        selectedPresetIndex = idx
                        Toast.makeText(context, "📍 Moved to ${preset.name}", Toast.LENGTH_SHORT).show()
                    },
                    onAddMore = onNavigateToSettings
                )
            }

            // ── RECENT LOCATIONS ──
            if (showRecent) {
                RecentList(
                    locations = recentLocations,
                    onLocationClick = { loc ->
                        latitude = loc.lat
                        longitude = loc.lng
                        selectedPresetIndex = null
                        Toast.makeText(context, "📍 ${loc.name}", Toast.LENGTH_SHORT).show()
                    },
                    onRemove = { idx -> recentStore.remove(idx) },
                    onClear = { recentStore.clear() }
                )
            }

            // ── ROUTE SIMULATION (on MainScreen) ──
            val showRouteSim by prefsManager.showRouteSim.collectAsState(initial = false)
            if (showRouteSim) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🛣️ Route Simulation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        // Start point search
                        OutlinedTextField(
                            value = routeStartQuery,
                            onValueChange = { routeStartQuery = it },
                            label = { Text("🟢 Start Location") },
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    scope.launch {
                                        val results = NominatimApi.search(routeStartQuery, 1)
                                        if (results.isNotEmpty()) {
                                            routeStartLatLng = LatLng(results[0].lat, results[0].lng)
                                            routeStartQuery = results[0].name
                                            Toast.makeText(context, "✅ Start: ${results[0].name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "❌ Not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Search, "Search")
                                }
                            }
                        )

                        // End point search
                        OutlinedTextField(
                            value = routeEndQuery,
                            onValueChange = { routeEndQuery = it },
                            label = { Text("🔴 End Location") },
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    scope.launch {
                                        val results = NominatimApi.search(routeEndQuery, 1)
                                        if (results.isNotEmpty()) {
                                            routeEndLatLng = LatLng(results[0].lat, results[0].lng)
                                            routeEndQuery = results[0].name
                                            Toast.makeText(context, "✅ End: ${results[0].name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "❌ Not found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Search, "Search")
                                }
                            }
                        )

                        // Use current coords as start
                        TextButton(
                            onClick = {
                                routeStartLatLng = LatLng(latitude, longitude)
                                routeStartQuery = String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
                            }
                        ) {
                            Text("📍 Use current position as start", fontSize = 12.sp)
                        }

                        // Mode chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("driving" to "🚗", "walking" to "🚶").forEach { (mode, emoji) ->
                                FilterChip(
                                    selected = routeMode == mode,
                                    onClick = {
                                        routeMode = mode
                                        routeSpeedKmh = if (mode == "driving") 50f else 5f
                                    },
                                    label = { Text("$emoji $mode", fontSize = 12.sp) }
                                )
                            }
                        }

                        // Speed slider
                        Text(
                            "Speed: ${routeSpeedKmh.toInt()} km/h",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = routeSpeedKmh,
                            onValueChange = { routeSpeedKmh = it },
                            valueRange = 1f..200f,
                            colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber)
                        )

                        // Progress bar
                        LinearProgressIndicator(
                            progress = routeProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Amber,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            routeStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Control buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val start = routeStartLatLng
                                    val end = routeEndLatLng
                                    if (start == null || end == null) {
                                        Toast.makeText(context, "Search both start and end first", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    scope.launch {
                                        routeStatus = "Fetching route..."
                                        isRouteRunning = true

                                        val waypoints = if (routeDirection == "backward") {
                                            listOf(end, start)
                                        } else {
                                            listOf(start, end)
                                        }

                                        val routePoints = OsrmApi.getRoute(waypoints, routeMode)
                                        if (routePoints == null || routePoints.size < 2) {
                                            routeStatus = "❌ Could not find route"
                                            isRouteRunning = false
                                            return@launch
                                        }

                                        // Draw the full route polyline on the map
                                        routeGeoPoints = routePoints.map {
                                            GeoPoint(it.lat, it.lng)
                                        }

                                        // Wait 3 seconds so user sees the full route overview
                                        routeStatus = "Showing route (${routePoints.size} points)..."
                                        delay(3000)

                                        routeStatus = "Simulating..."
                                        val metersPerSecond = (routeSpeedKmh * 1000.0) / 3600.0

                                        // Ensure spoofing is active
                                        prefsManager.setSpoofEnabled(true)
                                        val firstPoint = routePoints[0]
                                        SpoofService.startSpoof(
                                            context, firstPoint.lat, firstPoint.lng, 10f, spoofMode
                                        )

                                        routeJob = scope.launch {
                                            var routeIdx = 0

                                            while (isActive) {
                                                delay(1000)

                                                // Check if route is complete
                                                if (routeIdx >= routePoints.size - 1) {
                                                    routeStatus = "✅ Route complete!"
                                                    routeProgress = 1f
                                                    isRouteRunning = false
                                                    break
                                                }

                                                // Advance along route by speed-based distance
                                                var distRemaining = metersPerSecond
                                                val startIdx = routeIdx

                                                while (distRemaining > 0 && routeIdx < routePoints.size - 1) {
                                                    val curr = routePoints[routeIdx]
                                                    val next = routePoints[routeIdx + 1]
                                                    val segDist = haversineMeters(
                                                        curr.lat, curr.lng, next.lat, next.lng
                                                    )

                                                    if (segDist <= distRemaining) {
                                                        distRemaining -= segDist
                                                        routeIdx++
                                                    } else {
                                                        // Partial segment — advance to next point
                                                        routeIdx++
                                                        break
                                                    }
                                                }

                                                // Ensure we always advance at least 1 point
                                                if (routeIdx == startIdx && routeIdx < routePoints.size - 1) {
                                                    routeIdx++
                                                }

                                                val pos = routePoints[routeIdx]
                                                routeProgress = routeIdx.toFloat() / (routePoints.size - 1).toFloat()

                                                // Compute bearing to next point for arrow direction
                                                if (routeIdx < routePoints.size - 1) {
                                                    val nextPt = routePoints[routeIdx + 1]
                                                    simulationBearing = computeBearing(
                                                        pos.lat, pos.lng, nextPt.lat, nextPt.lng
                                                    )
                                                }

                                                routeStatus = String.format(
                                                    Locale.US,
                                                    "Simulating... %d/%d (%.0f%%)",
                                                    routeIdx, routePoints.size - 1,
                                                    routeProgress * 100
                                                )

                                                // Update the spoofed position
                                                latitude = pos.lat
                                                longitude = pos.lng
                                                prefsManager.setLocation(pos.lat, pos.lng, 10f, "🛣️ Route Sim")
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
                                Text(if (isRoutePaused) "▶️" else "▶️ Start", fontSize = 12.sp)
                            }

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
                                Text("⏸️", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    routeJob?.cancel()
                                    isRouteRunning = false
                                    isRoutePaused = false
                                    routeProgress = 0f
                                    routeStatus = "Stopped"
                                    routeGeoPoints = emptyList() // clear polyline
                                    simulationBearing = 0f
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isRouteRunning,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Red)
                            ) {
                                Text("⏹️ Stop", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for FABs
        }

        // ════════════════════════════════════════
        // BOTTOM ACTION BAR
        // ════════════════════════════════════════
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Apply Button
                Button(
                    onClick = {
                        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                            Toast.makeText(context, "❌ Invalid coordinates!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Check location permission first
                        if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Toast.makeText(
                                context,
                                "⚠️ Location permission required. Please grant it.",
                                Toast.LENGTH_LONG
                            ).show()
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            return@Button
                        }

                        // Check mock location setup for Standard mode
                        if (spoofMode == SpoofMode.MOCK) {
                            val mockEngine = MockLocationEngine(context)
                            if (!mockEngine.isAvailable()) {
                                Toast.makeText(
                                    context,
                                    "⚠️ Enable mock locations in Developer Options first",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                        }

                        val name = when {
                            selectedPresetIndex != null && selectedPresetIndex!! < presets.size ->
                                presets[selectedPresetIndex!!].name
                            else -> String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
                        }

                        scope.launch {
                            try {
                                prefsManager.setSpoofEnabled(true)
                                prefsManager.setLocation(latitude, longitude, accuracy, name)
                                SpoofService.startSpoof(context, latitude, longitude, accuracy, spoofMode)

                                // Add to recent
                                val displayName = if (name.contains(",") && name.length < 20) {
                                    try {
                                        NominatimApi.reverse(latitude, longitude) ?: name
                                    } catch (e: Exception) {
                                        name
                                    }
                                } else {
                                    name
                                }
                                recentStore.add(RecentLocation(latitude, longitude, displayName))
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Toast.makeText(context, "📍 Location set to $name", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber)
                ) {
                    Text("✅ Apply", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }

                // Reset Button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                prefsManager.setSpoofEnabled(false)
                                SpoofService.stopSpoof(context)
                                // Restore current GPS location
                                hasLoadedGps = false
                            } catch (e: Exception) {
                                Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        selectedPresetIndex = null
                        Toast.makeText(context, "🔄 Real GPS restored", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔄 Real Location")
                }

                // Settings
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
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

// ── Compute Bearing (degrees clockwise from north) ──
private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}
