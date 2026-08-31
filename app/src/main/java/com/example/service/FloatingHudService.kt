package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.model.HudStatus
import com.example.model.LogLevel
import com.example.ui.theme.*
import com.example.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingHudService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_hud_service_channel"

        var isRunning = false
            private set

        // Observables for HUD updates
        val hudStatus = MutableStateFlow(HudStatus.STANDBY)
        val aiOutput = MutableStateFlow("Ready for Capture")
        val currentLatency = MutableStateFlow(0L)
        val marqueeLog = MutableStateFlow("2Captcha Autonomous Solver Ready")
        val targetSnapshot = MutableStateFlow<Bitmap?>(null)
        val isPaused = MutableStateFlow(false)
        val isAutoSolveEnabled = MutableStateFlow(false)

        // Action callback triggers
        var onTriggerSolveRequested: (() -> Unit)? = null
        var onEmergencyPauseToggled: ((Boolean) -> Unit)? = null
        var onAutoSolveToggled: ((Boolean) -> Unit)? = null

        fun start(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingHudService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        isRunning = true

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initFloatingOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        Logger.log("SYSTEM", "Floating HUD Overlay Window rendered on display.", LogLevel.SYSTEM)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "2Captcha Floating HUD Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active floating HUD overlay for visual 2Captcha automation"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("2Captcha Autonomous Solver")
            .setContentText("Floating HUD Overlay Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun initFloatingOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        val wmParams = layoutParams
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingHudService)
            setViewTreeSavedStateRegistryOwner(this@FloatingHudService)
            setContent {
                MyApplicationTheme {
                    FloatingHudContent(
                        onDrag = { dx, dy ->
                            wmParams.x += dx.toInt()
                            wmParams.y += dy.toInt()
                            try {
                                windowManager.updateViewLayout(floatingView, wmParams)
                            } catch (_: Exception) {}
                        },
                        onClose = {
                            stopSelf()
                        }
                    )
                }
            }
        }

        floatingView = composeView
        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            Logger.log("SYSTEM", "Failed to add floating window: ${e.message}", LogLevel.ERROR)
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (_: Exception) {}
            floatingView = null
        }
        isRunning = false
        Logger.log("SYSTEM", "Floating HUD Overlay Service terminated.", LogLevel.SYSTEM)
        super.onDestroy()
    }
}

@Composable
fun FloatingHudContent(
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit
) {
    var isCollapsed by remember { mutableStateOf(false) }
    val status by FloatingHudService.hudStatus.collectAsState()
    val predictedText by FloatingHudService.aiOutput.collectAsState()
    val latency by FloatingHudService.currentLatency.collectAsState()
    val marquee by FloatingHudService.marqueeLog.collectAsState()
    val snapshot by FloatingHudService.targetSnapshot.collectAsState()
    val paused by FloatingHudService.isPaused.collectAsState()
    val isAutoSolve by FloatingHudService.isAutoSolveEnabled.collectAsState()

    if (isCollapsed) {
        // Compact Draggable Bubble Mode
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.95f))
                .border(2.dp, CyanGlow, CircleShape)
                .clickable { isCollapsed = false },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Expand HUD",
                    tint = if (paused) NeonRed else CyanGlow,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (paused) "PAUSE" else "2CAPT",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        // Full Draggable HUD Card
        Card(
            modifier = Modifier
                .width(280.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16).copy(alpha = 0.96f)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanGlow.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(status.badgeColorHex))
                        )
                        Text(
                            text = status.label,
                            color = Color(status.badgeColorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { isCollapsed = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Minimize",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = NeonRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Scraped Target Preview Thumbnail
                if (snapshot != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF020617))
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = snapshot!!.asImageBitmap(),
                            contentDescription = "Target Frame",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // AI Output Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF020617))
                        .border(1.dp, CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "AI OUTPUT / PREDICTION",
                            fontSize = 9.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = predictedText,
                            fontSize = 14.sp,
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Latency & Marquee Log
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RTT: ${latency}ms",
                        fontSize = 10.sp,
                        color = CyanGlow,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = marquee,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                    )
                }

                // Action Controls: Auto-Solve Loop + Single Solve + Pause
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val nextAuto = !isAutoSolve
                            FloatingHudService.isAutoSolveEnabled.value = nextAuto
                            FloatingHudService.onAutoSolveToggled?.invoke(nextAuto)
                        },
                        modifier = Modifier.weight(1.1f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoSolve) NeonGreen else Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoSolve) Icons.Default.AllInclusive else Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (isAutoSolve) Color.Black else CyanGlow
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isAutoSolve) "AUTO: ON" else "AUTO: OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAutoSolve) Color.Black else Color.White
                        )
                    }

                    Button(
                        onClick = { FloatingHudService.onTriggerSolveRequested?.invoke() },
                        modifier = Modifier.weight(0.9f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Solve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val next = !paused
                            FloatingHudService.isPaused.value = next
                            FloatingHudService.onEmergencyPauseToggled?.invoke(next)
                        },
                        modifier = Modifier.weight(0.9f).height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (paused) NeonGreen else NeonRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (paused) "Resume" else "Pause",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
