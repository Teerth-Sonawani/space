package com.space.app.domain.generation.providers

import com.space.app.domain.generation.LlmClient
import com.space.app.domain.generation.LlmRequest
import com.space.app.domain.generation.LlmResult
import okhttp3.OkHttpClient

/** Groq OpenAI-compatible chat completions. */
class GroqLlmClient(
    apiKey: String,
    model: String = DEFAULT_MODEL,
    http: OkHttpClient = defaultHttpClient()
) : LlmClient {

    private val delegate = OpenAiCompatClient(
        apiKey = apiKey,
        baseUrl = "https://api.groq.com/openai/v1",
        model = model,
        http = http
    )

    override suspend fun complete(request: LlmRequest): LlmResult =
        delegate.complete(request)

    companion object {
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }
}
