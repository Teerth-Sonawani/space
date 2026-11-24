package com.space.app.domain.generation

import com.space.app.domain.model.ClarifyingQuestion

/** One configured key's questions client, keyed by its source id. */
data class ChainedQuestionsProvider(
    val sourceId: String,
    val generator: QuestionsGenerator
)

sealed interface QuestionsChainResult {
    data class Ok(val questions: List<ClarifyingQuestion>) : QuestionsChainResult
    data class Failed(val reason: String) : QuestionsChainResult
}

/**
 * The questions call with the same failover table as the plan call, via the
 * shared [runFailOverChain] core. Never returns the offline pack itself — the
 * caller (view model) decides whether to fall back, so that decision stays in
 * one place and stays testable.
 */
class QuestionsChain(
    /** Suspend because building a client means reading encrypted keys off disk. */
    private val providers: suspend () -> List<ChainedQuestionsProvider>,
    private val health: KeyHealthStore = KeyHealthStore(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val perAttemptTimeoutMs: Long = 20_000L,
    private val chainBudgetMs: Long = 55_000L,
    private val defaultCooldownSeconds: Long = DEFAULT_COOLDOWN_SECONDS
) {
    suspend fun generate(idea: String): QuestionsChainResult {
        val list = providers()
        if (list.isEmpty()) return QuestionsChainResult.Failed("No API keys configured")

        val outcome = runFailOverChain(
            sourceIds = list.map { it.sourceId },
            health = health,
            clock = clock,
            perAttemptTimeoutMs = perAttemptTimeoutMs,
            chainBudgetMs = chainBudgetMs,
            defaultCooldownSeconds = defaultCooldownSeconds
        ) { sourceId ->
            val provider = list.first { it.sourceId == sourceId }
            when (val attempt = provider.generator.attempt(idea)) {
                is QuestionsAttempt.Success -> ChainAttempt.Success(attempt.questions)
                is QuestionsAttempt.FailOver -> ChainAttempt.FailOver(
                    attempt.reason,
                    attempt.message,
                    attempt.retryAfterSeconds
                )

                is QuestionsAttempt.HardFail -> ChainAttempt.HardFail(attempt.message)
            }
        }

        return when (outcome) {
            is ChainOutcome.Ok -> QuestionsChainResult.Ok(outcome.value)
            is ChainOutcome.Failed -> QuestionsChainResult.Failed(outcome.reason)
        }
    }
}
