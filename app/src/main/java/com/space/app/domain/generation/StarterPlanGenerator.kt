package com.space.app.domain.generation

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.model.NodeKind
import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanEdge
import com.space.app.domain.model.PlanNode

/**
 * The offline starter plan: a generic but valid Goal -> Research -> Plan ->
 * Build -> Review shape that echoes the user's own words back at them.
 *
 * This is the last-resort generator described in the plan (section 3). It is
 * deliberately not the default once real providers exist - it exists so the app
 * never dead-ends when every configured key is exhausted.
 */
class StarterPlanGenerator : ValidatingPlanGenerator() {

    override fun buildPlan(brief: IdeaBrief): Plan {
        val answerNote = brief.answers
            .filter { it.answer.isNotBlank() }
            .joinToString(" ") { "${it.answer}." }
            .trim()

        val goalDetail = buildString {
            val idea = brief.idea.trim()
            if (idea.isNotEmpty()) appendLine(idea)
            if (answerNote.isNotEmpty()) {
                appendLine()
                append("What you told me: ")
                append(answerNote)
            }
        }.trim()

        return Plan(
            title = goalTitle(brief.idea),
            nodes = listOf(
                PlanNode(
                    id = "goal",
                    title = goalTitle(brief.idea),
                    summary = "The outcome this plan is organised around.",
                    detail = goalDetail,
                    kind = NodeKind.GOAL
                ),
                PlanNode(
                    id = "research",
                    title = "Research the ground",
                    summary = "Find out what already exists and what it costs.",
                    detail = "Before committing, spend a fixed amount of time looking " +
                        "at how others have done this, what they charge, and what " +
                        "went wrong for them.",
                    kind = NodeKind.STEP,
                    subtasks = listOf(
                        "Find three comparable examples",
                        "Note what they charge",
                        "Write down what surprises you"
                    )
                ),
                PlanNode(
                    id = "sketch",
                    title = "Sketch the plan",
                    summary = "A rough path from here to the outcome.",
                    detail = "Write the smallest version of the plan that could " +
                        "actually work. If it needs more than a page, it is too big " +
                        "to start.",
                    kind = NodeKind.STEP
                ),
                PlanNode(
                    id = "risks",
                    title = "Name the risks",
                    summary = "What would stop this, and how likely is it?",
                    detail = "List the things that could kill the idea. For each one, " +
                        "decide now whether you would push through it or walk away - " +
                        "deciding in advance is much easier than deciding in the moment.",
                    kind = NodeKind.RISK
                ),
                PlanNode(
                    id = "first",
                    title = "Choose the first step",
                    summary = "The smallest move that proves the idea.",
                    detail = "Pick the step with the highest information per hour spent. " +
                        "It should be possible to finish it this week.",
                    kind = NodeKind.DECISION
                ),
                PlanNode(
                    id = "build",
                    title = "Build the first version",
                    summary = "Make the smallest real thing.",
                    detail = "Build only enough to find out whether the idea holds up. " +
                        "Resist adding anything that does not answer a question you " +
                        "actually have.",
                    kind = NodeKind.STEP,
                    subtasks = listOf(
                        "Set a deadline you can keep",
                        "Ship something a person can use",
                        "Keep a note of what you changed"
                    )
                ),
                PlanNode(
                    id = "review",
                    title = "Review and adjust",
                    summary = "Compare against the goal, then decide what is next.",
                    detail = "Look back at the goal node. Did the first version move " +
                        "toward it? Then either iterate, scale up, or stop - all three " +
                        "are valid answers.",
                    kind = NodeKind.GOAL
                )
            ),
            edges = listOf(
                PlanEdge(from = "goal", to = "research"),
                PlanEdge(from = "goal", to = "sketch"),
                PlanEdge(from = "research", to = "risks", label = "what could go wrong"),
                PlanEdge(from = "sketch", to = "first", label = "pick one"),
                PlanEdge(from = "risks", to = "build", label = "if it still holds"),
                PlanEdge(from = "first", to = "build", label = "go"),
                PlanEdge(from = "build", to = "review")
            )
        )
    }

    companion object {
        /** Node titles are capped at six words by the plan schema. */
        const val MAX_TITLE_WORDS = 6

        fun goalTitle(idea: String): String {
            val words = idea.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(MAX_TITLE_WORDS)
            if (words.isEmpty()) return "Your idea"
            return words.joinToString(" ").replaceFirstChar { it.uppercase() }
        }
    }
}
