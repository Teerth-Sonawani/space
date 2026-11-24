package com.space.app.domain.generation

import com.space.app.domain.model.Answer
import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.validation.PlanValidation
import com.space.app.domain.validation.PlanValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterPlanGeneratorTest {

    private val generator = StarterPlanGenerator()

    @Test
    fun `generate returns a valid plan for a plain idea`() = runBlocking {
        val result = generator.generate(IdeaBrief(idea = "Open a tiny bakery"))
        assertTrue(
            "generator produced an invalid plan: $result",
            result is PlanValidation.Valid
        )
    }

    @Test
    fun `buildPlan always satisfies the validator`() {
        val briefs = listOf(
            IdeaBrief(idea = ""),
            IdeaBrief(idea = "   "),
            IdeaBrief(idea = "Ship a small app"),
            IdeaBrief(idea = "One two three four five six seven eight nine ten"),
            IdeaBrief(
                idea = "Learn to bake sourdough",
                answers = listOf(
                    Answer("Who is this for?", "Just me"),
                    Answer("What is your budget to start?", "Almost nothing")
                )
            )
        )
        briefs.forEach { brief ->
            val validation = PlanValidator.validate(generator.buildPlan(brief))
            if (validation is PlanValidation.Invalid) {
                throw AssertionError("brief '$brief' produced: ${validation.errors}")
            }
        }
    }

    @Test
    fun `goal title is capped at six words`() {
        val title = StarterPlanGenerator.goalTitle(
            "One two three four five six seven eight nine ten"
        )
        assertEquals("One two three four five six", title)
    }

    @Test
    fun `blank idea still produces a usable goal title`() {
        assertEquals("Your idea", StarterPlanGenerator.goalTitle("   "))
    }

    @Test
    fun `answers are carried into the goal node detail`() {
        val plan = generator.buildPlan(
            IdeaBrief(
                idea = "Learn to bake sourdough",
                answers = listOf(Answer("Who is this for?", "Just me"))
            )
        )
        val goal = plan.nodes.first { it.id == "goal" }
        assertTrue(
            "answer missing from goal detail: '${goal.detail}'",
            goal.detail.contains("Just me")
        )
    }

    @Test
    fun `generated plan stays inside the node cap`() {
        val plan = generator.buildPlan(IdeaBrief(idea = "Anything"))
        assertTrue(
            "generated ${plan.nodes.size} nodes",
            plan.nodes.size <= PlanValidator.MAX_NODES
        )
        assertTrue(
            "generated ${plan.nodes.size} nodes",
            plan.nodes.size >= PlanValidator.MIN_NODES
        )
    }
}
