package com.space.app.domain.generation.providers

import com.space.app.domain.generation.LlmClient
import com.space.app.domain.generation.LlmRequest
import com.space.app.domain.generation.LlmResult
import okhttp3.OkHttpClient

/** OpenRouter OpenAI-compatible chat completions (free-tier models available). */
class OpenRouterLlmClient(
    apiKey: String,
    model: String = DEFAULT_MODEL,
    http: OkHttpClient = defaultHttpClient()
) : LlmClient {

    private val delegate = OpenAiCompatClient(
        apiKey = apiKey,
        baseUrl = "https://openrouter.ai/api/v1",
        model = model,
        http = http,
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://space.app.local",
            "X-Title" to "Space"
        )
    )

    override suspend fun complete(request: LlmRequest): LlmResult =
        delegate.complete(request)

    companion object {
        const val DEFAULT_MODEL = "meta-llama/llama-3.2-3b-instruct:free"
    }
}
