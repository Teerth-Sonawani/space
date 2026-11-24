package com.space.app.domain.generation

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.Plan
import com.space.app.domain.validation.PlanValidation
import com.space.app.domain.validation.PlanValidator

/**
 * Produces a plan from a brief.
 *
 * The UI only ever depends on this interface, so swapping the offline starter
 * for a real LLM provider (M3) or a provider chain with fallback (M5) changes
 * nothing above this line. Implementations must still hand their output to
 * [PlanValidator] - a generator that returns an invalid graph is a bug, not a
 * plan.
 */
interface PlanGenerator {
    suspend fun generate(brief: IdeaBrief): PlanValidation
}

/** Builds the plan and validates it, so every generator shares one gate. */
abstract class ValidatingPlanGenerator : PlanGenerator {

    /** Public so the plan shape can be asserted directly in tests. */
    abstract fun buildPlan(brief: IdeaBrief): Plan

    override suspend fun generate(brief: IdeaBrief): PlanValidation =
        PlanValidator.validate(buildPlan(brief))
}
