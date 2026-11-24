package com.space.app.domain.generation.prompts

import com.space.app.domain.model.IdeaBrief

object PlanPrompts {

    val system: String = """
        You turn a rough idea and clarifying answers into a flowchart plan as JSON only.
        Output a single JSON object with keys: title, nodes, edges.
        nodes: array of {id, title, summary, detail, kind, subtasks?}
        - id: short unique slug
        - title: at most 6 words
        - summary: one short line
        - detail: markdown string (can be empty)
        - kind: one of goal, step, decision, risk
        - subtasks: optional string array
        edges: array of {from, to, label?} where from/to are node ids.
        Hard rules: 6–16 nodes, exactly one start node (no incoming edges), acyclic graph,
        every edge references real ids. Do not include positions or layout.
        Reply with JSON only — no markdown fences, no commentary.
    """.trimIndent()

    fun user(brief: IdeaBrief): String = buildString {
        appendLine("Idea:")
        appendLine(brief.idea.trim())
        if (brief.answers.isNotEmpty()) {
            appendLine()
            appendLine("Answers:")
            brief.answers.forEach { answer ->
                append("- ")
                append(answer.question)
                append(": ")
                appendLine(answer.answer)
            }
        }
    }

    fun repair(brief: IdeaBrief, previousOutput: String, errors: List<String>): String =
        buildString {
            appendLine("Your previous JSON failed validation:")
            errors.forEach { append("- ").appendLine(it) }
            appendLine()
            appendLine("Previous output:")
            appendLine(previousOutput.take(6000))
            appendLine()
            appendLine("Fix the JSON so it passes all rules. JSON only.")
            appendLine()
            append(user(brief))
        }
}
