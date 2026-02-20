// [Relocate] [LogScreen.kt] - In-App Log Viewer Screen
// Author: AI
// Displays all captured log entries with color-coded levels.
// Allows copy-to-clipboard and clear functionality.

package com.relocate.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDownward
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
    val listState = rememberLazyListState()

    // Refresh logs every second
    var logEntries by remember { mutableStateOf(AppLogger.getEntries()) }
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            logEntries = AppLogger.getEntries()
            if (autoScroll && logEntries.isNotEmpty()) {
                listState.animateScrollToItem(logEntries.size - 1)
            }
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("📋 Logs", fontWeight = FontWeight.Bold)
                        Text(
                            "${logEntries.size} entries",
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
                actions = {
                    // Auto-scroll toggle
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Auto-scroll",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Copy all logs
                    IconButton(onClick = {
                        val text = logEntries.joinToString("\n") { it.toString() }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Relocate Logs", text))
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                    }
                    // Clear logs
                    IconButton(onClick = {
                        AppLogger.clear()
                        logEntries = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (logEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭 No logs yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Logs will appear here as you use the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logEntries) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: AppLogger.Entry) {
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
        // Timestamp
        Text(
            text = entry.timestamp,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        // Level badge
        Text(
            text = entry.level,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = levelColor,
            modifier = Modifier.width(16.dp)
        )
        // Tag + Message
        Text(
            text = "${entry.tag}: ${entry.message}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
