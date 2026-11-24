package com.space.app.domain.generation.providers

import com.space.app.data.ProviderKind
import com.space.app.domain.generation.LlmClient
import okhttp3.OkHttpClient

object LlmClientFactory {
    fun create(
        kind: ProviderKind,
        apiKey: String,
        http: OkHttpClient
    ): LlmClient = when (kind) {
        ProviderKind.GEMINI -> GeminiLlmClient(apiKey = apiKey, http = http)
        ProviderKind.GROQ -> GroqLlmClient(apiKey = apiKey, http = http)
        ProviderKind.OPENROUTER -> OpenRouterLlmClient(apiKey = apiKey, http = http)
    }
}
