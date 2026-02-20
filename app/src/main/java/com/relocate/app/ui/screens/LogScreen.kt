// [Relocate] [LogScreen.kt] - Live Console with Hook Activity Tab
// Author: AI
// v1.8.0: Two-tab console — "App Logs" (existing ring-buffer) and
// "Hook Activity" (cross-process XPosed hook log from shared file).
// Hook Activity shows which app triggered which hook in real-time.

package com.relocate.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.logging.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appListState = rememberLazyListState()
    val hookListState = rememberLazyListState()

    // ── State ────────────────────────────────────────────────────────────────
    var selectedTab by remember { mutableIntStateOf(0) }
    var appLogEntries by remember { mutableStateOf(AppLogger.getEntries()) }
    var hookEntries by remember { mutableStateOf<List<AppLogger.HookEntry>>(emptyList()) }
    var autoScroll by remember { mutableStateOf(true) }
    var isLive by remember { mutableStateOf(true) }

    // Request storage permission for reading hook log file
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted/denied — we try to read regardless */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ — might need MANAGE_EXTERNAL_STORAGE for the shared file
            // We'll try to read anyway; the hook log is in /sdcard/Relocate/
        }
    }

    // Polling loop — refresh every 500ms
    LaunchedEffect(isLive) {
        while (isLive) {
            appLogEntries = AppLogger.getEntries()

            // Read new hook entries (delta)
            val newHookEntries = AppLogger.readNewHookEntries()
            if (newHookEntries.isNotEmpty()) {
                hookEntries = hookEntries + newHookEntries
                // Keep last 1000
                if (hookEntries.size > 1000) {
                    hookEntries = hookEntries.takeLast(1000)
                }
            }

            // Auto-scroll
            if (autoScroll) {
                if (selectedTab == 0 && appLogEntries.isNotEmpty()) {
                    try { appListState.animateScrollToItem(appLogEntries.size - 1) } catch (_: Exception) {}
                }
                if (selectedTab == 1 && hookEntries.isNotEmpty()) {
                    try { hookListState.animateScrollToItem(hookEntries.size - 1) } catch (_: Exception) {}
                }
            }
            delay(500)
        }
    }

    // Load all hook entries on first open
    LaunchedEffect(Unit) {
        hookEntries = AppLogger.readAllHookEntries()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📺 Live Console", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        // Live indicator
                        if (isLive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFFF1744), CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "LIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF1744)
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
                    // Live toggle
                    IconButton(onClick = { isLive = !isLive }) {
                        Text(
                            if (isLive) "⏸" else "▶️",
                            fontSize = 16.sp
                        )
                    }
                    // Auto-scroll toggle
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Auto-scroll",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Copy logs
                    IconButton(onClick = {
                        val text = if (selectedTab == 0) {
                            appLogEntries.joinToString("\n") { it.toString() }
                        } else {
                            hookEntries.joinToString("\n") { it.toString() }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Relocate Logs", text))
                        Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    // Clear logs
                    IconButton(onClick = {
                        if (selectedTab == 0) {
                            AppLogger.clear()
                            appLogEntries = emptyList()
                        } else {
                            AppLogger.clearHookLog()
                            hookEntries = emptyList()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
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
            // ── Tab Row ─────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📋 App Logs", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Badge { Text("${appLogEntries.size}") }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎯 Hook Activity", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Badge(
                                containerColor = if (hookEntries.isNotEmpty()) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text("${hookEntries.size}")
                            }
                        }
                    }
                )
            }

            // ── Tab Content ─────────────────────────────────────────────────
            when (selectedTab) {
                0 -> AppLogTab(appLogEntries, appListState)
                1 -> HookActivityTab(hookEntries, hookListState)
            }
        }
    }
}

// ── App Log Tab (existing logs) ─────────────────────────────────────────────────

@Composable
private fun AppLogTab(
    entries: List<AppLogger.Entry>,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (entries.isEmpty()) {
        EmptyState("📭 No app logs yet", "Logs appear as you use the app")
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(entries) { entry ->
                AppLogEntryRow(entry)
            }
        }
    }
}

// ── Hook Activity Tab (cross-process XPosed hook logs) ──────────────────────────

@Composable
private fun HookActivityTab(
    entries: List<AppLogger.HookEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (entries.isEmpty()) {
        EmptyState(
            "🎯 No hook activity yet",
            "Hook logs appear when target apps (like Uber Driver) run with LSPosed hooks active"
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(entries) { entry ->
                HookEntryRow(entry)
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── App Log Entry Row ───────────────────────────────────────────────────────────

@Composable
private fun AppLogEntryRow(entry: AppLogger.Entry) {
    val bgColor = when (entry.level) {
        "E" -> Color(0x20FF0000)
        "W" -> Color(0x20FFA000)
        "I" -> Color.Transparent
        "D" -> Color(0x100000FF)
        else -> Color.Transparent
    }
    val levelColor = when (entry.level) {
        "E" -> Color(0xFFFF4444)
        "W" -> Color(0xFFFFAA00)
        "I" -> Color(0xFF4CAF50)
        "D" -> Color(0xFF2196F3)
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.timestamp,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )
        Text(
            text = entry.level,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = levelColor,
            modifier = Modifier.width(14.dp)
        )
        Text(
            text = "${entry.tag}: ${entry.message}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Hook Entry Row ──────────────────────────────────────────────────────────────

@Composable
private fun HookEntryRow(entry: AppLogger.HookEntry) {
    // Color-code by hook type
    val hookColor = when {
        entry.hookId.startsWith("Hook0") && entry.hookId.length == 6 &&
            entry.hookId[4].digitToIntOrNull() in 1..5 -> Color(0xFF4CAF50)  // Location hooks = green
        entry.hookId.startsWith("Hook0") && entry.hookId.length == 6 &&
            entry.hookId[4].digitToIntOrNull() in 6..9 -> Color(0xFFFF9800)  // Root hooks = orange
        entry.hookId.contains("12") || entry.hookId.contains("13") -> Color(0xFF2196F3)  // ID hooks = blue
        entry.hookId.contains("14") || entry.hookId.contains("15") ||
            entry.hookId.contains("16") -> Color(0xFF9C27B0)  // New v1.8 hooks = purple
        entry.hookId == "INIT" -> Color(0xFF00BCD4)  // Init = teal
        else -> Color.Gray
    }

    // Package name → short name
    val shortProcess = when {
        entry.processName.contains("ubercab.driver") -> "🚕 Uber Driver"
        entry.processName.contains("ubercab.eats") -> "🍔 Uber Eats"
        entry.processName.contains("ubercab") -> "🚗 Uber"
        entry.processName.contains("bolt") -> "⚡ Bolt"
        entry.processName.contains("cabonline") -> "🚖 Cabonline"
        entry.processName.contains("chrome") -> "🌐 Chrome"
        entry.processName.contains("gms") -> "📱 GMS"
        else -> entry.processName.substringAfterLast(".")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(hookColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            text = entry.timestamp,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )
        // Hook ID badge
        Box(
            modifier = Modifier
                .background(hookColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = entry.hookId,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = hookColor
            )
        }
        Spacer(Modifier.width(4.dp))
        // Process name
        Text(
            text = shortProcess,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp)
        )
        Spacer(Modifier.width(4.dp))
        // Details
        Text(
            text = entry.details,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
