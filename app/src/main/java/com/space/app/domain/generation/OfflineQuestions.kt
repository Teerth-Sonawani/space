package com.space.app.domain.generation

import com.space.app.domain.model.ClarifyingQuestion

/** Static pack used when the questions call fails or no keys are configured. */
object OfflineQuestions {
    val pack: List<ClarifyingQuestion> = listOf(
        ClarifyingQuestion(
            id = "audience",
            prompt = "Who is this for?",
            suggestions = listOf("Just me", "Friends and family", "Customers", "My team")
        ),
        ClarifyingQuestion(
            id = "time",
            prompt = "How much time do you have for the first step?",
            suggestions = listOf("A weekend", "Two weeks", "A month", "No deadline")
        ),
        ClarifyingQuestion(
            id = "success",
            prompt = "What does success look like six months in?",
            suggestions = listOf(
                "It pays for itself",
                "A steady side income",
                "A full-time thing",
                "I just want to finish it"
            )
        ),
        ClarifyingQuestion(
            id = "budget",
            prompt = "What is your budget to start?",
            suggestions = listOf("Almost nothing", "Under 500", "500 to 5000", "Whatever it takes")
        )
    )
}
