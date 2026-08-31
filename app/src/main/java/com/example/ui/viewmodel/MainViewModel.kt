package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.engine.GeminiVisionEngine
import com.example.engine.SelfLearningEngine
import com.example.model.*
import com.example.service.CaptchaAccessibilityService
import com.example.service.FloatingHudService
import com.example.service.ScreenCaptureManager
import com.example.service.ScreenCaptureService
import com.example.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val visionEngine = GeminiVisionEngine()
    private val learningEngine = SelfLearningEngine(visionEngine, prefs)

    var screenCaptureManager: ScreenCaptureManager? = null

    // UI States
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _engineMode = MutableStateFlow(EngineMode.REST_VISION)
    val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

    private val _targetUrl = MutableStateFlow(prefs.targetUrl)
    val targetUrl: StateFlow<String> = _targetUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.selectedModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _selectedVoice = MutableStateFlow(prefs.selectedVoice)
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _humanTelemetry = MutableStateFlow(prefs.getHumanTelemetry())
    val humanTelemetry: StateFlow<HumanTelemetryConfig> = _humanTelemetry.asStateFlow()

    private val _learnedRules = MutableStateFlow(prefs.getLearnedRules())
    val learnedRules: StateFlow<List<String>> = _learnedRules.asStateFlow()

    private val _telemetryStats = MutableStateFlow(
        TelemetryStats(
            tasksSolved = prefs.getTasksSolved(),
            totalAttempts = prefs.getTotalAttempts(),
            avgLatencyMs = if (prefs.getTotalAttempts() > 0) prefs.getLatencySum() / prefs.getTotalAttempts() else 0L,
            successRate = if (prefs.getTotalAttempts() > 0) (prefs.getTasksSolved().toFloat() / prefs.getTotalAttempts()) * 100f else 100f,
            activeModel = prefs.selectedModel
        )
    )
    val telemetryStats: StateFlow<TelemetryStats> = _telemetryStats.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(
        listOf(
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview",
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025",
            "gemini-2.0-flash-exp"
        )
    )
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _isMediaProjectionAuthorized = MutableStateFlow(false)
    val isMediaProjectionAuthorized: StateFlow<Boolean> = _isMediaProjectionAuthorized.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _currentSolution = MutableStateFlow<CaptchaSolution?>(null)
    val currentSolution: StateFlow<CaptchaSolution?> = _currentSolution.asStateFlow()

    private val _isSolving = MutableStateFlow(false)
    val isSolving: StateFlow<Boolean> = _isSolving.asStateFlow()

    private val _isAutoSolveActive = MutableStateFlow(false)
    val isAutoSolveActive: StateFlow<Boolean> = _isAutoSolveActive.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var autoSolveJob: Job? = null
    private var sessionStartTime = SystemClock.elapsedRealtime()

    init {
        // Recover and display previous crash report if any
        prefs.getLastCrashReport()?.let { report ->
            Logger.log("CRASH", "PREVIOUS APP CRASH DETECTED:\n$report", LogLevel.ERROR)
            prefs.clearCrashReport()
        }

        startSessionTimer()
        viewModelScope.launch {
            delay(500)
            refreshCurrentFrame()
        }

        // Auto-enable screen capture readiness if Accessibility is active
        if (CaptchaAccessibilityService.instance != null) {
            _isMediaProjectionAuthorized.value = true
        }

        viewModelScope.launch {
            CaptchaAccessibilityService.isConnected.collect { connected ->
                if (connected) {
                    _isMediaProjectionAuthorized.value = true
                    delay(300)
                    refreshCurrentFrame()
                }
            }
        }

        // Observe ScreenCaptureService state
        viewModelScope.launch {
            ScreenCaptureService.isCapturing.collect { capturing ->
                if (capturing) {
                    _isMediaProjectionAuthorized.value = true
                    delay(300)
                    refreshCurrentFrame()
                }
            }
        }

        // Hook HUD actions
        FloatingHudService.onTriggerSolveRequested = {
            triggerAutonomousSolve()
        }

        FloatingHudService.onAutoSolveToggled = { enabled ->
            toggleAutoSolve(enabled)
        }

        FloatingHudService.onEmergencyPauseToggled = { paused ->
            if (paused) {
                FloatingHudService.hudStatus.value = HudStatus.PAUSED
                Logger.log("SYSTEM", "Emergency Auto-Pause triggered. Session halted to protect account safety.", LogLevel.SYSTEM)
            } else {
                FloatingHudService.hudStatus.value = HudStatus.STANDBY
                Logger.log("SYSTEM", "Session resumed.", LogLevel.SYSTEM)
            }
        }

        // Hook Error correction banner detection
        CaptchaAccessibilityService.onErrorCorrectionDetected = { wrong, correct, directive ->
            viewModelScope.launch {
                learningEngine.handleErrorCorrection(wrong, correct, directive)
                _learnedRules.value = prefs.getLearnedRules()
            }
        }
    }

    fun handleErrorCorrection(wrongGuess: String, correctAnswer: String, directive: String) {
        learningEngine.handleErrorCorrection(wrongGuess, correctAnswer, directive)
        _learnedRules.value = prefs.getLearnedRules()
    }

    private fun startSessionTimer() {
        sessionStartTime = SystemClock.elapsedRealtime()
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val seconds = (SystemClock.elapsedRealtime() - sessionStartTime) / 1000
                _telemetryStats.value = _telemetryStats.value.copy(
                    sessionSeconds = seconds
                )
            }
        }
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun setTargetUrl(url: String) {
        _targetUrl.value = url
        prefs.targetUrl = url
    }

    fun setEngineMode(mode: EngineMode) {
        _engineMode.value = mode
        prefs.engineMode = mode.name
        Logger.log("SYSTEM", "Engine mode switched to: ${mode.title}", LogLevel.SYSTEM)
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun setSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun setSelectedVoice(voice: String) {
        _selectedVoice.value = voice
    }

    fun saveAndLockSettings() {
        prefs.apiKey = _apiKey.value.trim()
        prefs.selectedModel = _selectedModel.value
        prefs.selectedVoice = _selectedVoice.value
        prefs.saveHumanTelemetry(_humanTelemetry.value)

        _telemetryStats.value = _telemetryStats.value.copy(activeModel = _selectedModel.value)
        _toastMessage.value = "Settings locked & saved to Encrypted Storage!"
        Logger.log("SYSTEM", "Model '${_selectedModel.value}' and API configuration locked.", LogLevel.SYSTEM)
    }

    fun updateHumanTelemetry(config: HumanTelemetryConfig) {
        _humanTelemetry.value = config
        prefs.saveHumanTelemetry(config)
    }

    fun fetchModelsFromGoogle() {
        viewModelScope.launch {
            _isFetchingModels.value = true
            val key = _apiKey.value.ifBlank { prefs.apiKey }
            val result = visionEngine.fetchAvailableModels(key)
            _isFetchingModels.value = false

            result.onSuccess { models ->
                if (models.isNotEmpty()) {
                    _availableModels.value = models
                    if (!models.contains(_selectedModel.value)) {
                        _selectedModel.value = models.first()
                    }
                    _toastMessage.value = "Retrieved ${models.size} content-generation models!"
                }
            }.onFailure { err ->
                _toastMessage.value = "Fetch failed: ${err.message}"
            }
        }
    }

    fun setMediaProjectionAuthorized(authorized: Boolean) {
        _isMediaProjectionAuthorized.value = authorized
    }

    suspend fun captureLiveFrame(): Bitmap {
        // Priority 1: Direct OS Hardware Screenshot via Active Accessibility Service (Android 11+, Zero-Prompt, Rock-Solid)
        val accessibility = CaptchaAccessibilityService.instance
        if (accessibility != null) {
            val screenshot = accessibility.captureScreenBitmap()
            if (screenshot != null) return screenshot
        }

        // Priority 2: Dedicated ScreenCaptureService GPU Framebuffer via MediaProjection
        val serviceCapture = ScreenCaptureService.instance
        if (serviceCapture != null) {
            val bmp = serviceCapture.captureFrame()
            if (bmp != null) return bmp
        }

        // Priority 2b: ScreenCaptureManager instance if present
        val capture = screenCaptureManager
        if (capture != null) {
            val bmp = capture.captureFrame()
            if (bmp != null) return bmp
        }

        // Priority 3: Fallback test challenge
        Logger.log("VISION", "Notice: Active screen capture source waiting for frame. Using test canvas.", LogLevel.INFO)
        return ScreenCaptureManager(getApplication()).generateRealisticTestCaptcha()
    }

    fun refreshCurrentFrame() {
        viewModelScope.launch {
            val bmp = captureLiveFrame()
            _currentFrame.value = bmp
            FloatingHudService.targetSnapshot.value = bmp
        }
    }

    fun toggleAutoSolve(enabled: Boolean) {
        _isAutoSolveActive.value = enabled
        FloatingHudService.isAutoSolveEnabled.value = enabled

        autoSolveJob?.cancel()
        if (enabled) {
            Logger.log("SYSTEM", "Autonomous Continuous Solving Loop ACTIVATED.", LogLevel.SYSTEM)
            FloatingHudService.marqueeLog.value = "Autonomous Loop Active"
            autoSolveJob = viewModelScope.launch {
                while (_isAutoSolveActive.value) {
                    if (FloatingHudService.isPaused.value) {
                        delay(1000)
                        continue
                    }

                    suspendCancellableCoroutine<Boolean> { cont ->
                        triggerAutonomousSolve { success ->
                            if (cont.isActive) cont.resume(success)
                        }
                    }

                    // 2.2s breathing room before capturing and solving next captcha
                    delay(2200)
                }
            }
        } else {
            Logger.log("SYSTEM", "Autonomous Continuous Solving Loop STOPPED.", LogLevel.SYSTEM)
            FloatingHudService.marqueeLog.value = "Auto Loop Stopped"
        }
    }

    fun triggerAutonomousSolve(onFinished: ((Boolean) -> Unit)? = null) {
        if (_isSolving.value) {
            onFinished?.invoke(false)
            return
        }

        viewModelScope.launch {
            _isSolving.value = true
            FloatingHudService.hudStatus.value = HudStatus.CAPTURING
            FloatingHudService.marqueeLog.value = "Capturing live screen..."

            val frame = captureLiveFrame()
            _currentFrame.value = frame
            FloatingHudService.targetSnapshot.value = frame

            FloatingHudService.hudStatus.value = HudStatus.THINKING
            FloatingHudService.marqueeLog.value = "Gemini Cognitive Reasoning..."

            val key = _apiKey.value.ifBlank { prefs.apiKey }
            val model = _selectedModel.value
            val rules = _learnedRules.value

            val result = visionEngine.solveCaptcha(
                bitmap = frame,
                apiKey = key,
                modelName = model,
                learnedRules = rules
            )

            _isSolving.value = false

            result.onSuccess { solution ->
                _currentSolution.value = solution
                FloatingHudService.aiOutput.value = solution.cleanAnswer
                FloatingHudService.currentLatency.value = solution.latencyMs
                FloatingHudService.hudStatus.value = HudStatus.TYPING
                FloatingHudService.marqueeLog.value = "Typing: ${solution.cleanAnswer}"

                prefs.recordAttempt(solved = true, latencyMs = solution.latencyMs)
                updateTelemetryFromPrefs()

                // Execute organic typing and auto-submission via accessibility
                val accessibility = CaptchaAccessibilityService.instance
                if (accessibility != null) {
                    accessibility.performOrganicTyping(
                        textToType = solution.cleanAnswer,
                        targetInputBounds = null,
                        telemetry = _humanTelemetry.value
                    ) {
                        viewModelScope.launch {
                            FloatingHudService.hudStatus.value = HudStatus.SUBMITTING
                            FloatingHudService.marqueeLog.value = "Submitted successfully."
                            delay(600)
                            FloatingHudService.hudStatus.value = HudStatus.STANDBY
                            onFinished?.invoke(true)
                        }
                    }
                } else {
                    delay(500)
                    FloatingHudService.hudStatus.value = HudStatus.STANDBY
                    FloatingHudService.marqueeLog.value = "Solved (${solution.latencyMs}ms)"
                    onFinished?.invoke(true)
                }

            }.onFailure { err ->
                FloatingHudService.hudStatus.value = HudStatus.RETRYING
                FloatingHudService.marqueeLog.value = "Error: ${err.message}"
                prefs.recordAttempt(solved = false, latencyMs = 0L)
                updateTelemetryFromPrefs()
                _toastMessage.value = "Solve error: ${err.message}"
                delay(1200)
                FloatingHudService.hudStatus.value = HudStatus.STANDBY
                onFinished?.invoke(false)
            }
        }
    }

    fun deleteLearnedRule(index: Int) {
        prefs.removeLearnedRule(index)
        _learnedRules.value = prefs.getLearnedRules()
        _toastMessage.value = "Rule removed."
    }

    fun clearAllLearnedRules() {
        prefs.clearLearnedRules()
        _learnedRules.value = emptyList()
        _toastMessage.value = "All learned rules cleared."
    }

    fun addManualRule(rule: String) {
        if (rule.isNotBlank()) {
            prefs.addLearnedRule(rule)
            _learnedRules.value = prefs.getLearnedRules()
            _toastMessage.value = "Rule saved!"
        }
    }

    fun onLiveAnswerReceived(answer: String, clickX: Float, clickY: Float) {
        FloatingHudService.aiOutput.value = answer
        FloatingHudService.marqueeLog.value = "Live Solved: $answer"
        FloatingHudService.hudStatus.value = HudStatus.TYPING

        prefs.recordAttempt(solved = true, latencyMs = 350L)
        updateTelemetryFromPrefs()

        val accessibility = CaptchaAccessibilityService.instance
        if (accessibility != null) {
            accessibility.performOrganicTyping(
                textToType = answer,
                targetInputBounds = null,
                telemetry = _humanTelemetry.value
            ) {
                FloatingHudService.hudStatus.value = HudStatus.SUBMITTING
                FloatingHudService.hudStatus.value = HudStatus.STANDBY
            }
        }
    }

    private fun updateTelemetryFromPrefs() {
        val attempts = prefs.getTotalAttempts()
        val solved = prefs.getTasksSolved()
        val latencySum = prefs.getLatencySum()

        _telemetryStats.value = _telemetryStats.value.copy(
            tasksSolved = solved,
            totalAttempts = attempts,
            successRate = if (attempts > 0) (solved.toFloat() / attempts) * 100f else 100f,
            avgLatencyMs = if (attempts > 0) latencySum / attempts else 0L
        )
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun resetTelemetryStats() {
        prefs.resetStats()
        updateTelemetryFromPrefs()
        _toastMessage.value = "Telemetry counters reset."
    }

    override fun onCleared() {
        sessionTimerJob?.cancel()
        autoSolveJob?.cancel()
        screenCaptureManager?.release()
        super.onCleared()
    }
}