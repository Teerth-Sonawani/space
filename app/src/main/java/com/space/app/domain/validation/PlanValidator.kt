package com.space.app.domain.validation

import com.space.app.domain.model.Plan

/**
 * Hard checks every plan must pass before it is allowed to render.
 * A failed validation is treated the same as malformed model output:
 * it goes through the repair-then-failover path in the AI layer.
 */
sealed interface PlanValidation {
    data class Valid(val plan: Plan) : PlanValidation
    data class Invalid(val errors: List<String>) : PlanValidation
}

object PlanValidator {

    const val MIN_NODES = 6
    const val MAX_NODES = 16
    const val MAX_TITLE_WORDS = 6

    fun validate(plan: Plan): PlanValidation {
        val errors = mutableListOf<String>()

        if (plan.nodes.size < MIN_NODES) {
            errors += "Plan needs at least $MIN_NODES nodes (has ${plan.nodes.size})"
        }
        if (plan.nodes.size > MAX_NODES) {
            errors += "Plan exceeds $MAX_NODES nodes (has ${plan.nodes.size})"
        }

        val ids = plan.nodes.map { it.id }
        val duplicates = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            errors += "Duplicate node ids: ${duplicates.joinToString()}"
        }

        plan.nodes.forEach { node ->
            val words = node.title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size > MAX_TITLE_WORDS) {
                errors += "Node '${node.id}' title exceeds $MAX_TITLE_WORDS words"
            }
        }

        val idSet = ids.toSet()
        plan.edges.forEach { edge ->
            if (edge.from !in idSet) errors += "Edge references unknown node '${edge.from}'"
            if (edge.to !in idSet) errors += "Edge references unknown node '${edge.to}'"
        }

        val startCount = plan.nodes.count { node -> plan.edges.none { it.to == node.id } }
        if (startCount != 1) {
            errors += "Plan must have exactly one start node (found $startCount)"
        }

        if (hasCycle(plan)) {
            errors += "Plan graph contains a cycle"
        }

        return if (errors.isEmpty()) PlanValidation.Valid(plan) else PlanValidation.Invalid(errors)
    }

    private fun hasCycle(plan: Plan): Boolean {
        val outgoing = plan.edges.groupBy({ it.from }, { it.to })
        val state = HashMap<String, Int>() // 1 = visiting, 2 = done

        fun visit(id: String): Boolean {
            when (state[id]) {
                1 -> return true
                2 -> return false
            }
            state[id] = 1
            for (next in outgoing[id].orEmpty()) {
                if (visit(next)) return true
            }
            state[id] = 2
            return false
        }

        return plan.nodes.any { visit(it.id) }
    }
}
