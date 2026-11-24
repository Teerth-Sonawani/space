package com.space.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class NodeKind {
    GOAL,
    STEP,
    DECISION,
    RISK
}

@Serializable
data class PlanNode(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String = "",
    val kind: NodeKind = NodeKind.STEP,
    val subtasks: List<String> = emptyList()
)

@Serializable
data class PlanEdge(
    val from: String,
    val to: String,
    val label: String? = null
)

@Serializable
data class Plan(
    val title: String,
    val nodes: List<PlanNode>,
    val edges: List<PlanEdge>
)
