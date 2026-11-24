package com.space.app.domain.generation

import com.space.app.domain.model.Plan

/**
 * Outcome of one provider attempt, so [ProviderChain] can apply the failover table.
 */
sealed interface GenerationAttempt {
    data class Success(val plan: Plan, val usedStarter: Boolean = false) : GenerationAttempt

    /** Try the next key (after applying cooldown / invalid as needed). */
    data class FailOver(
        val reason: FailOverKind,
        val message: String,
        val retryAfterSeconds: Long? = null
    ) : GenerationAttempt

    /** Prompt/request bug — do not fail over. */
    data class HardFail(val message: String) : GenerationAttempt
}

enum class FailOverKind {
    RATE_LIMIT,
    AUTH,
    SERVER,
    NETWORK,
    TIMEOUT,
    MALFORMED
}
