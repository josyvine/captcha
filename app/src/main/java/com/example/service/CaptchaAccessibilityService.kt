package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.model.HumanTelemetryConfig
import com.example.model.LogLevel
import com.example.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Random
import java.util.regex.Pattern
import kotlin.coroutines.resume

class CaptchaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = Random()

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        var instance: CaptchaAccessibilityService? = null
            private set(value) {
                field = value
                _isConnected.value = (value != null)
            }

        fun isRunning(): Boolean = instance != null

        // Callbacks
        var onErrorCorrectionDetected: ((wrongGuess: String, correctAnswer: String, directive: String) -> Unit)? = null
        var onInputNodeFound: ((bounds: Rect) -> Unit)? = null

        // STRICT MASTER SWITCH: Only allows automated scanning & clicks when solving is explicitly active in 2Captcha
        var isSolvingActive: Boolean = false
    }

    /**
     * Captures an instant high-resolution frame directly via Android 11+ Accessibility Screenshot API
     */
    suspend fun captureScreenBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { cont ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                val copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                Logger.log("ACCESSIBILITY", "Captured live screen frame via Accessibility API (${copy?.width}x${copy?.height}).", LogLevel.VISION)
                                if (cont.isActive) cont.resume(copy)
                            } catch (e: Throwable) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Logger.log("ACCESSIBILITY", "Accessibility takeScreenshot failed code: $errorCode", LogLevel.INFO)
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                )
            } catch (e: Throwable) {
                Logger.log("ACCESSIBILITY", "Accessibility takeScreenshot exception: ${e.message}", LogLevel.INFO)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logger.log("ACCESSIBILITY", "Captcha Accessibility Service connected & active with hardware gestures & screen capture.", LogLevel.ACCESSIBILITY)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        isSolvingActive = false
        Logger.log("ACCESSIBILITY", "Captcha Accessibility Service unbound.", LogLevel.ACCESSIBILITY)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Safety Filter 1: If auto-solving is not actively engaged, stay 100% idle
        if (!isSolvingActive) return

        // Safety Filter 2: Ignore keyboard and system UI packages
        val pkg = event.packageName?.toString() ?: ""
        if (pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("systemui")) {
            return
        }

        try {
            val root = rootInActiveWindow ?: return
            if (isStrict2CaptchaScreen(root)) {
                inspectNodes(root)
            }
        } catch (_: Exception) {
            // Suppressed to prevent crashes on recycled transient nodes
        }
    }

    override fun onInterrupt() {
        isSolvingActive = false
        Logger.log("ACCESSIBILITY", "Accessibility Service interrupted.", LogLevel.ACCESSIBILITY)
    }

    /**
     * Strict check ensuring the active screen is a real 2Captcha task container
     */
    private fun isStrict2CaptchaScreen(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val combined = "$text $desc".lowercase()

        if (combined.contains("2captcha.com") ||
            combined.contains("play-and-earn") ||
            (combined.contains("2captcha") && combined.contains("balance"))
        ) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isStrict2CaptchaScreen(child)) return true
        }
        return false
    }

    /**
     * Inspects active window hierarchy for real 2Captcha red error correction banners
     */
    private fun inspectNodes(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val hint = node.hintText?.toString() ?: ""
        val combined = "$text $contentDesc $hint"

        // Detect actual 2Captcha error correction banners
        if (combined.contains("Correct answer:", ignoreCase = true) ||
            combined.contains("Right answer is:", ignoreCase = true) ||
            combined.contains("Правильный ответ:", ignoreCase = true)
        ) {
            val pattern = Pattern.compile("(?:Correct\\s+answer:|Right\\s+answer\\s+is:)\\s*\\[?([^\\]\\n,]{1,25})\\]?", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(combined)
            if (matcher.find()) {
                val correctAnswer = matcher.group(1)?.trim() ?: ""
                if (correctAnswer.isNotBlank()) {
                    Logger.log("LEARNING", "2Captcha correction banner detected! Correct: '$correctAnswer'", LogLevel.LEARNING)
                    onErrorCorrectionDetected?.invoke("PreviousGuess", correctAnswer, "2Captcha Task")
                }
            }
        }

        // Detect captcha input fields
        if (node.isEditable && (
            combined.contains("captcha", ignoreCase = true) ||
            node.className?.contains("EditText", ignoreCase = true) == true
        )) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            onInputNodeFound?.invoke(bounds)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                inspectNodes(child)
            }
        }
    }

    /**
     * Dispatches hardware click gesture at target percentage coordinates (0.0% to 100.0%) for puzzle matching
     */
    fun performCoordinateTap(
        xPercent: Float,
        yPercent: Float,
        telemetry: HumanTelemetryConfig = HumanTelemetryConfig(),
        onComplete: (() -> Unit)? = null
    ) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenW: Int
        val screenH: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenW = bounds.width().coerceAtLeast(720)
            screenH = bounds.height().coerceAtLeast(1280)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            screenW = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
            screenH = if (metrics.heightPixels > 0) metrics.heightPixels else 2400
        }

        val clampedX = xPercent.coerceIn(2.0f, 98.0f)
        val clampedY = yPercent.coerceIn(2.0f, 98.0f)

        val targetX = (screenW * (clampedX / 100.0f))
        val targetY = (screenH * (clampedY / 100.0f))

        // Jitter simulation for natural human click
        val jitterX = (random.nextFloat() - 0.5f) * 6f
        val jitterY = (random.nextFloat() - 0.5f) * 6f
        val finalX = targetX + jitterX
        val finalY = targetY + jitterY

        val holdDuration = telemetry.touchHoldMinMs + random.nextInt(
            maxOf(1, (telemetry.touchHoldMaxMs - telemetry.touchHoldMinMs).toInt())
        )

        val path = Path().apply {
            moveTo(finalX, finalY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, holdDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        Logger.log("ACCESSIBILITY", "Puzzle Click dispatched at ($finalX, $finalY) [${clampedX.toInt()}%, ${clampedY.toInt()}%] (${holdDuration}ms hold).", LogLevel.ACCESSIBILITY)

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Logger.log("ACCESSIBILITY", "Coordinate tap completed successfully.", LogLevel.ACCESSIBILITY)
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Logger.log("ACCESSIBILITY", "Coordinate tap gesture cancelled by system.", LogLevel.ERROR)
            }
        }, null)
    }

    /**
     * Dispatches native hardware touch gesture to target bounds with humanized hold physics
     */
    fun performHumanizedTap(
        targetBounds: Rect,
        telemetry: HumanTelemetryConfig = HumanTelemetryConfig(),
        onComplete: (() -> Unit)? = null
    ) {
        val innerLeft = targetBounds.left + (targetBounds.width() * 0.30f).toInt()
        val innerRight = targetBounds.left + (targetBounds.width() * 0.70f).toInt()
        val innerTop = targetBounds.top + (targetBounds.height() * 0.30f).toInt()
        val innerBottom = targetBounds.top + (targetBounds.height() * 0.70f).toInt()

        val tapX = (innerLeft + random.nextInt(maxOf(1, innerRight - innerLeft))).toFloat()
        val tapY = (innerTop + random.nextInt(maxOf(1, innerBottom - innerTop))).toFloat()

        val holdDuration = telemetry.touchHoldMinMs + random.nextInt(
            maxOf(1, (telemetry.touchHoldMaxMs - telemetry.touchHoldMinMs).toInt())
        )

        val path = Path().apply {
            moveTo(tapX, tapY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, holdDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        Logger.log("ACCESSIBILITY", "Dispatching gesture at ($tapX, $tapY) with ${holdDuration}ms dwell hold.", LogLevel.ACCESSIBILITY)

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Logger.log("ACCESSIBILITY", "Native gesture completed successfully.", LogLevel.ACCESSIBILITY)
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Logger.log("ACCESSIBILITY", "Native gesture cancelled by system.", LogLevel.ERROR)
            }
        }, null)
    }

    /**
     * Executes humanized organic typing into the target input field and auto-submits
     */
    fun performOrganicTyping(
        textToType: String,
        targetInputBounds: Rect? = null,
        telemetry: HumanTelemetryConfig = HumanTelemetryConfig(),
        onFinished: () -> Unit
    ) {
        serviceScope.launch {
            Logger.log("ACCESSIBILITY", "Initiating organic typing for \"$textToType\"...", LogLevel.ACCESSIBILITY)

            // Focus active input field
            findAndFocusInputField(targetInputBounds, telemetry)
            delay(150)

            val currentBuffer = StringBuilder()
            val chars = textToType.toCharArray()

            for (i in chars.indices) {
                val targetChar = chars[i]

                // Realistic Typo Check (3% probability)
                if (random.nextFloat() < telemetry.typoProbability && targetChar.isLetterOrDigit()) {
                    val typoChar = getAdjacentQwertyChar(targetChar)
                    currentBuffer.append(typoChar)
                    applyTextToActiveFocus(currentBuffer.toString())
                    Logger.log("ACCESSIBILITY", "Humanized Typo triggered: '$typoChar'...", LogLevel.ACCESSIBILITY)

                    delay(90)
                    currentBuffer.deleteCharAt(currentBuffer.length - 1)
                    applyTextToActiveFocus(currentBuffer.toString())
                    delay(120)
                }

                // Append correct character
                currentBuffer.append(targetChar)
                applyTextToActiveFocus(currentBuffer.toString())

                // Keystroke pacing delay (180ms - 320ms)
                val pacing = telemetry.minKeystrokeMs + random.nextInt(
                    maxOf(1, telemetry.maxKeystrokeMs - telemetry.minKeystrokeMs)
                )
                delay(pacing.toLong())

                // Cognitive Hesitation
                if (i < chars.size - 1 && random.nextFloat() < telemetry.hesitationProbability) {
                    val hesitation = telemetry.hesitationMinMs + random.nextInt(
                        maxOf(1, telemetry.hesitationMaxMs - telemetry.hesitationMinMs)
                    )
                    delay(hesitation.toLong())
                }
            }

            // Post-typing reaction delay
            val submitDelay = 150 + random.nextInt(150)
            delay(submitDelay.toLong())

            // Automatically trigger submit button click
            clickSubmitButton(telemetry)
            delay(300)

            onFinished()
        }
    }

    private fun findAndFocusInputField(bounds: Rect?, telemetry: HumanTelemetryConfig) {
        try {
            val root = rootInActiveWindow ?: return
            val inputNode = findFirstInputNode(root)
            if (inputNode != null) {
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val nodeBounds = Rect()
                inputNode.getBoundsInScreen(nodeBounds)
                performHumanizedTap(nodeBounds, telemetry)
                return
            }
        } catch (_: Exception) {}

        if (bounds != null) {
            performHumanizedTap(bounds, telemetry)
        }
    }

    private fun findFirstInputNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.className?.contains("EditText", ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstInputNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Automatically identifies and clicks the 2Captcha green Submit/Checkmark button
     */
    fun clickSubmitButton(telemetry: HumanTelemetryConfig = HumanTelemetryConfig()) {
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val submitNode = findSubmitNode(root)
                if (submitNode != null) {
                    val bounds = Rect()
                    submitNode.getBoundsInScreen(bounds)
                    Logger.log("ACCESSIBILITY", "Found submit button in accessibility tree at $bounds. Clicking...", LogLevel.ACCESSIBILITY)
                    submitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    performHumanizedTap(bounds, telemetry)
                    return
                }

                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log("ACCESSIBILITY", "Submit node search: ${e.message}", LogLevel.INFO)
        }
    }

    private fun findSubmitNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val hint = node.hintText?.toString() ?: ""
        val combined = "$text $desc $hint".lowercase()

        if (node.isClickable && (
            combined.contains("submit") ||
            combined.contains("check") ||
            combined.contains("send") ||
            combined.contains("verify") ||
            combined.contains("ok") ||
            combined.contains("отправить") ||
            combined.contains("готово")
        )) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findSubmitNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun applyTextToActiveFocus(text: String) {
        try {
            val root = rootInActiveWindow ?: return
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstInputNode(root)
            if (focused != null) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        } catch (_: Exception) {}
    }

    private fun getAdjacentQwertyChar(c: Char): Char {
        val lower = c.lowercaseChar()
        val adjacentMap = mapOf(
            'q' to 'w', 'w' to 'e', 'e' to 'r', 'r' to 't', 't' to 'y', 'y' to 'u', 'u' to 'i', 'i' to 'o', 'o' to 'p',
            'a' to 's', 's' to 'd', 'd' to 'f', 'f' to 'g', 'g' to 'h', 'h' to 'j', 'j' to 'k', 'k' to 'l',
            'z' to 'x', 'x' to 'c', 'c' to 'v', 'v' to 'b', 'b' to 'n', 'n' to 'm',
            '1' to '2', '2' to '3', '3' to '4', '4' to '5', '5' to '6', '6' to '7', '7' to '8', '8' to '9', '9' to '0'
        )
        val typo = adjacentMap[lower] ?: 'a'
        return if (c.isUpperCase()) typo.uppercaseChar() else typo
    }
}