package com.space.app.domain.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionsJsonDecoderTest {

    @Test
    fun `decodes a plain payload`() {
        val questions = QuestionsJsonDecoder.decode(questionsJson(3)).getOrThrow()

        assertEquals(3, questions.size)
        assertEquals("q1", questions[0].id)
        assertEquals(listOf("a", "b"), questions[0].suggestions)
    }

    @Test
    fun `decodes a fenced payload`() {
        val questions = QuestionsJsonDecoder.decode("```json\n${questionsJson(2)}\n```").getOrThrow()

        assertEquals(2, questions.size)
    }

    @Test
    fun `caps the list at four questions`() {
        val questions = QuestionsJsonDecoder.decode(questionsJson(6)).getOrThrow()

        assertEquals(4, questions.size)
    }

    @Test
    fun `caps suggestions at three and drops blank ones`() {
        val json = """
            {"questions":[
              {"id":"q1","prompt":"One?","suggestions":["a","  ","b","c","d"]},
              {"id":"q2","prompt":"Two?","suggestions":[]}
            ]}
        """.trimIndent()

        val questions = QuestionsJsonDecoder.decode(json).getOrThrow()

        assertEquals(listOf("a", "b", "c"), questions[0].suggestions)
        assertTrue(questions[1].suggestions.isEmpty())
    }

    @Test
    fun `missing ids are generated in order`() {
        val json = """
            {"questions":[
              {"prompt":"One?","suggestions":["a"]},
              {"prompt":"Two?","suggestions":["b"]}
            ]}
        """.trimIndent()

        val questions = QuestionsJsonDecoder.decode(json).getOrThrow()

        assertEquals(listOf("q1", "q2"), questions.map { it.id })
    }

    @Test
    fun `fewer than two questions is rejected so the repair path can run`() {
        val json = """{"questions":[{"id":"q1","prompt":"Only one?","suggestions":["a"]}]}"""

        assertTrue(QuestionsJsonDecoder.decode(json).isFailure)
    }

    @Test
    fun `questions with blank prompts are dropped before the size check`() {
        val json = """
            {"questions":[
              {"id":"q1","prompt":"   ","suggestions":["a"]},
              {"id":"q2","prompt":"Good?","suggestions":["b"]},
              {"id":"q3","prompt":"Also good?","suggestions":["c"]}
            ]}
        """.trimIndent()

        val questions = QuestionsJsonDecoder.decode(json).getOrThrow()

        assertEquals(2, questions.size)
        assertTrue(questions.none { it.prompt.isBlank() })
    }

    @Test
    fun `garbage in is a failed result, not an exception`() {
        assertTrue(QuestionsJsonDecoder.decode("nope").isFailure)
        assertTrue(QuestionsJsonDecoder.decode("").isFailure)
    }
}
