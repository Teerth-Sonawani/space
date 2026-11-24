package com.space.app.domain.generation

import com.space.app.domain.generation.prompts.QuestionsPrompts
import com.space.app.domain.model.ClarifyingQuestion

/** Outcome of one questions call, mirroring [GenerationAttempt]. */
sealed interface QuestionsAttempt {
    data class Success(val questions: List<ClarifyingQuestion>) : QuestionsAttempt

    /** Try the next key (after applying cooldown / invalid as needed). */
    data class FailOver(
        val reason: FailOverKind,
        val message: String,
        val retryAfterSeconds: Long? = null
    ) : QuestionsAttempt

    /** Prompt/request bug — do not fail over. */
    data class HardFail(val message: String) : QuestionsAttempt
}

/**
 * The questions call: idea in, 2–4 clarifying questions out.
 *
 * Behaves like [LlmPlanGenerator] on purpose — same HTTP mapping and the same
 * single repair retry when the model answers with unusable JSON — so a key that
 * fails here fails the same way it would on the plan call.
 */
class QuestionsGenerator(
    private val client: LlmClient
) {
    suspend fun attempt(idea: String): QuestionsAttempt {
        val first = callOnce(user = QuestionsPrompts.user(idea))
        val malformed = (first.attempt as? QuestionsAttempt.FailOver)
            ?.takeIf { it.reason == FailOverKind.MALFORMED }
        if (malformed != null) {
            val repair = callOnce(
                user = QuestionsPrompts.repair(
                    idea = idea,
                    previousOutput = first.rawText,
                    errors = malformed.message
                )
            )
            return repair.attempt
        }
        return first.attempt
    }

    private suspend fun callOnce(user: String): ParsedCall {
        val request = LlmRequest(
            system = QuestionsPrompts.system,
            user = user,
            temperature = 0.5,
            maxTokens = 1024
        )
        return when (val result = client.complete(request)) {
            is LlmResult.Ok -> {
                val decoded = QuestionsJsonDecoder.decode(result.text)
                if (decoded.isSuccess) {
                    ParsedCall(result.text, QuestionsAttempt.Success(decoded.getOrThrow()))
                } else {
                    ParsedCall(
                        result.text,
                        QuestionsAttempt.FailOver(
                            FailOverKind.MALFORMED,
                            decoded.exceptionOrNull()?.message ?: "Could not parse questions"
                        )
                    )
                }
            }

            is LlmResult.HttpError -> ParsedCall(
                "",
                HttpFailOver.from(result.code, result.retryAfterSeconds).toQuestionsAttempt()
            )

            is LlmResult.NetworkError -> ParsedCall(
                "",
                QuestionsAttempt.FailOver(
                    FailOverKind.NETWORK,
                    result.cause.message ?: "Network error"
                )
            )

            LlmResult.Timeout -> ParsedCall(
                "",
                QuestionsAttempt.FailOver(FailOverKind.TIMEOUT, "Request timed out")
            )
        }
    }

    private data class ParsedCall(val rawText: String, val attempt: QuestionsAttempt)
}
