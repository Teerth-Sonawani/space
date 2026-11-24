package com.space.app.domain.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLAN section 3's HTTP status table, in one place and three representations. */
class HttpFailOverTest {

    @Test
    fun `401 and 403 invalidate the key and try the next source`() {
        for (code in listOf(401, 403)) {
            val decision = HttpFailOver.from(code)
            assertTrue("code $code", decision is FailOverDecision.TryNext)
            assertEquals(FailOverKind.AUTH, (decision as FailOverDecision.TryNext).kind)
        }
    }

    @Test
    fun `429 carries Retry-After through to the cooldown`() {
        val decision = HttpFailOver.from(429, retryAfterSeconds = 12) as FailOverDecision.TryNext

        assertEquals(FailOverKind.RATE_LIMIT, decision.kind)
        assertEquals(12L, decision.retryAfterSeconds)
    }

    @Test
    fun `5xx is a server failure that fails over`() {
        for (code in listOf(500, 502, 503, 599)) {
            val decision = HttpFailOver.from(code) as FailOverDecision.TryNext
            assertEquals("code $code", FailOverKind.SERVER, decision.kind)
        }
    }

    @Test
    fun `400 stops the chain instead of hiding a prompt bug behind failover`() {
        val decision = HttpFailOver.from(400)

        assertTrue(decision is FailOverDecision.Stop)
        assertTrue((decision as FailOverDecision.Stop).message.contains("400"))
    }

    @Test
    fun `unexpected statuses fail over rather than deadlock`() {
        val decision = HttpFailOver.from(418) as FailOverDecision.TryNext

        assertEquals("HTTP 418", decision.message)
    }

    @Test
    fun `converting to a generation attempt keeps kind, message and Retry-After`() {
        val attempt = HttpFailOver.from(429, 30).toGenerationAttempt()

        assertTrue(attempt is GenerationAttempt.FailOver)
        attempt as GenerationAttempt.FailOver
        assertEquals(FailOverKind.RATE_LIMIT, attempt.reason)
        assertEquals(30L, attempt.retryAfterSeconds)
    }

    @Test
    fun `converting a stop becomes a hard fail on both call types`() {
        val generation = HttpFailOver.from(400).toGenerationAttempt()
        val questions = HttpFailOver.from(400).toQuestionsAttempt()

        assertTrue(generation is GenerationAttempt.HardFail)
        assertTrue(questions is QuestionsAttempt.HardFail)
    }

    @Test
    fun `converting a try-next keeps the table aligned for the questions call`() {
        val attempt = HttpFailOver.from(401).toQuestionsAttempt() as QuestionsAttempt.FailOver

        assertEquals(FailOverKind.AUTH, attempt.reason)
    }
}
