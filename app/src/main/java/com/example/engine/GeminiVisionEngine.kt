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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Cleans model name prefix for REST generateContent endpoint
     */
    private fun sanitizeRestModelName(modelName: String): String {
        return modelName.trim().removePrefix("models/")
    }

    /**
     * Fetches available Google Gemini models that support standard REST generateContent
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

                // Filter out WebSocket-only live models to ensure only REST-compatible vision models are listed
                val isLiveOnly = cleanName.contains("live", ignoreCase = true) ||
                        cleanName.contains("native-audio", ignoreCase = true) ||
                        cleanName.contains("bidi", ignoreCase = true)

                var supportsGenerateContent = false
                if (methodsArray != null) {
                    for (j in 0 until methodsArray.length()) {
                        if (methodsArray.getString(j) == "generateContent") {
                            supportsGenerateContent = true
                            break
                        }
                    }
                }

                if (supportsGenerateContent && !isLiveOnly && cleanName.isNotBlank()) {
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
        modelName: String,
        customDirective: String? = null,
        learnedRules: List<String> = emptyList()
    ): Result<CaptchaSolution> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API Key is missing. Set it in Settings."))
        }

        val targetModel = sanitizeRestModelName(modelName)

        try {
            Logger.log("VISION", "Encoding frame to Base64 JPEG for model $targetModel...", LogLevel.VISION)
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
                                "Inspect this 2Captcha working area. Check for error correction banners at the top, then solve the challenge. Output strictly in format: <answer>SOLUTION</answer>"
                            } else {
                                "Directive: $customDirective\nSolve this 2Captcha puzzle. Output strictly in format: <answer>SOLUTION</answer>"
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

            val targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(targetUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            Logger.log("NETWORK", "Sending frame payload to Gemini REST endpoint ($targetModel)...", LogLevel.NETWORK)
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
        modelName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API Key missing"))
        val targetModel = sanitizeRestModelName(modelName)

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

            val targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"
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
        sb.append("- Output MUST be placed strictly inside <answer>...</answer> tags. For example: <answer>RDG</answer> or <answer>984</answer> or <answer>32</answer>.\n\n")

        sb.append("2CAPTCHA TASK PARSING RULES:\n")
        sb.append("1. ERROR CORRECTION CHECK: Look first at the top of the working area. If a red or orange banner says 'Right answer is...', 'Correct answer:', or 'Your answer was wrong', extract that exact correct answer inside <answer>...</answer> immediately!\n")
        sb.append("2. ARITHMETIC EQUATIONS: When instruction says 'solve math' or contains '+', '-', '*', '=' (e.g. '26 + 6 = ?') -> compute exact numeric solution -> <answer>32</answer>.\n")
        sb.append("3. NUMERAL TO DIGITS: When instruction says 'enter numeral in digits' and shows written words (e.g. 'nine hundred and eighty four') -> <answer>984</answer>.\n")
        sb.append("4. SHAPE & GEOMETRIC SUB-LABELS: When instruction says 'Type letters above the square' (or triangle, circle, etc.), inspect each letter and its aligned shape. Extract ONLY the letters corresponding to that shape in left-to-right order.\n")
        sb.append("5. DISTORTED OCR ALPHANUMERIC: Read all letters and numbers in left-to-right order accurately.\n")
        sb.append("6. MULTI-FRAME & MOVING CHARACTERS: If characters appear on left and right across alternating positions, combine them sequentially into a single word string.\n")
        sb.append("7. INTERACTIVE & PUZZLE MATCHING: If the task requires selecting letter pairs, coordinates, or missing sequence numbers, identify the exact sequence.\n")
        sb.append("8. DICE VALUES: Sum or list the numbers on dice from left to right as requested.\n")

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
        val pattern = Pattern.compile("<answer>([\\s\\S]*?)</answer>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(raw)
        if (matcher.find()) {
            return matcher.group(1)?.trim() ?: ""
        }
        return raw.replace("\n", "").trim()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}