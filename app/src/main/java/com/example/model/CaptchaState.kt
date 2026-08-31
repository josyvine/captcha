package com.example.model

import android.graphics.Bitmap

enum class EngineMode(val title: String, val badge: String) {
    REST_VISION("REST Vision Mode", "⚡ REST Fast"),
    GEMINI_LIVE("Gemini Live Stream", "🔴 Live WebSocket")
}

enum class HudStatus(val label: String, val badgeColorHex: Long) {
    STANDBY("STANDBY", 0xFF94A3B8),
    CAPTURING("CAPTURING", 0xFF0284C7),
    THINKING("THINKING", 0xFF00F0FF),
    TYPING("TYPING", 0xFF00FF66),
    SUBMITTING("SUBMITTING", 0xFFFFB800),
    LEARNING("🧠 LEARNING", 0xFFA855F7),
    RETRYING("RETRYING", 0xFFFF3366),
    PAUSED("PAUSED (SAFE)", 0xFFFF3366)
}

enum class LogLevel {
    INFO,
    SYSTEM,
    NETWORK,
    VISION,
    ACCESSIBILITY,
    LEARNING,
    ERROR
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

data class TelemetryStats(
    val tasksSolved: Int = 0,
    val totalAttempts: Int = 0,
    val successRate: Float = 100.0f,
    val avgLatencyMs: Long = 0L,
    val activeModel: String = "gemini-3.5-flash",
    val sessionSeconds: Long = 0L
)

data class HumanTelemetryConfig(
    val minKeystrokeMs: Int = 180,
    val maxKeystrokeMs: Int = 320,
    val hesitationMinMs: Int = 150,
    val hesitationMaxMs: Int = 350,
    val hesitationProbability: Float = 0.40f, // 40% probability
    val typoProbability: Float = 0.03f,       // 3% probability
    val touchHoldMinMs: Long = 20L,           // 20ms glass pressure dwell
    val touchHoldMaxMs: Long = 50L            // 50ms glass pressure dwell
)

data class CaptchaSolution(
    val rawAnswer: String,
    val cleanAnswer: String,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val clickTargetX: Float? = null,
    val clickTargetY: Float? = null,
    val snapshot: Bitmap? = null
)
