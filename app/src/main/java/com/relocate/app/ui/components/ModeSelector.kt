// [Relocate] [ModeSelector.kt] - Spoofing Mode Toggle
// Allows user to choose between Standard (Mock) and Root (Undetectable) modes.
// Shows setup instructions for mock location, prompts root check when Root clicked.

package com.relocate.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relocate.app.data.SpoofMode
import com.relocate.app.ui.theme.*

@Composable
fun ModeSelector(
    currentMode: SpoofMode,
    onModeChange: (SpoofMode) -> Unit,
    isRootAvailable: Boolean,
    onRootCheckRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        Text(
            text = "Spoofing Mode",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Standard Mode Card
            ModeCard(
                modifier = Modifier.weight(1f),
                title = "Standard",
                subtitle = "Mock Location",
                emoji = "📱",
                isSelected = currentMode == SpoofMode.MOCK,
                warningText = "⚠️ Detectable",
                warningColor = Amber,
                onClick = { onModeChange(SpoofMode.MOCK) }
            )

            // Root Mode Card — always clickable, will trigger root check
            ModeCard(
                modifier = Modifier.weight(1f),
                title = "Root",
                subtitle = "Undetectable",
                emoji = "🔓",
                isSelected = currentMode == SpoofMode.ROOT,
                warningText = if (isRootAvailable) "✅ SU Available" else "🔍 Tap to Check",
                warningColor = if (isRootAvailable) Green else Amber,
                onClick = {
                    if (isRootAvailable) {
                        onModeChange(SpoofMode.ROOT)
                    } else {
                        // Trigger root check — will request SU permission
                        onRootCheckRequested()
                    }
                },
                enabled = true // Always enabled — clicking checks root
            )
        }

        // ── Mock Location Setup Guide (visible only when Standard mode selected) ──
        AnimatedVisibility(visible = currentMode == SpoofMode.MOCK) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Amber.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "⚙️ Required Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Amber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1. Enable Developer Options\n2. Go to \"Select Mock Location App\"\n3. Choose \"Relocate\"",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                )
                            } catch (e: Exception) {
                                // Some devices use a different intent
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                } catch (_: Exception) { }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Developer Settings", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    emoji: String,
    isSelected: Boolean,
    warningText: String,
    warningColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val borderColor by animateColorAsState(
        if (isSelected) Amber else MaterialTheme.colorScheme.outline,
        label = "border"
    )
    val bgColor by animateColorAsState(
        if (isSelected) AmberDim else Color.Transparent,
        label = "bg"
    )

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (enabled) borderColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) bgColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = warningText,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = warningColor
            )
        }
    }
}
