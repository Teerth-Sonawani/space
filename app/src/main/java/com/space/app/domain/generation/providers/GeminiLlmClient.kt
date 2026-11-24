package com.space.app.domain.generation.providers

import com.space.app.domain.generation.LlmClient
import com.space.app.domain.generation.LlmRequest
import com.space.app.domain.generation.LlmResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google AI Gemini generateContent adapter.
 * Default model is a current free-tier-friendly flash variant.
 */
class GeminiLlmClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val http: OkHttpClient = defaultHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LlmClient {

    override suspend fun complete(request: LlmRequest): LlmResult {
        val url = ("https://generativelanguage.googleapis.com/v1beta/models/" +
            "$model:generateContent")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .build()

        val payload = buildJsonObject {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(request.system)) })
                        }
                    )
                }
            )
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put(
                                "parts",
                                buildJsonArray {
                                    add(buildJsonObject { put("text", JsonPrimitive(request.user)) })
                                }
                            )
                        }
                    )
                }
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", JsonPrimitive(request.temperature))
                    put("maxOutputTokens", JsonPrimitive(request.maxTokens))
                }
            )
        }

        val httpRequest = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        return when (val raw = http.executeLlm(httpRequest)) {
            is LlmResult.Ok -> extractText(raw.text)?.let { LlmResult.Ok(it) }
                ?: LlmResult.HttpError(502, "Empty Gemini content")
            else -> raw
        }
    }

    private fun extractText(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val candidates = root["candidates"]?.jsonArray ?: return null
            val parts = candidates.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?: return null
            parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .joinToString("")
                .ifBlank { null }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
