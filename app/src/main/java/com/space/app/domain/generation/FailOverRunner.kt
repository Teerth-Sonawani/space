package com.space.app.domain.generation

import com.space.app.data.KeyHealth
import kotlinx.coroutines.withTimeoutOrNull

/** One provider's result, before it is turned into a plan or a question list. */
internal sealed interface ChainAttempt<out T> {
    data class Success<T>(val value: T) : ChainAttempt<T>

    /** Cool this key and move to the next provider. */
    data class FailOver(
        val reason: FailOverKind,
        val message: String,
        val retryAfterSeconds: Long? = null
    ) : ChainAttempt<Nothing>

    /** Prompt or payload bug on our side — do not fail over. */
    data class HardFail(val message: String) : ChainAttempt<Nothing>
}

internal sealed interface ChainOutcome<out T> {
    data class Ok<T>(val value: T) : ChainOutcome<T>

    /**
     * @param attemptedAny false when every source was cooling or invalid, so
     * callers know this was "no usable key" rather than "all keys failed".
     */
    data class Failed(val reason: String, val attemptedAny: Boolean) : ChainOutcome<Nothing>
}

/**
 * Runs PLAN section 3's failover table once, for either LLM call type:
 *
 * | step | behaviour |
 * |---|---|
 * | budget | stop before starting a source once `chainBudgetMs` is spent |
 * | health | skip sources that are cooling or invalid |
 * | timeout | a source that does not answer in `perAttemptTimeoutMs` is a TIMEOUT |
 * | 5xx | one retry on the same source, then fail over |
 * | 429 / 401 / network / malformed | fail over, applying cooldown or invalidation |
 * | 400 | stop the whole chain — this is our bug, not the key's |
 *
 * Payload type `T` is whatever the caller needs ([com.space.app.domain.model.Plan]
 * for the plan call, a question list for the questions call), so both chains
 * share one tested implementation.
 */
internal suspend fun <T> runFailOverChain(
    sourceIds: List<String>,
    health: KeyHealthStore,
    clock: () -> Long,
    perAttemptTimeoutMs: Long,
    chainBudgetMs: Long,
    defaultCooldownSeconds: Long = DEFAULT_COOLDOWN_SECONDS,
    call: suspend (sourceId: String) -> ChainAttempt<T>
): ChainOutcome<T> {
    if (sourceIds.isEmpty()) {
        return ChainOutcome.Failed("No API keys configured", attemptedAny = false)
    }

    val startedAt = clock()
    var lastMessage = "All sources failed"
    var attemptedAny = false

    for (sourceId in sourceIds) {
        if (clock() - startedAt >= chainBudgetMs) {
            lastMessage = "Timed out trying sources"
            break
        }

        when (health.status(sourceId)) {
            is KeyHealth.Cooling,
            is KeyHealth.Invalid -> continue

            is KeyHealth.Active -> Unit
        }
        attemptedAny = true

        var attempt = callWithTimeout(sourceId, perAttemptTimeoutMs, call)

        // One same-key retry for a transient 5xx only (PLAN section 3).
        if (attempt is ChainAttempt.FailOver && attempt.reason == FailOverKind.SERVER) {
            attempt = callWithTimeout(sourceId, perAttemptTimeoutMs, call)
        }

        when (attempt) {
            is ChainAttempt.Success -> return ChainOutcome.Ok(attempt.value)
            is ChainAttempt.HardFail -> return ChainOutcome.Failed(attempt.message, attemptedAny)
            is ChainAttempt.FailOver -> {
                lastMessage = attempt.message
                health.applyFailOver(
                    sourceId = sourceId,
                    kind = attempt.reason,
                    retryAfterSeconds = attempt.retryAfterSeconds,
                    now = clock(),
                    defaultCooldownSeconds = defaultCooldownSeconds
                )
            }
        }
    }

    return ChainOutcome.Failed(lastMessage, attemptedAny)
}

/** A source that never answers becomes a TIMEOUT attempt instead of hanging the chain. */
private suspend fun <T> callWithTimeout(
    sourceId: String,
    perAttemptTimeoutMs: Long,
    call: suspend (String) -> ChainAttempt<T>
): ChainAttempt<T> = withTimeoutOrNull(perAttemptTimeoutMs) {
    call(sourceId)
} ?: ChainAttempt.FailOver(FailOverKind.TIMEOUT, "Request timed out")
