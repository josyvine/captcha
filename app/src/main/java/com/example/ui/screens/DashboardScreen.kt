package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EngineMode
import com.example.model.HudStatus
import com.example.service.CaptchaAccessibilityService
import com.example.service.FloatingHudService
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onRequestMediaProjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val targetUrl by viewModel.targetUrl.collectAsState()
    val engineMode by viewModel.engineMode.collectAsState()
    val stats by viewModel.telemetryStats.collectAsState()
    val currentFrame by viewModel.currentFrame.collectAsState()
    val isSolving by viewModel.isSolving.collectAsState()
    val isAutoSolveActive by viewModel.isAutoSolveActive.collectAsState()
    val isMediaProjectionAuthorized by viewModel.isMediaProjectionAuthorized.collectAsState()
    val hudStatus by FloatingHudService.hudStatus.collectAsState()
    val aiOutput by FloatingHudService.aiOutput.collectAsState()
    val currentLatency by FloatingHudService.currentLatency.collectAsState()

    var isAccessibilityEnabled by remember { mutableStateOf(CaptchaAccessibilityService.isRunning()) }
    var isOverlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Periodic check for permission status
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = CaptchaAccessibilityService.isRunning()
            isOverlayPermissionGranted = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(1500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Brand Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (FloatingHudService.isRunning) NeonGreen else TextSecondary)
                    )
                    Text(
                        text = "2CAPTCHA SOLVER",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Autonomous Visual AI Engine",
                    fontSize = 12.sp,
                    color = CyanGlow
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                    Text(
                        text = "v2.5 PRO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }
            }
        }

        // Screen Capture Authorization Warning Banner (if not active)
        if (!isMediaProjectionAuthorized) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1B4B),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonAmber)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SCREEN CAPTURE NOT AUTHORIZED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap 'Authorize' to grant GPU screen capture so the AI reads your live browser screen directly instead of the fallback template.",
                            color = NeonAmber,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Button(
                        onClick = onRequestMediaProjection,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Authorize", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Engine Mode Switcher (Clean, non-truncating with Google Material Icons)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(16.dp))
                    Text(
                        text = "ENGINE OPERATION MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EngineMode.entries.forEach { mode ->
                        val isSelected = engineMode == mode
                        val modeIcon = if (mode == EngineMode.REST_VISION) Icons.Default.Bolt else Icons.Default.GraphicEq
                        val modeLabel = if (mode == EngineMode.REST_VISION) "REST Fast" else "Gemini Live"

                        Button(
                            onClick = { viewModel.setEngineMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("engine_mode_${mode.name.lowercase()}"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) PrimaryContainerDark else Color(0xFF1E293B),
                                contentColor = if (isSelected) CyanGlow else TextSecondary
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, CyanGlow) else androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) (if (mode == EngineMode.GEMINI_LIVE) NeonGreen else CyanGlow) else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = modeLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Target URL Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(16.dp))
                        Text(
                            text = "TARGET 2CAPTCHA URL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                    TextButton(
                        onClick = { viewModel.setTargetUrl("https://2captcha.com/play-and-earn") },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Reset Default", fontSize = 11.sp, color = CyanGlow)
                    }
                }

                OutlinedTextField(
                    value = targetUrl,
                    onValueChange = { viewModel.setTargetUrl(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("target_url_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Http, contentDescription = null, tint = CyanGlow)
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", tint = TextSecondary)
                        }
                    }
                )
            }
        }

        // System Services & Permissions Health
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(16.dp))
                    Text(
                        text = "SYSTEM HARDWARE & PERMISSIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }

                // 1. Accessibility Service
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isAccessibilityEnabled) NeonGreen else NeonAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Accessibility Touch & Node", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (isAccessibilityEnabled) "Active (Hardware isTrusted=true gestures)" else "Action Required (Tap to Enable)",
                                fontSize = 11.sp,
                                color = if (isAccessibilityEnabled) NeonGreen else NeonAmber
                            )
                        }
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAccessibilityEnabled) PrimaryContainerDark else NeonAmber
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("enable_accessibility_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isAccessibilityEnabled) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isAccessibilityEnabled) "Active" else "Enable",
                                fontSize = 11.sp,
                                color = if (isAccessibilityEnabled) CyanGlow else Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 2. Floating HUD Overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (FloatingHudService.isRunning) Icons.Default.CheckCircle else Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (FloatingHudService.isRunning) NeonGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Floating HUD Overlay", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (FloatingHudService.isRunning) "Running over target apps" else "Stopped (Tap to Start)",
                                fontSize = 11.sp,
                                color = if (FloatingHudService.isRunning) NeonGreen else TextSecondary
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (!isOverlayPermissionGranted) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                if (FloatingHudService.isRunning) {
                                    FloatingHudService.stop(context)
                                } else {
                                    FloatingHudService.start(context)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (FloatingHudService.isRunning) NeonRed else ElectricBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("toggle_floating_hud_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (FloatingHudService.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!isOverlayPermissionGranted) "Grant" else if (FloatingHudService.isRunning) "Stop HUD" else "Start HUD",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 3. Screen Capture Projection (Reactive to authorization & accessibility)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isMediaProjectionAuthorized) Icons.Default.CheckCircle else Icons.Default.Screenshot,
                            contentDescription = null,
                            tint = if (isMediaProjectionAuthorized) NeonGreen else CyanGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Screen Capture Engine", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (isMediaProjectionAuthorized) "Active (Hardware Screen Streaming)" else "Tap to Refresh / Authorize Frame",
                                fontSize = 11.sp,
                                color = if (isMediaProjectionAuthorized) NeonGreen else CyanGlow
                            )
                        }
                    }
                    Button(
                        onClick = onRequestMediaProjection,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMediaProjectionAuthorized) PrimaryContainerDark else ElectricBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("init_screen_capture_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMediaProjectionAuthorized) Icons.Default.Check else Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (isMediaProjectionAuthorized) NeonGreen else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isMediaProjectionAuthorized) "Active" else "Authorize",
                                fontSize = 11.sp,
                                color = if (isMediaProjectionAuthorized) NeonGreen else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Telemetry Stats Dashboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME TELEMETRY METRICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "Session: ${stats.sessionSeconds}s",
                        fontSize = 11.sp,
                        color = CyanGlow,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tasks Solved
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF020617),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("SOLVED", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${stats.tasksSolved}/${stats.totalAttempts}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }
                    }

                    // Success Rate
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF020617),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("ACCURACY", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f%%", stats.successRate),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanGlow
                            )
                        }
                    }

                    // Latency
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF020617),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("AVG LATENCY", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${stats.avgLatencyMs}ms",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonAmber,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Live Viewport Frame Inspection & Solve Trigger
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE VIEWPORT FRAME INSPECTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { viewModel.refreshCurrentFrame() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Frame", tint = CyanGlow, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Frame Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF020617))
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentFrame != null) {
                        Image(
                            bitmap = currentFrame!!.asImageBitmap(),
                            contentDescription = "Viewport Frame",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("No Frame Captured", color = TextSecondary, fontSize = 12.sp)
                    }

                    // Overlay HUD Badge & Frame Source Indicator
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF090D16).copy(alpha = 0.90f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(hudStatus.badgeColorHex))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(hudStatus.badgeColorHex))
                                        .testTag("hud_status_indicator")
                                )
                                Text(
                                    text = hudStatus.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(hudStatus.badgeColorHex)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF090D16).copy(alpha = 0.90f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isMediaProjectionAuthorized) NeonGreen else NeonAmber
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMediaProjectionAuthorized) Icons.Default.Tv else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isMediaProjectionAuthorized) NeonGreen else NeonAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isMediaProjectionAuthorized) "LIVE SCREEN" else "TEST CANVAS (TAP AUTHORIZE)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMediaProjectionAuthorized) NeonGreen else NeonAmber
                                )
                            }
                        }
                    }
                }

                // AI Prediction Result Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF020617),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanGlow.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("EXTRACTED SOLUTION", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = aiOutput,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (currentLatency > 0) {
                            Text(
                                text = "${currentLatency}ms RTT",
                                fontSize = 11.sp,
                                color = CyanGlow,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Action Buttons: Auto-Solve Loop + Single Solve
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleAutoSolve(!isAutoSolveActive) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("toggle_auto_solve_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoSolveActive) NeonGreen else Color(0xFF1E293B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isAutoSolveActive) NeonGreen else CardBorder
                        )
                    ) {
                        Icon(
                            imageVector = if (isAutoSolveActive) Icons.Default.AllInclusive else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = if (isAutoSolveActive) Color.Black else CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAutoSolveActive) "AUTO LOOP: ON" else "AUTO LOOP: OFF",
                            color = if (isAutoSolveActive) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.triggerAutonomousSolve() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("trigger_solve_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                        enabled = !isSolving
                    ) {
                        if (isSolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Solving...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOLVE NOW",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
