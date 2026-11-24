package com.space.app.domain.generation

import com.space.app.domain.model.NodeKind
import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanEdge
import com.space.app.domain.model.PlanNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object PlanJsonDecoder {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decode(raw: String): Result<Plan> = runCatching {
        val payload = json.decodeFromString(PlanDto.serializer(), JsonExtract.extract(raw))
        Plan(
            title = payload.title.trim().ifEmpty { "Untitled plan" },
            nodes = payload.nodes.map { it.toNode() },
            edges = payload.edges.map {
                PlanEdge(from = it.from, to = it.to, label = it.label)
            }
        )
    }

    @Serializable
    private data class PlanDto(
        val title: String = "Untitled plan",
        val nodes: List<NodeDto> = emptyList(),
        val edges: List<EdgeDto> = emptyList()
    )

    @Serializable
    private data class NodeDto(
        val id: String,
        val title: String,
        val summary: String = "",
        val detail: String = "",
        val kind: String = "step",
        val subtasks: List<String> = emptyList()
    ) {
        fun toNode(): PlanNode = PlanNode(
            id = id.trim(),
            title = title.trim(),
            summary = summary.trim(),
            detail = detail.trim(),
            kind = parseKind(kind),
            subtasks = subtasks.map { it.trim() }.filter { it.isNotEmpty() }
        )
    }

    @Serializable
    private data class EdgeDto(
        val from: String,
        val to: String,
        val label: String? = null
    )

    private fun parseKind(raw: String): NodeKind = when (raw.trim().lowercase()) {
        "goal" -> NodeKind.GOAL
        "decision" -> NodeKind.DECISION
        "risk" -> NodeKind.RISK
        else -> NodeKind.STEP
    }
}
