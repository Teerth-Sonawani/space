package com.space.app.domain.generation.providers

import com.space.app.domain.generation.LlmClient
import com.space.app.domain.generation.LlmRequest
import com.space.app.domain.generation.LlmResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Shared OpenAI-compatible chat completions client used by Groq and OpenRouter.
 */
class OpenAiCompatClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val http: OkHttpClient = defaultHttpClient(),
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LlmClient {

    override suspend fun complete(request: LlmRequest): LlmResult {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(request.temperature))
            put("max_tokens", JsonPrimitive(request.maxTokens))
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(request.system))
                        }
                    )
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", JsonPrimitive(request.user))
                        }
                    )
                }
            )
        }

        val httpRequest = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .apply { extraHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .post(payload.toString().toRequestBody(JSON))
            .build()

        return when (val raw = http.executeLlm(httpRequest)) {
            is LlmResult.Ok -> extractContent(raw.text)?.let { LlmResult.Ok(it) }
                ?: LlmResult.HttpError(502, "Empty completion content")
            else -> raw
        }
    }

    private fun extractContent(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val choices = root["choices"]?.jsonArray ?: return null
            val message = choices.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            message?.get("content")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
