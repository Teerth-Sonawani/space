package com.space.app.domain.generation

/**
 * Low-level text completion used by every vendor adapter.
 * Higher layers ([LlmPlanGenerator], [QuestionsGenerator]) own prompts and JSON.
 */
interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmResult
}

data class LlmRequest(
    val system: String,
    val user: String,
    /** Soft hint; adapters map to their vendor model id. */
    val temperature: Double = 0.4,
    val maxTokens: Int = 4096
)

sealed interface LlmResult {
    data class Ok(val text: String) : LlmResult

    data class HttpError(
        val code: Int,
        val body: String,
        val retryAfterSeconds: Long? = null
    ) : LlmResult

    data class NetworkError(val cause: Throwable) : LlmResult

    data object Timeout : LlmResult
}
