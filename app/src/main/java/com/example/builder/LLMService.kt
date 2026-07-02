package com.example.builder

import android.util.Log
import com.example.data.model.ApiKey
import com.example.data.model.ChatMessage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LLMService {

    private val TAG = "LLMService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    interface Callback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    fun callLLM(
        apiKeyConfig: ApiKey,
        systemPrompt: String,
        messages: List<ChatMessage>,
        callback: Callback
    ) {
        val provider = apiKeyConfig.providerId
        val apiKey = apiKeyConfig.apiKey
        val selectedModel = apiKeyConfig.selectedModel ?: getDefaultModel(provider)

        Log.d(TAG, "Calling LLM provider: $provider with model: $selectedModel")

        if (provider == "google") {
            callGeminiAPI(apiKey, selectedModel, systemPrompt, messages, callback)
        } else {
            val baseUrl = when (provider) {
                "openrouter" -> "https://openrouter.ai/api/v1"
                "deepseek" -> "https://api.deepseek.com/v1"
                "mistral" -> "https://api.mistral.ai/v1"
                "nvidia" -> "https://integrate.api.nvidia.com/v1"
                "custom" -> apiKeyConfig.baseUrl ?: ""
                else -> ""
            }

            if (baseUrl.isEmpty()) {
                callback.onError("Base URL for provider $provider is empty.")
                return
            }

            callOpenAICompatibleAPI(baseUrl, apiKey, selectedModel, systemPrompt, messages, callback)
        }
    }

    private fun callGeminiAPI(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
        callback: Callback
    ) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        try {
            val jsonBody = JSONObject()

            // 1. Add system instructions
            val systemInstruction = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            jsonBody.put("systemInstruction", systemInstruction)

            // 2. Add contents (messages)
            val contentsArray = JSONArray()
            messages.forEach { msg ->
                val role = when (msg.role) {
                    "user" -> "user"
                    "model" -> "model"
                    else -> "user" // Fallback
                }
                val partObj = JSONObject().put("text", msg.content)
                val messageObj = JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(partObj))
                contentsArray.put(messageObj)
            }
            jsonBody.put("contents", contentsArray)

            // 3. Generation configuration
            val generationConfig = JSONObject().put("temperature", 0.2)
            jsonBody.put("generationConfig", generationConfig)

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback.onError(e.message ?: "Network error calling Gemini API")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bodyString = it.body?.string()
                        if (it.isSuccessful && bodyString != null) {
                            try {
                                val root = JSONObject(bodyString)
                                val candidates = root.getJSONArray("candidates")
                                if (candidates.length() > 0) {
                                    val candidate = candidates.getJSONObject(0)
                                    val content = candidate.getJSONObject("content")
                                    val parts = content.getJSONArray("parts")
                                    if (parts.length() > 0) {
                                        val text = parts.getJSONObject(0).getString("text")
                                        callback.onSuccess(text)
                                        return
                                    }
                                }
                                callback.onError("No text returned in Gemini response.")
                            } catch (e: Exception) {
                                callback.onError("Failed to parse Gemini JSON: ${e.message}\nResponse: $bodyString")
                            }
                        } else {
                            callback.onError("Gemini API Error (Code ${it.code}): ${bodyString ?: "Empty body"}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            callback.onError("Failed to build Gemini request: ${e.message}")
        }
    }

    private fun callOpenAICompatibleAPI(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
        callback: Callback
    ) {
        val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        try {
            val jsonBody = JSONObject()
            jsonBody.put("model", model)
            jsonBody.put("temperature", 0.2)

            val messagesArray = JSONArray()

            // 1. Add system prompt
            messagesArray.put(JSONObject().put("role", "system").put("content", systemPrompt))

            // 2. Add history
            messages.forEach { msg ->
                val role = when (msg.role) {
                    "user" -> "user"
                    "model" -> "assistant"
                    else -> "system"
                }
                messagesArray.put(JSONObject().put("role", role).put("content", msg.content))
            }

            jsonBody.put("messages", messagesArray)

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback.onError(e.message ?: "Network error calling OpenAI-compatible API")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bodyString = it.body?.string()
                        if (it.isSuccessful && bodyString != null) {
                            try {
                                val root = JSONObject(bodyString)
                                val choices = root.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val messageObj = choice.getJSONObject("message")
                                    val text = messageObj.getString("content")
                                    callback.onSuccess(text)
                                    return
                                }
                                callback.onError("No text returned in OpenAI-compatible response.")
                            } catch (e: Exception) {
                                callback.onError("Failed to parse response JSON: ${e.message}\nResponse: $bodyString")
                            }
                        } else {
                            callback.onError("LLM API Error (Code ${it.code}): ${bodyString ?: "Empty body"}")
                        }
                    }
                }
            })

        } catch (e: Exception) {
            callback.onError("Failed to build request: ${e.message}")
        }
    }

    fun getDefaultModel(provider: String): String {
        return when (provider) {
            "google" -> "gemini-1.5-pro"
            "openrouter" -> "deepseek/deepseek-chat"
            "deepseek" -> "deepseek-chat"
            "mistral" -> "mistral-large-latest"
            "nvidia" -> "meta/llama-3-70b-instruct"
            "custom" -> "default-model"
            else -> ""
        }
    }
}
