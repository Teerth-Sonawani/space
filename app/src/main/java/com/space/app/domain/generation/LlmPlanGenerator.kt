package com.space.app.domain.generation

import com.space.app.domain.generation.prompts.PlanPrompts
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.validation.PlanValidation
import com.space.app.domain.validation.PlanValidator

/**
 * One LLM plan call with a single repair retry on malformed JSON / schema failure.
 */
class LlmPlanGenerator(
    private val client: LlmClient
) {
    suspend fun attempt(brief: IdeaBrief): GenerationAttempt {
        val first = callOnce(
            system = PlanPrompts.system,
            user = PlanPrompts.user(brief)
        )
        val malformed = first.attempt as? GenerationAttempt.FailOver
        if (malformed != null && malformed.reason == FailOverKind.MALFORMED) {
            val repair = callOnce(
                system = PlanPrompts.system,
                user = PlanPrompts.repair(
                    brief = brief,
                    previousOutput = first.rawText,
                    errors = listOf(malformed.message)
                )
            )
            return repair.attempt
        }
        return first.attempt
    }

    private suspend fun callOnce(system: String, user: String): ParsedCall {
        return when (
            val result = client.complete(
                LlmRequest(system = system, user = user, temperature = 0.35, maxTokens = 4096)
            )
        ) {
            is LlmResult.Ok -> ParsedCall(result.text, parseAndValidate(result.text))
            is LlmResult.HttpError -> ParsedCall("", mapHttp(result))
            is LlmResult.NetworkError -> ParsedCall(
                "",
                GenerationAttempt.FailOver(
                    FailOverKind.NETWORK,
                    result.cause.message ?: "Network error"
                )
            )
            LlmResult.Timeout -> ParsedCall(
                "",
                GenerationAttempt.FailOver(FailOverKind.TIMEOUT, "Request timed out")
            )
        }
    }

    private fun parseAndValidate(text: String): GenerationAttempt {
        val decoded = PlanJsonDecoder.decode(text)
        if (decoded.isFailure) {
            return GenerationAttempt.FailOver(
                FailOverKind.MALFORMED,
                decoded.exceptionOrNull()?.message ?: "Malformed JSON"
            )
        }
        return when (val validation = PlanValidator.validate(decoded.getOrThrow())) {
            is PlanValidation.Valid -> GenerationAttempt.Success(validation.plan)
            is PlanValidation.Invalid -> GenerationAttempt.FailOver(
                FailOverKind.MALFORMED,
                validation.errors.joinToString("; ")
            )
        }
    }

    private fun mapHttp(error: LlmResult.HttpError): GenerationAttempt =
        HttpFailOver.from(error.code, error.retryAfterSeconds).toGenerationAttempt()

    private data class ParsedCall(val rawText: String, val attempt: GenerationAttempt)
}
