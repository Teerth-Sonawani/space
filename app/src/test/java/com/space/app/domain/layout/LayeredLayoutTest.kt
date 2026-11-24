package com.space.app.domain.layout

import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanEdge
import com.space.app.domain.model.PlanNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredLayoutTest {

    private fun node(id: String) = PlanNode(id = id, title = id, summary = "s")

    private fun plan(
        nodes: List<PlanNode>,
        edges: List<PlanEdge>
    ) = Plan(title = "t", nodes = nodes, edges = edges)

    @Test
    fun `an empty plan lays out to an empty chart instead of throwing`() {
        val layout = LayeredLayout.layout(Plan(title = "t", nodes = emptyList(), edges = emptyList()))
        assertTrue(layout.positions.isEmpty())
        assertEquals(0f, layout.width, 0.001f)
        assertEquals(0f, layout.height, 0.001f)
    }

    @Test
    fun `chain of three ranks in order`() {
        val p = plan(
            nodes = listOf(node("a"), node("b"), node("c"), node("d"), node("e"), node("f")),
            edges = listOf(
                PlanEdge("a", "b"), PlanEdge("b", "c"), PlanEdge("c", "d"),
                PlanEdge("d", "e"), PlanEdge("e", "f")
            )
        )
        val layout = LayeredLayout.layout(p)
        assertEquals(0, layout.positions.getValue("a").rank)
        assertEquals(1, layout.positions.getValue("b").rank)
        assertEquals(2, layout.positions.getValue("c").rank)
        assertEquals(3, layout.positions.getValue("d").rank)
        assertEquals(4, layout.positions.getValue("e").rank)
        assertEquals(5, layout.positions.getValue("f").rank)
    }

    @Test
    fun `rank is longest path not shallowest`() {
        // a -> c and a -> b -> c: c must sit at rank 2.
        val p = plan(
            nodes = listOf(node("a"), node("b"), node("c"), node("d"), node("e"), node("f")),
            edges = listOf(
                PlanEdge("a", "b"), PlanEdge("b", "c"), PlanEdge("a", "c"),
                PlanEdge("c", "d"), PlanEdge("d", "e"), PlanEdge("e", "f")
            )
        )
        val layout = LayeredLayout.layout(p)
        assertEquals(2, layout.positions.getValue("c").rank)
    }

    @Test
    fun `every node is positioned`() {
        val nodes = (1..10).map { node("n$it") }
        val edges = (1 until 10).map { PlanEdge("n$it", "n${it + 1}") }
        val layout = LayeredLayout.layout(plan(nodes, edges))
        assertEquals(nodes.size, layout.positions.size)
        nodes.forEach { assertTrue(layout.positions.containsKey(it.id)) }
    }

    @Test
    fun `no two nodes overlap`() {
        val nodes = (1..10).map { node("n$it") }
        val edges = (1 until 10).map { PlanEdge("n$it", "n${it + 1}") } +
            PlanEdge("n1", "n5") + PlanEdge("n2", "n6")
        val layout = LayeredLayout.layout(plan(nodes, edges))
        val coords = layout.positions.values.map { it.x to it.y }
        assertEquals(coords.size, coords.toSet().size)
    }

    @Test
    fun `layout is deterministic`() {
        val nodes = (1..10).map { node("n$it") }
        val edges = (1 until 10).map { PlanEdge("n$it", "n${it + 1}") } +
            PlanEdge("n1", "n4") + PlanEdge("n3", "n7")
        val p = plan(nodes, edges)
        assertEquals(LayeredLayout.layout(p), LayeredLayout.layout(p))
    }

    @Test
    fun `canvas bounds contain all nodes`() {
        val nodes = (1..12).map { node("n$it") }
        val edges = (1 until 12).map { PlanEdge("n$it", "n${it + 1}") }
        val layout = LayeredLayout.layout(plan(nodes, edges))
        layout.positions.values.forEach {
            assertTrue(it.x >= 0f)
            assertTrue(it.y >= 0f)
            assertTrue(it.x + LayeredLayout.NODE_WIDTH <= layout.width)
            assertTrue(it.y + LayeredLayout.NODE_HEIGHT <= layout.height)
        }
    }

    @Test
    fun `siblings share a rank and sit side by side`() {
        val p = plan(
            nodes = (1..6).map { node("n$it") },
            edges = listOf(
                PlanEdge("n1", "n2"), PlanEdge("n1", "n3"), PlanEdge("n1", "n4"),
                PlanEdge("n2", "n5"), PlanEdge("n4", "n5"), PlanEdge("n5", "n6")
            )
        )
        val layout = LayeredLayout.layout(p)
        val rank1 = layout.positions.values.filter { it.rank == 1 }
        assertEquals(3, rank1.size)
        val xs = rank1.map { it.x }.sorted()
        assertTrue(xs[1] > xs[0] + LayeredLayout.NODE_WIDTH)
        assertTrue(xs[2] > xs[1] + LayeredLayout.NODE_WIDTH)
        assertEquals(rank1[0].y, rank1[1].y, 0.01f)
    }
}
