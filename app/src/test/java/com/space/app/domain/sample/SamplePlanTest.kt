package com.space.app.domain.sample

import com.space.app.domain.layout.LayeredLayout
import com.space.app.domain.validation.PlanValidation
import com.space.app.domain.validation.PlanValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample plan is what the chart screenshots and the M1 demo run on, so it
 * must satisfy exactly the same rules the AI output will have to satisfy (M3).
 * Without this, a hand-edit to the sample could break the chart at runtime.
 */
class SamplePlanTest {

    @Test
    fun `sample plan passes the same validation the AI output must pass`() {
        when (val result = PlanValidator.validate(SamplePlan.plan)) {
            is PlanValidation.Valid -> Unit
            is PlanValidation.Invalid ->
                throw AssertionError("Sample plan is invalid: ${result.errors.joinToString()}")
        }
    }

    @Test
    fun `every sample node is positioned by the layout`() {
        val layout = LayeredLayout.layout(SamplePlan.plan)
        assertEquals(SamplePlan.plan.nodes.size, layout.positions.size)
        SamplePlan.plan.nodes.forEach { node ->
            assertTrue("no position computed for '${node.id}'", layout.positions.containsKey(node.id))
        }
    }

    @Test
    fun `sample node titles respect the six word schema limit`() {
        SamplePlan.plan.nodes.forEach { node ->
            val words = node.title.trim().split(Regex("\\s+")).size
            assertTrue("'${node.title}' has $words words (limit 6)", words <= 6)
        }
    }

    @Test
    fun `every sample edge moves down exactly one rank or more`() {
        // The bezier drawing assumes a source sits above its target. If an edge
        // ever pointed upward the curve would fold back on itself.
        val layout = LayeredLayout.layout(SamplePlan.plan)
        SamplePlan.plan.edges.forEach { edge ->
            val from = layout.positions.getValue(edge.from)
            val to = layout.positions.getValue(edge.to)
            assertTrue(
                "edge ${edge.from} -> ${edge.to} goes from rank ${from.rank} to ${to.rank}",
                to.rank > from.rank
            )
        }
    }

    @Test
    fun `sample edge labels stay short enough to draw on the chart`() {
        SamplePlan.plan.edges.mapNotNull { it.label }.forEach { label ->
            assertTrue("'$label' is too long for an edge label", label.length <= 24)
        }
    }

    @Test
    fun `sample plan stays inside the sixteen node cap`() {
        assertTrue(
            "sample has ${SamplePlan.plan.nodes.size} nodes",
            SamplePlan.plan.nodes.size <= PlanValidator.MAX_NODES
        )
    }
}
