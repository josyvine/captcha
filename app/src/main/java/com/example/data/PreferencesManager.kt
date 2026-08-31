package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.model.HumanTelemetryConfig
import org.json.JSONArray

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("capt_solver_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_TARGET_URL = "target_url"
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_LEARNED_RULES = "learned_rules_json"

        // Human Telemetry keys
        private const val KEY_KEYSTROKE_MIN = "keystroke_min"
        private const val KEY_KEYSTROKE_MAX = "keystroke_max"
        private const val KEY_HESITATION_MIN = "hesitation_min"
        private const val KEY_HESITATION_MAX = "hesitation_max"
        private const val KEY_HESITATION_PROB = "hesitation_prob"
        private const val KEY_TYPO_PROB = "typo_prob"
        private const val KEY_TOUCH_HOLD_MIN = "touch_hold_min"
        private const val KEY_TOUCH_HOLD_MAX = "touch_hold_max"

        // Stats
        private const val KEY_TASKS_SOLVED = "tasks_solved"
        private const val KEY_TOTAL_ATTEMPTS = "total_attempts"
        private const val KEY_LATENCY_SUM = "latency_sum"
    }

    var apiKey: String
        get() {
            val saved = prefs.getString(KEY_API_KEY, "") ?: ""
            if (saved.isNotEmpty()) return saved
            // Fallback to BuildConfig if present and valid
            val buildKey = BuildConfig.GEMINI_API_KEY
            return if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        }
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var selectedModel: String
        get() = prefs.getString(KEY_SELECTED_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()

    var selectedVoice: String
        get() = prefs.getString(KEY_SELECTED_VOICE, "Puck") ?: "Puck"
        set(value) = prefs.edit().putString(KEY_SELECTED_VOICE, value).apply()

    var targetUrl: String
        get() = prefs.getString(KEY_TARGET_URL, "https://2captcha.com/play-and-earn") ?: "https://2captcha.com/play-and-earn"
        set(value) = prefs.edit().putString(KEY_TARGET_URL, value).apply()

    var engineMode: String
        get() = prefs.getString(KEY_ENGINE_MODE, "REST_VISION") ?: "REST_VISION"
        set(value) = prefs.edit().putString(KEY_ENGINE_MODE, value).apply()

    fun getHumanTelemetry(): HumanTelemetryConfig {
        return HumanTelemetryConfig(
            minKeystrokeMs = prefs.getInt(KEY_KEYSTROKE_MIN, 180),
            maxKeystrokeMs = prefs.getInt(KEY_KEYSTROKE_MAX, 320),
            hesitationMinMs = prefs.getInt(KEY_HESITATION_MIN, 150),
            hesitationMaxMs = prefs.getInt(KEY_HESITATION_MAX, 350),
            hesitationProbability = prefs.getFloat(KEY_HESITATION_PROB, 0.40f),
            typoProbability = prefs.getFloat(KEY_TYPO_PROB, 0.03f),
            touchHoldMinMs = prefs.getLong(KEY_TOUCH_HOLD_MIN, 20L),
            touchHoldMaxMs = prefs.getLong(KEY_TOUCH_HOLD_MAX, 50L)
        )
    }

    fun saveHumanTelemetry(config: HumanTelemetryConfig) {
        prefs.edit()
            .putInt(KEY_KEYSTROKE_MIN, config.minKeystrokeMs)
            .putInt(KEY_KEYSTROKE_MAX, config.maxKeystrokeMs)
            .putInt(KEY_HESITATION_MIN, config.hesitationMinMs)
            .putInt(KEY_HESITATION_MAX, config.hesitationMaxMs)
            .putFloat(KEY_HESITATION_PROB, config.hesitationProbability)
            .putFloat(KEY_TYPO_PROB, config.typoProbability)
            .putLong(KEY_TOUCH_HOLD_MIN, config.touchHoldMinMs)
            .putLong(KEY_TOUCH_HOLD_MAX, config.touchHoldMaxMs)
            .apply()
    }

    fun getLearnedRules(): List<String> {
        val json = prefs.getString(KEY_LEARNED_RULES, "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun addLearnedRule(rule: String) {
        val current = getLearnedRules().toMutableList()
        if (rule.isNotBlank() && !current.contains(rule)) {
            current.add(0, rule.trim())
            // Capped at 15 rules as specified in prompt
            if (current.size > 15) {
                current.removeAt(current.size - 1)
            }
            saveLearnedRules(current)
        }
    }

    fun removeLearnedRule(index: Int) {
        val current = getLearnedRules().toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            saveLearnedRules(current)
        }
    }

    fun clearLearnedRules() {
        saveLearnedRules(emptyList())
    }

    private fun saveLearnedRules(rules: List<String>) {
        val array = JSONArray()
        rules.forEach { array.put(it) }
        prefs.edit().putString(KEY_LEARNED_RULES, array.toString()).apply()
    }

    fun getTasksSolved(): Int = prefs.getInt(KEY_TASKS_SOLVED, 0)
    fun getTotalAttempts(): Int = prefs.getInt(KEY_TOTAL_ATTEMPTS, 0)
    fun getLatencySum(): Long = prefs.getLong(KEY_LATENCY_SUM, 0L)

    fun recordAttempt(solved: Boolean, latencyMs: Long) {
        val currentSolved = getTasksSolved() + (if (solved) 1 else 0)
        val currentAttempts = getTotalAttempts() + 1
        val currentLatencySum = getLatencySum() + latencyMs

        prefs.edit()
            .putInt(KEY_TASKS_SOLVED, currentSolved)
            .putInt(KEY_TOTAL_ATTEMPTS, currentAttempts)
            .putLong(KEY_LATENCY_SUM, currentLatencySum)
            .apply()
    }

    fun resetStats() {
        prefs.edit()
            .putInt(KEY_TASKS_SOLVED, 0)
            .putInt(KEY_TOTAL_ATTEMPTS, 0)
            .putLong(KEY_LATENCY_SUM, 0L)
            .apply()
    }

    fun saveCrashReport(report: String) {
        prefs.edit().putString("last_crash_report", report).apply()
    }

    fun getLastCrashReport(): String? {
        return prefs.getString("last_crash_report", null)
    }

    fun clearCrashReport() {
        prefs.edit().remove("last_crash_report").apply()
    }
}
