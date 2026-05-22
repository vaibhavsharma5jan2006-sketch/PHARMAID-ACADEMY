package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.concurrent.TimeUnit

// --- API Request / Response Models ---
data class GeminiPart(val text: String)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiSystemInstruction(val parts: List<GeminiPart>)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null
)

data class GeminiResponsePart(val text: String?)
data class GeminiResponseContent(val parts: List<GeminiResponsePart>?)
data class GeminiCandidate(val content: GeminiResponseContent?)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)

object GeminiTutor {
    private const val TAG = "GeminiTutor"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    // OkHttpClient with crucial 60-second timeouts for complex medical queries
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    suspend fun generatePharmaHelp(prompt: String, conversationHistory: List<Pair<String, Boolean>> = emptyList()): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or placeholder!")
            return@withContext "API Key not configured in the Secrets Panel. Go to AI Studio Secrets and enter your GEMINI_API_KEY."
        }

        // Build the contents array including conversation history if provided
        val contents = mutableListOf<GeminiContent>()
        
        for (turn in conversationHistory) {
            val prefix = if (turn.second) "Student: " else "Pharma Assistant: "
            contents.add(GeminiContent(parts = listOf(GeminiPart(text = "$prefix${turn.first}"))))
        }
        
        contents.add(GeminiContent(parts = listOf(GeminiPart(text = "Student: $prompt"))))

        val systemPrompt = """
            You are PHARMAID TUTOR, a distinguished academic pharmacy mentor and clinical pharmacology professor. Your duties:
            1. Accurately explain core pharmaceutical mechanisms, dosage formulas, and organic molecular pathways.
            2. Break down pharmacist calculations (Posology, Allegation method, milliequivalents, compounding metrics).
            3. Structure answers step-by-step with high-level summaries, bullet points, and clinical tips.
            4. If asked to generate mock exam questions, return a quiz with correct options and complete pharmacological explanations.
            5. Adopt an encouraging, professional, and rigorous medical-academic tone. Keep response formatted with clean markdowns.
        """.trimIndent()

        val requestPayload = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiSystemInstruction(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        try {
            val jsonRequestBody = requestAdapter.toJson(requestPayload)
            val requestBody = jsonRequestBody.toRequestBody("application/json".toMediaType())

            val requestUrl = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Error from Gemini service: Code ${response.code}, msg: $errBody")
                    
                    return@withContext if (response.code == 400 && errBody.contains("API key")) {
                        "Verification Error: Your GEMINI_API_KEY appears to be invalid. Please recheck your API key settings in the active Secrets Panel."
                    } else {
                        "Error code ${response.code}: Failed to communicate with the AI tutor. Please try again."
                    }
                }

                val bodyString = response.body?.string()
                if (bodyString != null) {
                    val parsedResponse = responseAdapter.fromJson(bodyString)
                    val resultText = parsedResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    return@withContext resultText ?: "No response grew from the clinical assistant. Please ask again."
                } else {
                    return@withContext "Received an empty response stream. Try again."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini Call: ", e)
            return@withContext "Network error connecting to PHARMAID AI Tutor: ${e.localizedMessage}. Verify your internet connection."
        }
    }
}
