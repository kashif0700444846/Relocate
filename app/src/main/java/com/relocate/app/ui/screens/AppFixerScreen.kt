// [Relocate] [AppFixerScreen.kt] - App Fixer UI Screen v1.8.0
// Author: kashif0700444846
// Redesigned: Expandable per-app panels with checkbox-based identity vector selection.
// Shows current IDs, lets user pick what to regenerate, and applies only selected fixes.

package com.relocate.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.fixer.AppFixerService
import com.relocate.app.fixer.AppFixerService.IdentityVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Data Models ───────────────────────────────────────────────────────────────

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val isPinned: Boolean = false
)

// Popular ride-hailing / navigation apps pinned at the top
private val PINNED_PACKAGES = listOf(
    "com.ubercab.driver"    to "Uber Driver",
    "com.ubercab"           to "Uber",
    "com.ubercab.eats"      to "Uber Eats",
    "com.bolt.driver"       to "Bolt Driver",
    "ee.mtakso.driver"      to "Bolt Driver (alt)",
    "se.cabonline.driverapp" to "Cabonline Driver",
    "com.indriver.driver"   to "InDriver",
    "com.gett.driver"       to "Gett Driver",
    "com.yandex.taxi"       to "Yandex Taxi"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFixerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State ────────────────────────────────────────────────────────────────
    var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }

    // Per-app state maps
    var selectedVectors by remember { mutableStateOf<Map<String, Set<IdentityVector>>>(emptyMap()) }
    var currentIds by remember { mutableStateOf<Map<String, AppFixerService.CurrentIds?>>(emptyMap()) }
    var loadingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isFixing by remember { mutableStateOf<String?>(null) }
    var fixProgress by remember { mutableStateOf<List<Pair<String, AppFixerService.StepStatus>>>(emptyList()) }
    var fixResult by remember { mutableStateOf<AppFixerService.FixResult?>(null) }

    // Load installed apps
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { info ->
                    AppEntry(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        icon = try { info.loadIcon(pm) } catch (_: Exception) { null },
                        isPinned = PINNED_PACKAGES.any { it.first == info.packageName }
                    )
                }
                .sortedWith(
                    compareByDescending<AppEntry> { it.isPinned }
                        .thenBy { it.label.lowercase() }
                )
            allApps = installed
        }
    }

    // Filter by search
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🔧 App Fixer", fontWeight = FontWeight.Bold)
                        Text(
                            "${allApps.size} apps • Tap to expand",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Search bar ──────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // ── App list ────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isExpanded = expandedPackage == app.packageName
                    val appVectors = selectedVectors[app.packageName]
                        ?: IdentityVector.values().toSet() // Default: all checked

                    AppCard(
                        app = app,
                        isExpanded = isExpanded,
                        currentIds = currentIds[app.packageName],
                        isLoadingIds = app.packageName in loadingIds,
                        selectedVectors = appVectors,
                        isFixing = isFixing == app.packageName,
                        fixProgress = if (isFixing == app.packageName) fixProgress else emptyList(),
                        fixResult = if (isFixing == app.packageName) fixResult else null,
                        onToggleExpand = {
                            if (isExpanded) {
                                expandedPackage = null
                            } else {
                                expandedPackage = app.packageName
                                fixResult = null
                                fixProgress = emptyList()
                                // Load current IDs when expanding
                                if (currentIds[app.packageName] == null) {
                                    loadingIds = loadingIds + app.packageName
                                    scope.launch {
                                        val ids = AppFixerService.readCurrentIds(context)
                                        currentIds = currentIds + (app.packageName to ids)
                                        loadingIds = loadingIds - app.packageName
                                    }
                                }
                            }
                        },
                        onToggleVector = { vector ->
                            val current = selectedVectors[app.packageName]
                                ?: IdentityVector.values().toSet()
                            val updated = if (vector in current) current - vector else current + vector
                            selectedVectors = selectedVectors + (app.packageName to updated)
                        },
                        onSelectAll = {
                            selectedVectors = selectedVectors +
                                (app.packageName to IdentityVector.values().toSet())
                        },
                        onDeselectAll = {
                            selectedVectors = selectedVectors + (app.packageName to emptySet())
                        },
                        onRefreshIds = {
                            loadingIds = loadingIds + app.packageName
                            scope.launch {
                                val ids = AppFixerService.readCurrentIds(context)
                                currentIds = currentIds + (app.packageName to ids)
                                loadingIds = loadingIds - app.packageName
                            }
                        },
                        onApplyFix = {
                            if (appVectors.isEmpty()) {
                                Toast.makeText(context, "Select at least one fix", Toast.LENGTH_SHORT).show()
                                return@AppCard
                            }
                            isFixing = app.packageName
                            fixResult = null
                            fixProgress = emptyList()
                            scope.launch {
                                val result = AppFixerService.fixApp(
                                    context = context,
                                    packageName = app.packageName,
                                    selectedVectors = appVectors,
                                    onProgress = { label, status ->
                                        fixProgress = fixProgress + (label to status)
                                    }
                                )
                                fixResult = result
                                isFixing = null
                                // Refresh IDs after fix
                                val updated = AppFixerService.readCurrentIds(context)
                                currentIds = currentIds + (app.packageName to updated)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ── App Card with Expandable Panel ──────────────────────────────────────────────

@Composable
private fun AppCard(
    app: AppEntry,
    isExpanded: Boolean,
    currentIds: AppFixerService.CurrentIds?,
    isLoadingIds: Boolean,
    selectedVectors: Set<IdentityVector>,
    isFixing: Boolean,
    fixProgress: List<Pair<String, AppFixerService.StepStatus>>,
    fixResult: AppFixerService.FixResult?,
    onToggleExpand: () -> Unit,
    onToggleVector: (IdentityVector) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onRefreshIds: () -> Unit,
    onApplyFix: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isPinned)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // ── App Header Row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                if (app.icon != null) {
                    val bitmap = remember(app.icon) {
                        val bmp = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        app.icon.setBounds(0, 0, 48, 48)
                        app.icon.draw(canvas)
                        bmp
                    }
                    val imageBitmap = remember(bitmap) {
                        androidx.compose.ui.graphics.asImageBitmap(bitmap)
                    }
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.label.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // App name + package
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.label,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isPinned) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "⭐",
                                fontSize = 12.sp
                            )
                        }
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                // Expand/collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Expanded Panel ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // ── Current IDs Section ─────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 Current Identity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        IconButton(
                            onClick = onRefreshIds,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh IDs",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    if (isLoadingIds) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Reading IDs...", fontSize = 12.sp)
                        }
                    } else if (currentIds != null) {
                        IdRow("Android ID", currentIds.androidId)
                        IdRow("DRM ID", if (currentIds.drmId.length > 20)
                            currentIds.drmId.take(16) + "..." else currentIds.drmId)
                        IdRow("Google Ad ID", currentIds.gaid)
                        IdRow("Fingerprint", if (currentIds.fingerprint.length > 40)
                            currentIds.fingerprint.take(36) + "..." else currentIds.fingerprint)
                        IdRow("Model", "${currentIds.buildModel} (${currentIds.buildDevice})")
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // ── Checkboxes Section ──────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔧 Select Fixes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row {
                            TextButton(onClick = onSelectAll, contentPadding = PaddingValues(4.dp)) {
                                Text("All", fontSize = 11.sp)
                            }
                            TextButton(onClick = onDeselectAll, contentPadding = PaddingValues(4.dp)) {
                                Text("None", fontSize = 11.sp)
                            }
                        }
                    }

                    IdentityVector.values().forEach { vector ->
                        VectorCheckbox(
                            vector = vector,
                            isChecked = vector in selectedVectors,
                            onToggle = { onToggleVector(vector) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Apply Button ─────────────────────────────────────────
                    Button(
                        onClick = onApplyFix,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isFixing && selectedVectors.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isFixing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Applying fixes...")
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("🔧 Apply Selected Fixes (${selectedVectors.size})")
                        }
                    }

                    // ── Fix Progress ─────────────────────────────────────────
                    if (fixProgress.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                fixProgress.forEach { (label, status) ->
                                    StepRow(label = label, status = status)
                                }
                            }
                        }
                    }

                    // ── Fix Result ────────────────────────────────────────────
                    if (fixResult != null) {
                        Spacer(Modifier.height(8.dp))
                        val success = fixResult.steps.count { it.second == AppFixerService.StepStatus.SUCCESS }
                        val failed = fixResult.steps.count { it.second == AppFixerService.StepStatus.FAILED }
                        val total = fixResult.steps.size

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (failed == 0) Color(0x204CAF50) else Color(0x20FF5722)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (failed == 0) "✅ All fixes applied!" else "⚠️ $success/$total steps succeeded",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (fixResult.newAndroidId != null) {
                                    Text("New Android ID: ${fixResult.newAndroidId}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                if (fixResult.newDrmId != null) {
                                    Text("New DRM ID: ${fixResult.newDrmId!!.take(16)}...", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                if (fixResult.newGaid != null) {
                                    Text("New GAID: ${fixResult.newGaid!!.take(12)}...", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                if (fixResult.newFingerprint != null) {
                                    Text("New Fingerprint: ${fixResult.newFingerprint!!.take(30)}...", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ── ID Row (current identity display) ───────────────────────────────────────────

@Composable
private fun IdRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Vector Checkbox Row ─────────────────────────────────────────────────────────

@Composable
private fun VectorCheckbox(
    vector: IdentityVector,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    val iconColor = when (vector) {
        IdentityVector.CLEAR_APP_DATA -> Color(0xFFFF5722)
        IdentityVector.ANDROID_ID -> Color(0xFF2196F3)
        IdentityVector.DRM_ID -> Color(0xFF9C27B0)
        IdentityVector.GAID -> Color(0xFF4CAF50)
        IdentityVector.FINGERPRINT -> Color(0xFFFF9800)
        IdentityVector.CHROME_COOKIES -> Color(0xFF00BCD4)
        IdentityVector.GMS_CACHE -> Color(0xFF607D8B)
        IdentityVector.APPOPS_RESET -> Color(0xFF795548)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(checkedColor = iconColor)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vector.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = vector.description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 13.sp
            )
        }
    }
}

// ── Step Row (fix progress display) ─────────────────────────────────────────────

@Composable
private fun StepRow(label: String, status: AppFixerService.StepStatus) {
    val icon = when (status) {
        AppFixerService.StepStatus.PENDING -> "⏳"
        AppFixerService.StepStatus.RUNNING -> "⚡"
        AppFixerService.StepStatus.SUCCESS -> "✅"
        AppFixerService.StepStatus.FAILED -> "❌"
        AppFixerService.StepStatus.SKIPPED -> "⏭️"
    }
    val textColor = when (status) {
        AppFixerService.StepStatus.FAILED -> Color(0xFFFF4444)
        AppFixerService.StepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        AppFixerService.StepStatus.SUCCESS -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 12.sp, modifier = Modifier.width(24.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
