// [Relocate] [RouteSimulationScreen.kt] - Standalone Route Simulation
// Author: AI
// Purpose: Full-featured route simulation with waypoint search, mode/speed
// controls, and live spoofed position updates along OSRM routes.

package com.relocate.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.data.PreferencesManager
import com.relocate.app.data.SpoofMode
import com.relocate.app.network.LatLng
import com.relocate.app.network.NominatimApi
import com.relocate.app.network.OsrmApi
import com.relocate.app.spoofing.SpoofService
import com.relocate.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

// ── Helper Data Class ──
private data class WaypointState(
    val label: String,
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSimulationScreen(
    prefsManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val spoofMode by prefsManager.spoofMode.collectAsState(initial = SpoofMode.MOCK)

    // ── Route Simulation State ──
    var routeWaypoints by remember {
        mutableStateOf(
            listOf(
                WaypointState("A", ""),
                WaypointState("B", "")
            )
        )
    }
    var routeMode by remember { mutableStateOf("driving") }
    var routeDirection by remember { mutableStateOf("forward") }
    var routeSpeedKmh by remember { mutableFloatStateOf(50f) }
    var routeStatus by remember { mutableStateOf("Add at least 2 waypoints to begin") }
    var routeProgress by remember { mutableFloatStateOf(0f) }
    var isRouteRunning by remember { mutableStateOf(false) }
    var isRoutePaused by remember { mutableStateOf(false) }
    var routeJob by remember { mutableStateOf<Job?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛣️", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Route Simulation", fontWeight = FontWeight.Bold)
                            Text(
                                "Simulate movement along real roads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Stop any running simulation before leaving
                        routeJob?.cancel()
                        onBack()
                    }) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Error Banner ──
            if (errorMessage != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Red.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            errorMessage ?: "",
                            color = Red,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, "Dismiss",
                                modifier = Modifier.size(16.dp),
                                tint = Red
                            )
                        }
                    }
                }
            }

            // ════════════════════════════════════════
            // WAYPOINTS
            // ════════════════════════════════════════
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍 Waypoints", fontWeight = FontWeight.Bold, fontSize = 15.sp)

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

                            // Search field
                            var wpQuery by remember { mutableStateOf(wp.name) }
                            OutlinedTextField(
                                value = wpQuery,
                                onValueChange = { wpQuery = it },
                                placeholder = { Text("🔍 Search...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Search button
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
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
                                                errorMessage = null
                                            } else {
                                                errorMessage = "No results found for \"$wpQuery\""
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Search failed: ${e.message ?: "Unknown error"}"
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Search, "Search", modifier = Modifier.size(16.dp))
                            }

                            // Remove (only if > 2 waypoints)
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

                    // Add Waypoint
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
                }
            }

            // ════════════════════════════════════════
            // MODE & DIRECTION
            // ════════════════════════════════════════
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Travel Mode
                    Text("🚗 Travel Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "driving" to "🚗 Driving",
                            "walking" to "🚶 Walking",
                            "custom" to "⚙️ Custom"
                        ).forEach { (mode, label) ->
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

                    Divider()

                    // Direction
                    Text("🧭 Direction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "forward" to "➡️ Forward",
                            "backward" to "⬅️ Backward",
                            "loop" to "🔄 Loop"
                        ).forEach { (dir, label) ->
                            FilterChip(
                                selected = routeDirection == dir,
                                onClick = { routeDirection = dir },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Divider()

                    // Speed
                    Text(
                        "⚡ Speed: ${routeSpeedKmh.toInt()} km/h",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
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
                }
            }

            // ════════════════════════════════════════
            // PROGRESS & CONTROLS
            // ════════════════════════════════════════
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📊 Simulation Status", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    // Progress bar
                    LinearProgressIndicator(
                        progress = routeProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Amber,
                        trackColor = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        routeStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )

                    // Control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start / Resume
                        Button(
                            onClick = {
                                try {
                                    val validWps = routeWaypoints.filter { it.lat != null && it.lng != null }
                                    if (validWps.size < 2) {
                                        errorMessage = "Search at least 2 waypoints before starting"
                                        return@Button
                                    }

                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            routeStatus = "Fetching route..."
                                            isRouteRunning = true

                                            var ordered = validWps.map { LatLng(it.lat!!, it.lng!!) }
                                            if (routeDirection == "backward") ordered = ordered.reversed()

                                            val routePoints = OsrmApi.getRoute(ordered, routeMode)
                                            if (routePoints == null || routePoints.size < 2) {
                                                routeStatus = "❌ Could not find route"
                                                isRouteRunning = false
                                                errorMessage = "OSRM returned no valid route. Try different waypoints."
                                                return@launch
                                            }

                                            routeStatus = "Simulating... (${routePoints.size} points)"
                                            val metersPerSecond = (routeSpeedKmh * 1000.0) / 3600.0

                                            // Ensure spoofing is on
                                            prefsManager.setSpoofEnabled(true)
                                            val firstPoint = routePoints[0]
                                            SpoofService.startSpoof(
                                                context, firstPoint.lat, firstPoint.lng, 10f, spoofMode
                                            )

                                            routeJob = scope.launch {
                                                var routeIdx = 0
                                                var goingForward = true

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
                                                            distToTravel -= segDist
                                                            routeIdx = nextIdx
                                                        } else {
                                                            distToTravel = 0.0
                                                        }
                                                    }

                                                    val pos = routePoints[routeIdx]
                                                    routeProgress = routeIdx.toFloat() / (routePoints.size - 1).toFloat()
                                                    routeStatus = String.format(
                                                        Locale.US,
                                                        "Simulating... %d/%d (%.0f%%)",
                                                        routeIdx, routePoints.size - 1,
                                                        routeProgress * 100
                                                    )

                                                    // Update spoofed position
                                                    prefsManager.setLocation(pos.lat, pos.lng, 10f, "🛣️ Route Simulation")
                                                    SpoofService.updateSpoof(context, pos.lat, pos.lng, 10f)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            routeStatus = "❌ Error"
                                            isRouteRunning = false
                                            errorMessage = "Simulation error: ${e.message ?: "Unknown"}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Failed to start: ${e.message ?: "Unknown error"}"
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
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
