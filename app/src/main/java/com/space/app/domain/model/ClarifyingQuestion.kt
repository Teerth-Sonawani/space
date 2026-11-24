package com.space.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ClarifyingQuestion(
    val id: String,
    val prompt: String,
    val suggestions: List<String> = emptyList()
)
