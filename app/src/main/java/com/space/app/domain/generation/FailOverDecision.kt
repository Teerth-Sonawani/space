package com.space.app.domain.generation

/**
 * The failover table from PLAN section 3 as a value.
 *
 * The questions call and the plan call must behave identically toward a 401,
 * a 429 or a 5xx, so the decision lives here once and is converted into each
 * call's own attempt type by [toGenerationAttempt] / [toQuestionsAttempt].
 */
sealed interface FailOverDecision {
    /** Cool or invalidate this key, then try the next provider. */
    data class TryNext(
        val kind: FailOverKind,
        val message: String,
        val retryAfterSeconds: Long? = null
    ) : FailOverDecision

    /** Our prompt or payload is wrong — failing over would hide a bug. */
    data class Stop(val message: String) : FailOverDecision
}

object HttpFailOver {
    fun from(code: Int, retryAfterSeconds: Long? = null): FailOverDecision = when (code) {
        401, 403 -> FailOverDecision.TryNext(FailOverKind.AUTH, "Key rejected ($code)")
        429 -> FailOverDecision.TryNext(
            FailOverKind.RATE_LIMIT,
            "Rate limited",
            retryAfterSeconds
        )

        in 500..599 -> FailOverDecision.TryNext(FailOverKind.SERVER, "Server error ($code)")
        400 -> FailOverDecision.Stop("Bad request (400) — prompt or payload bug")
        else -> FailOverDecision.TryNext(FailOverKind.SERVER, "HTTP $code")
    }
}

fun FailOverDecision.toGenerationAttempt(): GenerationAttempt = when (this) {
    is FailOverDecision.TryNext -> GenerationAttempt.FailOver(
        reason = kind,
        message = message,
        retryAfterSeconds = retryAfterSeconds
    )

    is FailOverDecision.Stop -> GenerationAttempt.HardFail(message)
}

fun FailOverDecision.toQuestionsAttempt(): QuestionsAttempt = when (this) {
    is FailOverDecision.TryNext -> QuestionsAttempt.FailOver(
        reason = kind,
        message = message,
        retryAfterSeconds = retryAfterSeconds
    )

    is FailOverDecision.Stop -> QuestionsAttempt.HardFail(message)
}
