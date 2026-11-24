package com.space.app.domain.validation

import com.space.app.domain.model.NodeKind
import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanEdge
import com.space.app.domain.model.PlanNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {

    private fun node(id: String) = PlanNode(id = id, title = id, summary = "s")

    private fun chainPlan(size: Int, edges: List<PlanEdge> = emptyList()): Plan {
        val nodes = (1..size).map { node("n$it") }
        val chain = edges.ifEmpty {
            (1 until size).map { PlanEdge(from = "n$it", to = "n${it + 1}") }
        }
        return Plan(title = "t", nodes = nodes, edges = chain)
    }

    @Test
    fun `sample-style chain plan is valid`() {
        val result = PlanValidator.validate(chainPlan(8))
        assertTrue(result is PlanValidation.Valid)
    }

    @Test
    fun `too few nodes is invalid`() {
        val result = PlanValidator.validate(chainPlan(PlanValidator.MIN_NODES - 1))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("at least") })
    }

    @Test
    fun `too many nodes is invalid`() {
        val result = PlanValidator.validate(chainPlan(PlanValidator.MAX_NODES + 1))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("exceeds") })
    }

    @Test
    fun `duplicate ids are invalid`() {
        val nodes = listOf(node("a"), node("a"), node("b"), node("c"), node("d"), node("e"))
        val edges = listOf(
            PlanEdge("a", "b"), PlanEdge("b", "c"), PlanEdge("c", "d"),
            PlanEdge("d", "e"), PlanEdge("e", "a")
        )
        val result = PlanValidator.validate(Plan("t", nodes, edges))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("Duplicate") })
    }

    @Test
    fun `dangling edge is invalid`() {
        val nodes = (1..8).map { node("n$it") }
        val edges = (1 until 8).map { PlanEdge("n$it", "n${it + 1}") } +
            PlanEdge("n1", "ghost")
        val result = PlanValidator.validate(Plan("t", nodes, edges))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("unknown node 'ghost'") })
    }

    @Test
    fun `cycle is invalid`() {
        val nodes = (1..8).map { node("n$it") }
        val edges = (1 until 8).map { PlanEdge("n$it", "n${it + 1}") } + PlanEdge("n8", "n1")
        val result = PlanValidator.validate(Plan("t", nodes, edges))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("cycle") })
    }

    @Test
    fun `self-loop is invalid`() {
        val nodes = (1..8).map { node("n$it") }
        val edges = (1 until 8).map { PlanEdge("n$it", "n${it + 1}") } + PlanEdge("n3", "n3")
        val result = PlanValidator.validate(Plan("t", nodes, edges))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("cycle") })
    }

    @Test
    fun `two start nodes are invalid`() {
        // Two independent chains, neither reaching the other.
        val nodes = (1..12).map { node("n$it") }
        val edges = listOf(
            PlanEdge("n1", "n2"), PlanEdge("n2", "n3"), PlanEdge("n3", "n4"),
            PlanEdge("n4", "n5"), PlanEdge("n5", "n6"),
            PlanEdge("n7", "n8"), PlanEdge("n8", "n9"), PlanEdge("n9", "n10"),
            PlanEdge("n10", "n11"), PlanEdge("n11", "n12")
        )
        val result = PlanValidator.validate(Plan("t", nodes, edges))
        assertTrue(result is PlanValidation.Invalid)
        assertTrue((result as PlanValidation.Invalid).errors.any { it.contains("exactly one start") })
    }

    @Test
    fun `all four node kinds validate`() {
        val kinds = listOf(NodeKind.GOAL, NodeKind.STEP, NodeKind.DECISION, NodeKind.RISK)
        val nodes = (1..8).map { i ->
            PlanNode(id = "n$i", title = "n$i", summary = "s", kind = kinds[i % kinds.size])
        }
        val edges = (1 until 8).map { PlanEdge("n$it", "n${it + 1}") }
        val result = PlanValidator.validate(Plan("t", nodes, edges))
        assertEquals(PlanValidation.Valid(Plan("t", nodes, edges)), result)
    }
}
