package com.space.app.domain.layout

import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanEdge
import com.space.app.domain.model.PlanNode

/** A node with its computed place in the chart. x/y are in dp units. */
data class PositionedNode(
    val node: PlanNode,
    val rank: Int,
    val orderInRank: Int,
    val x: Float,
    val y: Float
)

data class ChartLayout(
    val positions: Map<String, PositionedNode>,
    val edges: List<PlanEdge>,
    val width: Float,
    val height: Float,
    val maxRank: Int
)

/**
 * Layered top-to-bottom layout.
 * Rank = longest path from the single start node. Nodes within a rank are
 * ordered by the barycenter of their predecessors' orders to reduce edge
 * crossings, then centered horizontally. Deterministic for a given plan.
 */
object LayeredLayout {

    const val NODE_WIDTH = 148f
    const val NODE_HEIGHT = 68f
    const val H_GAP = 44f
    const val V_GAP = 92f
    const val MARGIN = 36f

    fun layout(plan: Plan): ChartLayout {
        // A plan with no nodes cannot be ranked; return an empty chart rather
        // than letting maxOf/keys.last() throw on the empty case.
        if (plan.nodes.isEmpty()) {
            return ChartLayout(
                positions = emptyMap(),
                edges = plan.edges,
                width = 0f,
                height = 0f,
                maxRank = 0
            )
        }

        val incoming = plan.edges.groupBy({ it.to }, { it.from })
        val outgoing = plan.edges.groupBy({ it.from }, { it.to })

        // Topological order (Kahn). The validator guarantees acyclicity.
        val inDegree = HashMap<String, Int>()
        plan.nodes.forEach { inDegree[it.id] = incoming[it.id].orEmpty().size }
        val queue = ArrayDeque<String>()
        plan.nodes.forEach { if (inDegree[it.id] == 0) queue.add(it.id) }
        val topoOrder = ArrayList<String>(plan.nodes.size)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            topoOrder.add(id)
            for (next in outgoing[id].orEmpty()) {
                val d = inDegree.getValue(next) - 1
                inDegree[next] = d
                if (d == 0) queue.add(next)
            }
        }

        // Longest-path ranking.
        val rank = HashMap<String, Int>()
        topoOrder.forEach { id ->
            val r = rank[id] ?: 0
            rank[id] = r
            for (next in outgoing[id].orEmpty()) {
                rank[next] = maxOf(rank[next] ?: 0, r + 1)
            }
        }

        // Order within each rank: barycenter of predecessors' orders, stable
        // fallback to input order for nodes without predecessors.
        val inputIndex = plan.nodes.withIndex().associate { (i, n) -> n.id to i }
        val orderInRank = HashMap<String, Int>()
        val byRank = plan.nodes.groupBy { rank.getValue(it.id) }.toSortedMap()

        byRank.forEach { (_, nodesInRank) ->
            val sorted = nodesInRank.sortedBy { n ->
                val preds = incoming[n.id].orEmpty()
                if (preds.isEmpty()) inputIndex.getValue(n.id).toFloat()
                else preds.map { orderInRank[it] ?: inputIndex.getValue(it) }.average().toFloat()
            }
            sorted.forEachIndexed { i, n -> orderInRank[n.id] = i }
        }

        // Position: each rank centered inside the widest rank.
        val contentWidth = byRank.values.maxOf { nodesInRank ->
            nodesInRank.size * NODE_WIDTH + (nodesInRank.size - 1) * H_GAP
        }
        val maxRank = byRank.keys.last()
        val width = contentWidth + 2 * MARGIN
        val height = (maxRank + 1) * NODE_HEIGHT + maxRank * V_GAP + 2 * MARGIN

        val positions = HashMap<String, PositionedNode>(plan.nodes.size)
        byRank.forEach { (r, nodesInRank) ->
            val rankWidth = nodesInRank.size * NODE_WIDTH + (nodesInRank.size - 1) * H_GAP
            val startX = MARGIN + (contentWidth - rankWidth) / 2f
            nodesInRank.forEachIndexed { i, n ->
                positions[n.id] = PositionedNode(
                    node = n,
                    rank = r,
                    orderInRank = i,
                    x = startX + i * (NODE_WIDTH + H_GAP),
                    y = MARGIN + r * (NODE_HEIGHT + V_GAP)
                )
            }
        }

        return ChartLayout(
            positions = positions,
            edges = plan.edges,
            width = width,
            height = height,
            maxRank = maxRank
        )
    }
}
