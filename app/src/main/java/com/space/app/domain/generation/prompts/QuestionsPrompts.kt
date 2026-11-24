package com.space.app.domain.generation.prompts

object QuestionsPrompts {

    val system: String = """
        You ask sharp clarifying questions before planning.
        Return JSON only: {"questions":[{"id":"q1","prompt":"...","suggestions":["a","b","c"]}]}
        Rules: 2–4 questions, each with 2–3 short tappable suggestions.
        Questions must be concrete and specific to the idea, not generic fluff.
        No markdown fences, no commentary.
    """.trimIndent()

    fun user(idea: String): String =
        "Idea:\n${idea.trim()}\n\nWrite clarifying questions as JSON."

    /** Second attempt after the model answered with JSON we could not use. */
    fun repair(idea: String, previousOutput: String, errors: String): String = """
        Idea:
        ${idea.trim()}

        Your previous answer was unusable:
        $errors

        Previous answer:
        ${previousOutput.take(1200)}

        Return only corrected JSON in the original shape:
        {"questions":[{"id":"q1","prompt":"...","suggestions":["a","b","c"]}]}
        2–4 questions, 2–3 suggestions each. No markdown fences, no commentary.
    """.trimIndent()
}
