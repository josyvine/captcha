package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.LogEntry
import com.example.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX_LOGS = 1000
    private var logFile: File? = null
    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        try {
            val appCtx = context.applicationContext ?: context
            prefs = appCtx.getSharedPreferences("app_persistent_logs", Context.MODE_PRIVATE)
            logFile = File(appCtx.filesDir, "terminal_logs.txt")
            
            val loadedLogs = mutableListOf<LogEntry>()
            if (logFile?.exists() == true) {
                logFile?.forEachLine { line ->
                    if (line.isNotBlank()) {
                        val entry = parseLogLine(line)
                        if (entry != null) {
                            loadedLogs.add(entry)
                        }
                    }
                }
            }

            if (loadedLogs.isEmpty()) {
                val initEntry = LogEntry(
                    timestamp = timeFormat.format(Date()),
                    tag = "SYSTEM",
                    message = "2Captcha Autonomous Visual Solver initialized. Ready.",
                    level = LogLevel.SYSTEM
                )
                loadedLogs.add(initEntry)
                appendToFile(initEntry)
            } else {
                val resumeEntry = LogEntry(
                    timestamp = timeFormat.format(Date()),
                    tag = "SYSTEM",
                    message = "Application session active. Preserved ${loadedLogs.size} historical logs.",
                    level = LogLevel.SYSTEM
                )
                loadedLogs.add(resumeEntry)
                appendToFile(resumeEntry)
            }

            while (loadedLogs.size > MAX_LOGS) {
                loadedLogs.removeAt(0)
            }
            _logs.value = loadedLogs
        } catch (e: Throwable) {
            Log.e("Logger", "Failed to init persistent logs: ${e.message}")
        }
    }

    @Synchronized
    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            tag = tag,
            message = message,
            level = level
        )
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_LOGS) {
            current.removeAt(0)
        }
        _logs.value = current
        appendToFile(entry)

        if (level == LogLevel.ERROR) {
            try {
                prefs?.edit()?.putString("last_error", "[${entry.timestamp}] [${entry.tag}] ${entry.message}")?.apply()
            } catch (_: Throwable) {}
        }
    }

    @Synchronized
    private fun appendToFile(entry: LogEntry) {
        try {
            val file = logFile ?: return
            val raw = "${entry.timestamp}|${entry.level.name}|${entry.tag}|${entry.message.replace("\n", "\\n")}\n"
            file.appendText(raw)
        } catch (e: Throwable) {
            Log.e("Logger", "Failed to append log to file: ${e.message}")
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        val level = try {
            LogLevel.valueOf(parts[1])
        } catch (_: Throwable) {
            LogLevel.INFO
        }
        return LogEntry(
            timestamp = parts[0],
            level = level,
            tag = parts[2],
            message = parts[3].replace("\\n", "\n")
        )
    }

    @Synchronized
    fun clear() {
        val clearEntry = LogEntry(
            timestamp = timeFormat.format(Date()),
            tag = "SYSTEM",
            message = "Terminal logs cleared by user.",
            level = LogLevel.SYSTEM
        )
        try {
            logFile?.writeText("")
            appendToFile(clearEntry)
        } catch (_: Throwable) {}
        _logs.value = listOf(clearEntry)
    }

    fun copyToClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val sb = StringBuilder()
        _logs.value.forEach { entry ->
            sb.append("[${entry.timestamp}] [${entry.level.name}] [${entry.tag}] ${entry.message}\n")
        }
        val clip = ClipData.newPlainText("2Captcha Solver Logs", sb.toString())
        clipboard.setPrimaryClip(clip)
    }
}


