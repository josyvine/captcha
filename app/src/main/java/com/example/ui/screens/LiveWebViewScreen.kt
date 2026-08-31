package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.LogLevel
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.PrimaryContainerDark
import com.example.ui.viewmodel.MainViewModel
import com.example.util.Logger
import java.io.ByteArrayOutputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveWebViewScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val apiKey by viewModel.apiKey.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val currentFrame by viewModel.currentFrame.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.evaluateJavascript("disconnectLiveWebSocket();", null)
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Ultra-compact single-line top bar with status indicator and actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "GEMINI LIVE HOST",
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
                        text = "16kHz PCM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanGlow,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Quick Actions: Push Frame & Reload
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val frame = currentFrame
                        if (frame != null) {
                            val stream = ByteArrayOutputStream()
                            frame.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                            webViewInstance?.evaluateJavascript("window.injectScreenFrame('$base64');", null)
                            Logger.log("VISION", "Manually pushed current screen frame to Live stream.", LogLevel.VISION)
                            Toast.makeText(context, "Frame sent to Live stream", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.refreshCurrentFrame()
                            Toast.makeText(context, "Capturing fresh screen frame...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryContainerDark)
                        .testTag("push_frame_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Push Frame",
                        tint = CyanGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .testTag("reload_webview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = CyanGlow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Full display WebView container
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF010409),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Logger.log("NETWORK", "Gemini Live Web Engine initialized.", LogLevel.NETWORK)
                            }
                        }

                        // Register Native JavaScript Bridge matching gemini_live_engine.html
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun getGeminiApiKey(): String {
                                return apiKey.ifBlank { viewModel.apiKey.value }
                            }

                            @JavascriptInterface
                            fun getGeminiModel(): String {
                                return selectedModel
                            }

                            @JavascriptInterface
                            fun getGeminiVoice(): String {
                                return selectedVoice
                            }

                            @JavascriptInterface
                            fun getLearnedRulesJson(): String {
                                val rules = viewModel.learnedRules.value
                                val array = org.json.JSONArray()
                                rules.forEach { array.put(it) }
                                return array.toString()
                            }

                            @JavascriptInterface
                            fun showToast(message: String) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }

                            @JavascriptInterface
                            fun copyToClipboard(text: String) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Gemini Live Logs", text))
                                    Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }

                            @JavascriptInterface
                            fun onLiveAnswer(answer: String, clickX: Float, clickY: Float) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    viewModel.onLiveAnswerReceived(answer, clickX, clickY)
                                }
                            }

                            @JavascriptInterface
                            fun onLiveClick(xPercent: Float, yPercent: Float) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    viewModel.onLiveClickReceived(xPercent, yPercent)
                                }
                            }

                            @JavascriptInterface
                            fun onLiveHint(wrongGuess: String, correctAnswer: String, directive: String) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    viewModel.handleErrorCorrection(wrongGuess, correctAnswer, directive)
                                }
                            }

                            @JavascriptInterface
                            fun onSubmitCaptchaAnswer(answer: String, clickX: Float, clickY: Float) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    viewModel.onLiveAnswerReceived(answer, clickX, clickY)
                                }
                            }

                            @JavascriptInterface
                            fun captureScreenSnapshot() {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    viewModel.refreshCurrentFrame()
                                    val frame = viewModel.currentFrame.value
                                    if (frame != null) {
                                        val stream = ByteArrayOutputStream()
                                        frame.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                                        val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                        webViewInstance?.evaluateJavascript("window.injectScreenFrame('$base64');", null)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onLiveSessionStateChanged(active: Boolean) {
                                (context as? android.app.Activity)?.runOnUiThread {
                                    Logger.log("NETWORK", if (active) "Gemini Live WebSocket Active." else "Gemini Live Disconnected.", LogLevel.NETWORK)
                                }
                            }

                            @JavascriptInterface
                            fun log(tag: String, message: String, level: String) {
                                val logLevel = try { LogLevel.valueOf(level.uppercase()) } catch (_: Exception) { LogLevel.INFO }
                                Logger.log(tag, message, logLevel)
                            }

                            @JavascriptInterface
                            fun logError(msg: String) {
                                Logger.log("ERROR", msg, LogLevel.ERROR)
                            }
                        }, "AndroidInterface")

                        loadUrl("file:///android_asset/gemini_live_engine.html")
                        webViewInstance = this
                    }
                }
            )
        }
    }
}