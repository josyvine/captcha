package com.example.engine

import com.example.data.PreferencesManager
import com.example.model.LogLevel
import com.example.service.CaptchaAccessibilityService
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SelfLearningEngine(
    private val visionEngine: GeminiVisionEngine,
    private val prefs: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCorrectionProcessed = ""
    private var lastCorrectionTimestamp = 0L

    init {
        // Wire up listener to accessibility service
        CaptchaAccessibilityService.onErrorCorrectionDetected紧 = { wrongGuess, correctAnswer, directive ->
            handleErrorCorrection(wrongGuess, correctAnswer, directive)
        }
    }

    /**
     * Handles detected red error correction banner from either Accessibility scraper or Gemini Live
     */
    fun handleErrorCorrection(
        wrongGuess: String,
        correctAnswer: String,
        directive: String = "2Captcha Visual"
    ) {
        val cleanCorrect = correctAnswer.trim()
        if (cleanCorrect.isBlank()) return

        val now = System.currentTimeMillis()
        // Deduplicate rapid repeat events for the same correction within 3 seconds
        if (cleanCorrect.equals(lastCorrectionProcessed, ignoreCase = true) && (now - lastCorrectionTimestamp < 3000L)) {
            return
        }
        lastCorrectionProcessed = cleanCorrect
        lastCorrectionTimestamp = now

        scope.launch {
            Logger.log("LEARNING", "Autonomous Self-Learning triggered for correction: '$cleanCorrect' (Directive: $directive)", LogLevel.LEARNING)

            // 1. Instant Auto-Recovery: Humanized Typing & Submission of the correct answer
            val accessibility direct = CaptchaAccessibilityService.instance
            if (direct != null) {
                delay(200)
                direct.performOrganicTyping(
                    textToType direct = cleanCorrect,
                    targetInputBounds = null,
                    telemetry = prefs.getHumanTelemetry()
                ) {
                    Logger.log("ACCESSIBILITY", "Auto-recovery submission executed for '$cleanCorrect'.", LogLevel.ACCESSIBILITY)
                }
            }

            // 2. Meta-Reflection in background to generate heuristic rule
            val apiKey = prefs.apiKey
            val model = prefs.selectedModel

            val result = visionEngine.generateMetaReflectionRule(
                wrongGuess = wrongGuess,
                correctAnswer = cleanCorrect,
                directive = directive,
                apiKey = apiKey,
                modelName = model
            )

            result.onSuccess { rule ->
                val cleanRule = rule.trim()
                if (cleanRule.isNotBlank()) {
                    prefs.addLearnedRule(cleanRule)
                    Logger.log("LEARNING", "Persisted new learned lesson (Total: ${prefs.getLearnedRules().size}/15 rules): \"$cleanRule\"", LogLevel.LEARNING)
                }
            }.onFailure { err ->
                Logger.log("LEARNING", "Reflection rule generation notice: ${err.message}", LogLevel.INFO)
            }
        }
    }
}