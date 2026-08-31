package com.example.engine

import com.example.data.PreferencesManager
import com.example.model.LogLevel
import com.example.service.CaptchaAccessibilityService
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SelfLearningEngine(
    private val visionEngine: GeminiVisionEngine,
    private val prefs: PreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Wire up listener to accessibility service
        CaptchaAccessibilityService.onErrorCorrectionDetected = { wrongGuess, correctAnswer, directive ->
            handleErrorCorrection(wrongGuess, correctAnswer, directive)
        }
    }

    /**
     * Handles detected red error correction banner
     */
    fun handleErrorCorrection(
        wrongGuess: String,
        correctAnswer: String,
        directive: String = "2Captcha Visual"
    ) {
        scope.launch {
            Logger.log("LEARNING", "Autonomous Self-Learning triggered for correction: '$correctAnswer'", LogLevel.LEARNING)

            // 1. Instant Auto-Recovery: Humanized Typing & Submission of the correct answer
            val accessibility = CaptchaAccessibilityService.instance
            if (accessibility != null) {
                accessibility.performOrganicTyping(
                    textToType = correctAnswer,
                    targetInputBounds = null,
                    telemetry = prefs.getHumanTelemetry()
                ) {
                    Logger.log("ACCESSIBILITY", "Auto-recovery submission executed for '$correctAnswer'.", LogLevel.ACCESSIBILITY)
                }
            }

            // 2. Meta-Reflection in background
            val apiKey = prefs.apiKey
            val model = prefs.selectedModel

            val result = visionEngine.generateMetaReflectionRule(
                wrongGuess = wrongGuess,
                correctAnswer = correctAnswer,
                directive = directive,
                apiKey = apiKey,
                modelName = model
            )

            result.onSuccess { rule ->
                prefs.addLearnedRule(rule)
                Logger.log("LEARNING", "Persisted new learned lesson (Total: ${prefs.getLearnedRules().size}/15 rules): \"$rule\"", LogLevel.LEARNING)
            }.onFailure { err ->
                Logger.log("LEARNING", "Reflection rule generation skipped: ${err.message}", LogLevel.ERROR)
            }
        }
    }
}
