// [Relocate] [AppFixerScreen.kt] - App Fixer UI Screen
// Author: kashif0700444846
// Allows user to pick any installed app and run a full device identity reset for it.
// Pins popular ride-hailing apps at the top for quick access.

package com.relocate.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.relocate.app.fixer.AppFixerService
import kotlinx.coroutines.launch

// ─── Data Models ───────────────────────────────────────────────────────────────

private data class AppEntry(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isPinned: Boolean = false
)

// Popular ride-hailing / navigation apps pinned at the top
private val PINNED_PACKAGES = listOf(
    "com.ubercab.driver"    to "Uber Driver",
    "com.ubercab"           to "Uber",
    "com.ubercab.eats"      to "Uber Eats",
    "me.lyft.android"       to "Lyft",
    "bolt.driver"           to "Bolt Driver",
    "ee.mtakso.driver"      to "Bolt (Taxify)",
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
    val pm = context.packageManager

    // ── State ──
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var isFixing by remember { mutableStateOf(false) }
    var fixDone by remember { mutableStateOf(false) }
    val progressSteps = remember { mutableStateListOf<Pair<String, AppFixerService.StepStatus>>() }
    var fixResult by remember { mutableStateOf<AppFixerService.FixResult?>(null) }

    // ── Load installed apps ──
    val allApps = remember {
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // user apps only
            .map { info ->
                val isPinned = PINNED_PACKAGES.any { it.first == info.packageName }
                AppEntry(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    icon = try { pm.getApplicationIcon(info.packageName) } catch (_: Exception) { null },
                    isPinned = isPinned
                )
            }
            .sortedWith(compareByDescending<AppEntry> { it.isPinned }.thenBy { it.name })

        installed
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    // ── UI ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App Fixer", fontWeight = FontWeight.Bold)
                        Text(
                            "Fix authentication & device identity",
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
            // ── Search bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search apps...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // ── Fix Dialog (shown when app selected) ──
            AnimatedVisibility(
                visible = selectedApp != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedApp?.let { app ->
                    FixPanel(
                        app = app,
                        isFixing = isFixing,
                        fixDone = fixDone,
                        progressSteps = progressSteps,
                        fixResult = fixResult,
                        onFix = {
                            isFixing = true
                            fixDone = false
                            progressSteps.clear()
                            fixResult = null
                            scope.launch {
                                val result = AppFixerService.fixApp(
                                    context = context,
                                    packageName = app.packageName,
                                    onProgress = { label, status ->
                                        val idx = progressSteps.indexOfFirst { it.first == label }
                                        if (idx >= 0) progressSteps[idx] = label to status
                                        else progressSteps.add(label to status)
                                    }
                                )
                                fixResult = result
                                isFixing = false
                                fixDone = true
                            }
                        },
                        onDismiss = {
                            selectedApp = null
                            fixDone = false
                            progressSteps.clear()
                            fixResult = null
                        }
                    )
                }
            }

            // ── App List ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pinned label
                if (filteredApps.any { it.isPinned }) {
                    item {
                        Text(
                            "📌 Ride-Hailing Apps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
                items(filteredApps.filter { it.isPinned }) { app ->
                    AppListItem(app = app, isSelected = selectedApp?.packageName == app.packageName) {
                        selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                        fixDone = false
                        progressSteps.clear()
                    }
                }
                if (filteredApps.any { !it.isPinned }) {
                    item {
                        Text(
                            "All Apps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                }
                items(filteredApps.filter { !it.isPinned }) { app ->
                    AppListItem(app = app, isSelected = selectedApp?.packageName == app.packageName) {
                        selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                        fixDone = false
                        progressSteps.clear()
                    }
                }
            }
        }
    }
}

// ── Per-app row ────────────────────────────────────────────────────────────────
@Composable
private fun AppListItem(
    app: AppEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            app.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap(48, 48).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
            } ?: Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(app.name.first().uppercaseChar().toString(), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (app.isPinned) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        "PINNED",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Fix Panel ─────────────────────────────────────────────────────────────────
@Composable
private fun FixPanel(
    app: AppEntry,
    isFixing: Boolean,
    fixDone: Boolean,
    progressSteps: List<Pair<String, AppFixerService.StepStatus>>,
    fixResult: AppFixerService.FixResult?,
    onFix: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fix: ${app.name}", fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Steps or pre-fix info
            if (progressSteps.isEmpty() && !isFixing) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Will perform:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        "Force-stop app",
                        "Clear app data (pm clear)",
                        "Generate new Android ID",
                        "Spoof Widevine DRM ID (x-uber-drm-id)",
                        "Clear Google Play Services cache",
                        "Reset AppOps permissions"
                    ).forEach { step ->
                        Text("• $step", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    progressSteps.forEach { (label, status) ->
                        StepRow(label = label, status = status)
                    }
                }
            }

            // Result
            if (fixDone && fixResult != null) {
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                Text("✅ Fix Complete!", fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50))
                Spacer(Modifier.height(4.dp))
                Text("New Android ID:", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fixResult.newAndroidId, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(2.dp))
                Text("New DRM ID (first 16 chars):", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fixResult.newDrmId.take(16) + "...", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ Restart ${app.name} fresh. If still blocked, contact Uber support.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action button
            Button(
                onClick = onFix,
                enabled = !isFixing && !fixDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isFixing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Fixing...")
                } else if (fixDone) {
                    Text("✅ Done — Close and reopen the app")
                } else {
                    Text("🔧 Fix ${app.name}")
                }
            }
        }
    }
}

// ── Step Row ──────────────────────────────────────────────────────────────────
@Composable
private fun StepRow(label: String, status: AppFixerService.StepStatus) {
    val (icon, color) = when (status) {
        AppFixerService.StepStatus.SUCCESS -> Pair(Icons.Default.CheckCircle, Color(0xFF4CAF50))
        AppFixerService.StepStatus.FAILED  -> Pair(Icons.Default.Error, Color(0xFFF44336))
        AppFixerService.StepStatus.RUNNING -> Pair(null, MaterialTheme.colorScheme.primary)
        else -> Pair(null, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == AppFixerService.StepStatus.RUNNING) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        } else {
            Box(Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
