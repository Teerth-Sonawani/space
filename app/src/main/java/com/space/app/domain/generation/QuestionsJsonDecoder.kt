package com.space.app.domain.generation

import com.space.app.domain.model.ClarifyingQuestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object QuestionsJsonDecoder {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decode(raw: String): Result<List<ClarifyingQuestion>> = runCatching {
        val payload = json.decodeFromString(QuestionsDto.serializer(), JsonExtract.extract(raw))
        payload.questions
            .mapIndexed { index, q ->
                ClarifyingQuestion(
                    id = q.id.trim().ifEmpty { "q${index + 1}" },
                    prompt = q.prompt.trim(),
                    suggestions = q.suggestions.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
                )
            }
            .filter { it.prompt.isNotEmpty() }
            .take(4)
            .also { list ->
                require(list.size in 2..4) {
                    "Need 2–4 questions, got ${list.size}"
                }
            }
    }

    @Serializable
    private data class QuestionsDto(
        val questions: List<QuestionDto> = emptyList()
    )

    @Serializable
    private data class QuestionDto(
        val id: String = "",
        val prompt: String = "",
        val suggestions: List<String> = emptyList()
    )
}
