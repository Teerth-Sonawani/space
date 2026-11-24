package com.space.app.domain.generation

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import com.space.app.domain.validation.PlanValidation

data class ChainedProvider(
    val sourceId: String,
    val planGenerator: LlmPlanGenerator
)

sealed interface ChainResult {
    data class Ok(val plan: Plan, val usedStarter: Boolean) : ChainResult
    data class Failed(val reason: String, val canUseStarter: Boolean) : ChainResult
}

/**
 * Tries configured providers in priority order with PLAN section 3's failover
 * rules, then offers the offline starter. The table itself lives in
 * [runFailOverChain] alongside the questions call, so the two cannot drift.
 */
class ProviderChain(
    /** Suspend because building a client means reading encrypted keys off disk. */
    private val providers: suspend () -> List<ChainedProvider>,
    private val health: KeyHealthStore = KeyHealthStore(),
    private val starter: PlanGenerator = StarterPlanGenerator(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val perAttemptTimeoutMs: Long = 20_000L,
    private val chainBudgetMs: Long = 55_000L,
    private val defaultCooldownSeconds: Long = DEFAULT_COOLDOWN_SECONDS
) : PlanGenerator {

    /**
     * Rich result for the UI. [generate] adapts this to [PlanValidation] for the
     * existing interface; prefer [generateChain] from the view model.
     */
    suspend fun generateChain(
        brief: IdeaBrief,
        autoStarterWhenEmpty: Boolean = true,
        allowStarterAfterExhaustion: Boolean = false
    ): ChainResult {
        val list = providers()
        if (list.isEmpty()) {
            return if (autoStarterWhenEmpty) {
                starterResult(brief, fallbackWhenInvalid = "No API keys configured")
            } else {
                ChainResult.Failed("No API keys configured", canUseStarter = true)
            }
        }

        val outcome = runFailOverChain(
            sourceIds = list.map { it.sourceId },
            health = health,
            clock = clock,
            perAttemptTimeoutMs = perAttemptTimeoutMs,
            chainBudgetMs = chainBudgetMs,
            defaultCooldownSeconds = defaultCooldownSeconds
        ) { sourceId ->
            val provider = list.first { it.sourceId == sourceId }
            provider.planGenerator.attempt(brief).toChainAttempt()
        }

        return when (outcome) {
            is ChainOutcome.Ok -> ChainResult.Ok(outcome.value, usedStarter = false)

            is ChainOutcome.Failed -> {
                // No usable key at all is not a failure the user can fix by
                // retrying, so go straight to the starter when allowed.
                if (allowStarterAfterExhaustion || !outcome.attemptedAny) {
                    starterResult(brief, fallbackWhenInvalid = outcome.reason)
                } else {
                    ChainResult.Failed(outcome.reason, canUseStarter = true)
                }
            }
        }
    }

    override suspend fun generate(brief: IdeaBrief): PlanValidation =
        when (
            val result = generateChain(
                brief,
                autoStarterWhenEmpty = true,
                allowStarterAfterExhaustion = true
            )
        ) {
            is ChainResult.Ok -> PlanValidation.Valid(result.plan)
            is ChainResult.Failed -> PlanValidation.Invalid(listOf(result.reason))
        }

    suspend fun generateStarter(brief: IdeaBrief): ChainResult = starterResult(
        brief,
        fallbackWhenInvalid = "Starter plan failed validation"
    )

    private suspend fun starterResult(brief: IdeaBrief, fallbackWhenInvalid: String): ChainResult =
        when (val v = starter.generate(brief)) {
            is PlanValidation.Valid -> ChainResult.Ok(v.plan, usedStarter = true)
            is PlanValidation.Invalid -> ChainResult.Failed(
                v.errors.firstOrNull() ?: fallbackWhenInvalid,
                canUseStarter = false
            )
        }
}

private fun GenerationAttempt.toChainAttempt(): ChainAttempt<Plan> = when (this) {
    is GenerationAttempt.Success -> ChainAttempt.Success(plan)
    is GenerationAttempt.FailOver -> ChainAttempt.FailOver(reason, message, retryAfterSeconds)
    is GenerationAttempt.HardFail -> ChainAttempt.HardFail(message)
}
