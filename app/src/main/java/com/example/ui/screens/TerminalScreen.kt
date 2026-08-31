package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LogLevel
import com.example.ui.theme.*
import com.example.util.Logger

data class LogFilterItem(
    val level: LogLevel?,
    val name: String,
    val icon: ImageVector,
    val tintColor: Color
)

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val allLogs by Logger.logs.collectAsState()

    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(allLogs, selectedFilter) {
        if (selectedFilter == null) allLogs else allLogs.filter { it.level == selectedFilter }
    }

    val filterOptions = remember {
        listOf(
            LogFilterItem(null, "All", Icons.Default.FilterList, CyanGlow),
            LogFilterItem(LogLevel.NETWORK, "Network", Icons.Default.Wifi, ElectricBlue),
            LogFilterItem(LogLevel.VISION, "Vision", Icons.Default.Visibility, NeonGreen),
            LogFilterItem(LogLevel.ACCESSIBILITY, "Touch", Icons.Default.TouchApp, NeonAmber),
            LogFilterItem(LogLevel.LEARNING, "Learned", Icons.Default.AutoAwesome, NeonPurple),
            LogFilterItem(LogLevel.SYSTEM, "System", Icons.Default.Dns, CyanGlow),
            LogFilterItem(LogLevel.ERROR, "Errors", Icons.Default.ErrorOutline, NeonRed)
        )
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Top Bar: Title, Count, Filters, and Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(18.dp))
                Text(
                    text = "TERMINAL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = PrimaryContainerDark
                ) {
                    Text(
                        text = "${filteredLogs.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Copy & Clear Quick Icon Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { Logger.copyToClipboard(context) },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryContainerDark)
                        .testTag("copy_logs_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = CyanGlow, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { Logger.clear() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .testTag("clear_logs_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = NeonRed, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Compact Icon-Based Category Filter Row (Replaces space-consuming text buttons with Google Icons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterOptions.forEach { filterItem ->
                val isSelected = selectedFilter == filterItem.level

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            selectedFilter = if (isSelected && filterItem.level != null) null else filterItem.level
                        },
                    color = if (isSelected) PrimaryContainerDark else Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) filterItem.tintColor else CardBorder
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = filterItem.icon,
                            contentDescription = filterItem.name,
                            tint = if (isSelected) filterItem.tintColor else TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // Expanded Terminal Console Container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF010409),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyanGlow.copy(alpha = 0.35f))
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No logs recorded for selected filter",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val levelColor = when (log.level) {
                            LogLevel.SYSTEM -> CyanGlow
                            LogLevel.NETWORK -> ElectricBlue
                            LogLevel.VISION -> NeonGreen
                            LogLevel.ACCESSIBILITY -> NeonAmber
                            LogLevel.LEARNING -> NeonPurple
                            LogLevel.ERROR -> NeonRed
                            LogLevel.INFO -> TextSecondary
                        }

                        val levelIcon = when (log.level) {
                            LogLevel.SYSTEM -> Icons.Default.Dns
                            LogLevel.NETWORK -> Icons.Default.Wifi
                            LogLevel.VISION -> Icons.Default.Visibility
                            LogLevel.ACCESSIBILITY -> Icons.Default.TouchApp
                            LogLevel.LEARNING -> Icons.Default.AutoAwesome
                            LogLevel.ERROR -> Icons.Default.ErrorOutline
                            LogLevel.INFO -> Icons.Default.Info
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = levelIcon,
                                contentDescription = null,
                                tint = levelColor,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(top = 2.dp)
                            )

                            Text(
                                text = log.timestamp,
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = "[${log.tag}]",
                                color = levelColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = log.message,
                                color = Color(0xFFF1F5F9),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
