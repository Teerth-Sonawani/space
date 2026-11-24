package com.space.app.domain.generation

import com.space.app.domain.generation.prompts.QuestionsPrompts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The questions call must obey the same table as the plan call, so it is
 * tested through the same scenarios against [QuestionsChain].
 */
class QuestionsChainTest {

    private val health = KeyHealthStore { 1_000_000L }

    private fun chain(clients: Map<String, FakeLlmClient>) = QuestionsChain(
        providers = {
            clients.map { (id, client) -> ChainedQuestionsProvider(id, QuestionsGenerator(client)) }
        },
        health = health,
        clock = { 1_000_000L },
        perAttemptTimeoutMs = 5_000L,
        chainBudgetMs = 60_000L
    )

    @Test
    fun `first key answers with the decoded questions`() = runTest {
        val client = FakeLlmClient(LlmResult.Ok(questionsJson(count = 3)))

        val result = chain(mapOf("s1" to client)).generate("Open a coffee cart")

        assertTrue(result is QuestionsChainResult.Ok)
        val questions = (result as QuestionsChainResult.Ok).questions
        assertEquals(3, questions.size)
        assertEquals("Question number 1?", questions.first().prompt)
    }

    @Test
    fun `no keys configured fails with a clear reason`() = runTest {
        val result = chain(emptyMap()).generate("Anything")

        assertTrue(result is QuestionsChainResult.Failed)
        assertEquals(
            "No API keys configured",
            (result as QuestionsChainResult.Failed).reason
        )
    }

    @Test
    fun `429 on the first key cools it and the second key answers`() = runTest {
        val first = FakeLlmClient(LlmResult.HttpError(429, "slow down", retryAfterSeconds = 45))
        val second = FakeLlmClient(LlmResult.Ok(questionsJson(count = 2)))

        val result = chain(mapOf("s1" to first, "s2" to second)).generate("Idea")

        assertTrue(result is QuestionsChainResult.Ok)
        assertEquals(2, (result as QuestionsChainResult.Ok).questions.size)
        assertTrue(health.status("s1") is com.space.app.data.KeyHealth.Cooling)
    }

    @Test
    fun `401 invalidates the first key and fails over to the second`() = runTest {
        val first = FakeLlmClient(LlmResult.HttpError(401, "unauthorized"))
        val second = FakeLlmClient(LlmResult.Ok(questionsJson()))

        val result = chain(mapOf("s1" to first, "s2" to second)).generate("Idea")

        assertTrue(result is QuestionsChainResult.Ok)
        assertEquals(1, first.calls)
        assertTrue(health.status("s1") is com.space.app.data.KeyHealth.Invalid)
    }

    @Test
    fun `malformed JSON gets one repair retry before failing over`() = runTest {
        val first = FakeLlmClient(LlmResult.Ok("not json"), LlmResult.Ok("not json either"))
        val second = FakeLlmClient(LlmResult.Ok(questionsJson(count = 2)))

        val result = chain(mapOf("s1" to first, "s2" to second)).generate("Idea")

        assertTrue(result is QuestionsChainResult.Ok)
        assertEquals("initial call plus one repair", 2, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun `a repair that fixes the JSON succeeds without failing over`() = runTest {
        val client = FakeLlmClient(
            LlmResult.Ok("oops"),
            LlmResult.Ok(questionsJson(count = 4))
        )

        val result = chain(mapOf("s1" to client)).generate("Idea")

        assertTrue(result is QuestionsChainResult.Ok)
        assertEquals(4, (result as QuestionsChainResult.Ok).questions.size)
        assertEquals(2, client.calls)
    }

    @Test
    fun `every key failing reports the failure so the caller can fall back`() = runTest {
        val client = FakeLlmClient(LlmResult.HttpError(401, "unauthorized"))

        val result = chain(mapOf("s1" to client)).generate("Idea")

        assertTrue(result is QuestionsChainResult.Failed)
    }

    @Test
    fun `the repair prompt carries the idea, the bad output and the errors`() {
        val repair = QuestionsPrompts.repair(
            idea = "Ship a game",
            previousOutput = "garbage",
            errors = "Need 2–4 questions"
        )

        assertTrue(repair.contains("Ship a game"))
        assertTrue(repair.contains("garbage"))
        assertTrue(repair.contains("Need 2–4 questions"))
        assertTrue(repair.contains("\"questions\""))
    }
}
