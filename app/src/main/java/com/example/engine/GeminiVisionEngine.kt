package com.example.engine

import android.graphics.Bitmap
import android.util.Base64
import com.example.model.CaptchaSolution
import com.example.model.LogLevel
import com.example.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GeminiVisionEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches available Google Gemini models supporting generateContent
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API Key is empty"))
        }

        try {
            Logger.log("NETWORK", "Fetching available Gemini models from Google Generative Language API...", LogLevel.NETWORK)
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "HTTP ${response.code}"
                Logger.log("NETWORK", "Fetch models failed: HTTP ${response.code} - $errBody", LogLevel.ERROR)
                return@withContext Result.failure(Exception("HTTP ${response.code}: $errBody"))
            }

            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            val modelsArray = json.optJSONArray("models") ?: JSONArray()
            val resultModels = mutableListOf<String>()

            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val name = modelObj.optString("name", "") // e.g. "models/gemini-3.5-flash"
                val cleanName = name.removePrefix("models/")
                val methodsArray = modelObj.optJSONArray("supportedGenerationMethods")

                var supportsGenerateContent = false
                if (methodsArray != null) {
                    for (j in 0 until methodsArray.length()) {
                        if (methodsArray.getString(j) == "generateContent") {
                            supportsGenerateContent = true
                            break
                        }
                    }
                }

                if (supportsGenerateContent && cleanName.isNotBlank()) {
                    resultModels.add(cleanName)
                }
            }

            Logger.log("NETWORK", "Successfully retrieved ${resultModels.size} content-generation models.", LogLevel.NETWORK)
            Result.success(resultModels)
        } catch (e: Exception) {
            Logger.log("NETWORK", "Error fetching models: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    /**
     * Solves a CAPTCHA image with 2Captcha specific cognitive reasoning rules
     */
    suspend fun solveCaptcha(
        bitmap: Bitmap,
        apiKey: String,
        modelName: String = "gemini-3.5-flash",
        customDirective: String? = null,
        learnedRules: List<String> = emptyList()
    ): Result<CaptchaSolution> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API Key is missing. Set it in Settings."))
        }

        try {
            Logger.log("VISION", "Encoding frame to Base64 JPEG for model $modelName...", LogLevel.VISION)
            val base64Image = bitmapToBase64(bitmap)

            // Construct 2Captcha Cognitive Reasoning System Instructions
            val systemInstructions = buildSystemInstructions(customDirective, learnedRules)

            // Construct Gemini REST Payload
            val payload = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val userTurn = JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            // Text directive
                            val promptText = if (customDirective.isNullOrBlank()) {
                                "Solve the 2Captcha puzzle in this image. Strict output format: <answer>SOLUTION</answer>"
                            } else {
                                "Directive: $customDirective\nSolve this 2Captcha puzzle. Strict output format: <answer>SOLUTION</answer>"
                            }
                            put(JSONObject().put("text", promptText))
                            // Image part
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(userTurn)
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstructions)))
                })

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1) // Low temperature for high precision OCR and math
                    put("topP", 0.95)
                    put("maxOutputTokens", 150)
                })
            }

            val targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(targetUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            Logger.log("NETWORK", "Sending frame payload to Gemini REST endpoint...", LogLevel.NETWORK)
            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                val err = response.body?.string() ?: "HTTP ${response.code}"
                Logger.log("NETWORK", "Gemini API Error (HTTP ${response.code}, ${latency}ms): $err", LogLevel.ERROR)
                return@withContext Result.failure(Exception("HTTP ${response.code}: $err"))
            }

            val responseBody = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)
            val rawText = parseCandidatesText(jsonResponse)

            Logger.log("VISION", "Received response in ${latency}ms. Raw: $rawText", LogLevel.VISION)

            val cleanAnswer = extractAnswer(rawText)
            Logger.log("VISION", "Extracted Clean Answer: '$cleanAnswer'", LogLevel.VISION)

            val solution = CaptchaSolution(
                rawAnswer = rawText,
                cleanAnswer = cleanAnswer,
                latencyMs = latency,
                snapshot = bitmap
            )

            Result.success(solution)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Logger.log("NETWORK", "Error during Gemini Vision solve (${latency}ms): ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    /**
     * Executes Meta-Reflection when an error correction banner ("Correct answer: [X]") occurs
     */
    suspend fun generateMetaReflectionRule(
        wrongGuess: String,
        correctAnswer: String,
        directive: String,
        apiKey: String,
        modelName: String = "gemini-3.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API Key missing"))

        try {
            Logger.log("LEARNING", "Triggering Meta-Reflection: Predicted '$wrongGuess', Correct '$correctAnswer'...", LogLevel.LEARNING)
            val reflectionPrompt = "You predicted '$wrongGuess', but 2Captcha stated correct answer is '$correctAnswer' for directive '$directive'. Formulate 1 strict, concise lesson (max 15 words) to avoid this mistake."

            val payload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", reflectionPrompt)))
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 60)
                })
            }

            val targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(targetUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val rawRule = parseCandidatesText(json).trim()
            val cleanRule = rawRule.replace("\n", " ").replace("\"", "").trim()

            Logger.log("LEARNING", "Generated Reflection Rule: \"$cleanRule\"", LogLevel.LEARNING)
            Result.success(cleanRule)
        } catch (e: Exception) {
            Logger.log("LEARNING", "Meta-reflection failed: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    private fun buildSystemInstructions(customDirective: String?, learnedRules: List<String>): String {
        val sb = StringBuilder()
        sb.append("You are an autonomous high-performance 2Captcha visual solver engine.\n\n")

        sb.append("CRITICAL ZERO CONVERSATIONAL FILLER DIRECTIVE:\n")
        sb.append("- Forbid conversational filler, explanations, markdown commentary, reasoning steps, or phrases like 'Reasoning:', 'The image is blank', 'I cannot solve this'.\n")
        sb.append("- Output MUST be placed strictly inside <answer>...</answer> tags. For example: <answer>RDG</answer> or <answer>984</answer> or <answer>MUAG6T</answer>.\n\n")

        sb.append("SCREENSHOT & PUZZLE PARSING RULES:\n")
        sb.append("The input image is a full screen capture or cropped challenge from 2Captcha (such as 2captcha.com/play-and-earn).\n")
        sb.append("1. Locate the 2Captcha container on the screen, the directive text, and the captcha challenge canvas.\n")
        sb.append("2. Touch / Click Order & Letter Pairs (e.g. 'Choose the letter pairs on the pictures in the correct order', 'Assemble from 2 elements the same code'):\n")
        sb.append("   - If the task shows target letter pairs or codes at the top (e.g. 'TR VP XW' or 'XVM SRI') and options below:\n")
        sb.append("   - Identify the exact sequence of items to click/select.\n")
        sb.append("   - If answer is typed or submitted, provide the exact sequence of letters/pairs separated by space, or coordinates if asked.\n")
        sb.append("   - If multiple-choice buttons or order is required, list the items in order e.g. <answer>TR VP XW</answer> or <answer>XVM SRI</answer>.\n")
        sb.append("3. Shape & Geometric Sub-labels: When instruction says 'Type letters above the square' (or triangle, circle, etc.), inspect each letter and its aligned shape below/above it. Extract ONLY the letters that correspond to that specific shape in left-to-right order (e.g. if letters above squares are 'R D G' and letters above triangles are 'W M V', and the prompt asks for squares, answer 'RDG').\n")
        sb.append("4. Distorted OCR Alphanumeric: If no shapes or special rules, accurately read all letters and numbers in left-to-right order.\n")
        sb.append("5. Arithmetic Equations: When instruction says 'solve math' or contains '+', '-', '*', '=' (e.g. '26 + 6 = ?') -> compute exact numeric solution -> 32.\n")
        sb.append("6. Numeral-to-Digits: When instruction says 'enter numeral in digits' and shows written words (e.g. 'nine hundred and eighty four') -> output 984.\n")
        sb.append("7. Dice Value Combinations: When dice are displayed, count dots on each die in left-to-right order.\n")
        sb.append("8. Character Length Boundaries: Adhere to 'min X' / 'max Y' constraints displayed below the captcha.\n")

        if (learnedRules.isNotEmpty()) {
            sb.append("\nAUTONOMOUS SELF-LEARNED RULES (MANDATORY TO FOLLOW):\n")
            learnedRules.forEachIndexed { index, rule ->
                sb.append("${index + 1}. $rule\n")
            }
        }

        return sb.toString()
    }

    private fun parseCandidatesText(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            sb.append(part.optString("text", ""))
        }
        return sb.toString()
    }

    private fun extractAnswer(raw: String): String {
        // Look for <answer>...</answer> tags
        val pattern = Pattern.compile("<answer>([\\s\\S]*?)</answer>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(raw)
        if (matcher.find()) {
            return matcher.group(1)?.trim() ?: ""
        }
        // Fallback cleanup if tags were missing
        return raw.replace("\n", "").trim()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
