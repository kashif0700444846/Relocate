// [Relocate] [FeaturesScreen.kt] - Permission-Gated Features Dashboard
// Author: AI
// Purpose: Displays device status (mock location, root, LSPosed) and
// gates features based on available permissions.

package com.relocate.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.ui.theme.*
import com.relocate.app.util.DeviceStatusUtils
import com.relocate.app.util.DeviceStatusUtils.LSPosedStatus
import com.relocate.app.util.DeviceStatusUtils.MockLocationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    onBack: () -> Unit,
    onNavigateToFixer: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRouteSim: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Live Permission Status ──
    var mockStatus by remember { mutableStateOf(MockLocationStatus.UNKNOWN) }
    var isRootAvailable by remember { mutableStateOf(false) }
    var lsposedStatus by remember { mutableStateOf(LSPosedStatus.UNKNOWN) }
    var hasLocation by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(true) }

    // Poll permissions every 3 seconds (so changes reflect immediately)
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                withContext(Dispatchers.IO) {
                    mockStatus = try { DeviceStatusUtils.getMockLocationStatus(context) } catch (_: Exception) { MockLocationStatus.UNKNOWN }
                    isRootAvailable = try { DeviceStatusUtils.isRootAvailable() } catch (_: Exception) { false }
                    lsposedStatus = try { DeviceStatusUtils.getLSPosedStatus() } catch (_: Exception) { LSPosedStatus.UNKNOWN }
                    hasLocation = try { DeviceStatusUtils.hasLocationPermission(context) } catch (_: Exception) { false }
                }
            } catch (_: Exception) {
                // Silently fail — show UNKNOWN/false states rather than crashing
            }
            isChecking = false
            delay(3000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Features", fontWeight = FontWeight.Bold)
                            Text(
                                "Permission-gated capabilities",
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
                },
                actions = {
                    // Refresh button
                    IconButton(onClick = {
                        isChecking = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                mockStatus = DeviceStatusUtils.getMockLocationStatus(context)
                                isRootAvailable = DeviceStatusUtils.isRootAvailable()
                                lsposedStatus = DeviceStatusUtils.getLSPosedStatus()
                                hasLocation = DeviceStatusUtils.hasLocationPermission(context)
                            } catch (_: Exception) { /* show current state */ }
                            isChecking = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            // STATUS DASHBOARD
            // ════════════════════════════════════════
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📊 Device Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Live checks — updates every 3 seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isChecking) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Checking permissions...", fontSize = 13.sp)
                        }
                    } else {
                        // Mock Location Status
                        StatusRow(
                            label = "Mock Location",
                            description = when (mockStatus) {
                                MockLocationStatus.ENABLED -> "Relocate is set as mock provider"
                                MockLocationStatus.DISABLED -> "Go to Developer Options → Select mock app"
                                MockLocationStatus.UNKNOWN -> "Cannot determine status"
                            },
                            isOk = mockStatus == MockLocationStatus.ENABLED,
                            isWarning = mockStatus == MockLocationStatus.UNKNOWN,
                            onClick = if (mockStatus == MockLocationStatus.DISABLED) {
                                {
                                    try {
                                        context.startActivity(
                                            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Open Developer Options manually", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else null
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Root Status
                        StatusRow(
                            label = "Root Access (SU)",
                            description = if (isRootAvailable) "Root is available — all features unlocked" else "Not rooted — root features disabled",
                            isOk = isRootAvailable,
                            isWarning = false
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // LSPosed Status
                        StatusRow(
                            label = "LSPosed Hooks",
                            description = when (lsposedStatus) {
                                LSPosedStatus.ACTIVE -> "Hooks are active — detection bypass ON"
                                LSPosedStatus.INACTIVE -> "No hook activity detected. Enable in LSPosed Manager"
                                LSPosedStatus.UNKNOWN -> "Cannot check hook status"
                            },
                            isOk = lsposedStatus == LSPosedStatus.ACTIVE,
                            isWarning = lsposedStatus == LSPosedStatus.UNKNOWN
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Location Permission
                        StatusRow(
                            label = "Location Permission",
                            description = if (hasLocation) "Fine location granted" else "Location permission not granted",
                            isOk = hasLocation,
                            isWarning = false
                        )
                    }
                }
            }

            // ════════════════════════════════════════
            // FEATURE CARDS
            // ════════════════════════════════════════
            Text(
                "Available Features",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            // 🔧 App Fixer — requires root
            FeatureCard(
                icon = Icons.Default.Build,
                title = "App Fixer",
                description = "Per-app identity reset with selective vector control. Regenerate Android ID, DRM ID, GAID, Build Fingerprint, and clear Chrome cookies.",
                requirementLabel = "Requires Root",
                isEnabled = isRootAvailable,
                accentColor = Color(0xFFFF9800),
                onClick = onNavigateToFixer
            )

            // 📺 Live Console — always accessible
            FeatureCard(
                icon = Icons.Default.Terminal,
                title = "Live Console",
                description = "Real-time App Logs and Hook Activity monitoring. View spoof events and hook interceptions as they happen.",
                requirementLabel = null,
                isEnabled = true,
                accentColor = Color(0xFF2196F3),
                onClick = onNavigateToLogs
            )

            // 🛣️ Route Simulation — requires location
            FeatureCard(
                icon = Icons.Default.Route,
                title = "Route Simulation",
                description = "Simulate movement along a real road route. Set waypoints, choose driving or walking mode, and control speed.",
                requirementLabel = if (!hasLocation) "Requires Location Permission" else null,
                isEnabled = hasLocation,
                accentColor = Color(0xFF4CAF50),
                onClick = onNavigateToRouteSim
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Status Row Component ────────────────────────────────────────────────────

@Composable
private fun StatusRow(
    label: String,
    description: String,
    isOk: Boolean,
    isWarning: Boolean,
    onClick: (() -> Unit)? = null
) {
    val dotColor by animateColorAsState(
        targetValue = when {
            isOk -> Color(0xFF4CAF50)   // Green
            isWarning -> Color(0xFFFFC107) // Amber
            else -> Color(0xFFF44336)    // Red
        },
        label = "statusDot"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        // Action icon for fixable statuses
        if (onClick != null && !isOk) {
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = "Fix",
                    modifier = Modifier.size(16.dp),
                    tint = dotColor
                )
            }
        } else {
            // Checkmark or X
            Text(
                if (isOk) "✅" else if (isWarning) "⚠️" else "❌",
                fontSize = 16.sp
            )
        }
    }
}

// ── Feature Card Component ──────────────────────────────────────────────────

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    requirementLabel: String?,
    isEnabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val alpha = if (isEnabled) 1f else 0.5f

    Card(
        onClick = {
            if (isEnabled) onClick()
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2
                )

                // Requirement badge
                if (requirementLabel != null && !isEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF44336).copy(alpha = 0.12f)
                    ) {
                        Text(
                            "🔒 $requirementLabel",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }

            // Arrow or lock
            Icon(
                if (isEnabled) Icons.Default.ChevronRight else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF44336).copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
