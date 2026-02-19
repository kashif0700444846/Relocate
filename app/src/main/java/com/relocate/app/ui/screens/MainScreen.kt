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
import com.relocate.app.network.NominatimApi
import com.relocate.app.spoofing.MockLocationEngine
import com.relocate.app.spoofing.RootSpoofEngine
import com.relocate.app.spoofing.SpoofService
import com.relocate.app.ui.components.*
import com.relocate.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
                    }
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
