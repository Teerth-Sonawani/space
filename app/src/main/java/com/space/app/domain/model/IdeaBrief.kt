package com.space.app.domain.model

import kotlinx.serialization.Serializable

/** One answered clarification question. */
@Serializable
data class Answer(
    val question: String,
    val answer: String
)

/**
 * Everything the user has told us before a plan is generated: the raw idea and
 * whatever came back from the clarifying questions. This is the only input a
 * [com.space.app.domain.generation.PlanGenerator] ever sees.
 */
@Serializable
data class IdeaBrief(
    val idea: String,
    val answers: List<Answer> = emptyList()
)
