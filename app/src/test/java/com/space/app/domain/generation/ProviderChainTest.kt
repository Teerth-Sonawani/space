package com.space.app.domain.generation

import com.space.app.domain.model.IdeaBrief
import com.space.app.domain.validation.PlanValidation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN section 10's failover exit tests, end to end through [ProviderChain],
 * the prompt/JSON layer and the shared failover core.
 */
class ProviderChainTest {

    private val brief = IdeaBrief(idea = "Open a coffee cart", answers = emptyList())
    private val health = KeyHealthStore { 1_000_000L }

    private fun chain(
        clients: Map<String, FakeLlmClient>,
        health: KeyHealthStore = this.health,
        autoStarterWhenEmpty: Boolean = true,
        allowStarterAfterExhaustion: Boolean = false
    ) = ProviderChain(
        providers = {
            clients.map { (id, client) -> ChainedProvider(id, LlmPlanGenerator(client)) }
        },
        health = health,
        starter = StarterPlanGenerator(),
        clock = { 1_000_000L },
        perAttemptTimeoutMs = 5_000L,
        chainBudgetMs = 60_000L
    )

    @Test
    fun `no keys configured falls back to the starter plan without a network call`() = runTest {
        val result = chain(emptyMap()).generateChain(brief, autoStarterWhenEmpty = true)

        assertTrue(result is ChainResult.Ok)
        result as ChainResult.Ok
        assertTrue(result.usedStarter)
        assertTrue(result.plan.nodes.isNotEmpty())
    }

    @Test
    fun `no keys configured can also fail with a starter offer`() = runTest {
        val result = chain(emptyMap()).generateChain(brief, autoStarterWhenEmpty = false)

        assertTrue(result is ChainResult.Failed)
        result as ChainResult.Failed
        assertTrue(result.canUseStarter)
        assertEquals("No API keys configured", result.reason)
    }

    @Test
    fun `401 on the first key invalidates it and the second key answers`() = runTest {
        val first = FakeLlmClient(LlmResult.HttpError(401, "unauthorized"))
        val second = FakeLlmClient(LlmResult.Ok(validPlanJson))

        val result = chain(mapOf("s1" to first, "s2" to second)).generateChain(brief)

        assertTrue(result is ChainResult.Ok)
        assertFalse((result as ChainResult.Ok).usedStarter)
        assertEquals(1, first.calls)
        assertTrue(health.status("s1") is com.space.app.data.KeyHealth.Invalid)
    }

    @Test
    fun `429 on the first key cools it down and the second key answers`() = runTest {
        val first = FakeLlmClient(LlmResult.HttpError(429, "slow down", retryAfterSeconds = 30))
        val second = FakeLlmClient(LlmResult.Ok(validPlanJson))

        val result = chain(mapOf("s1" to first, "s2" to second)).generateChain(brief)

        assertTrue(result is ChainResult.Ok)
        val cooling = health.status("s1") as com.space.app.data.KeyHealth.Cooling
        assertEquals(1_000_000L + 30_000L, cooling.untilEpochMs)
    }

    @Test
    fun `5xx retries the same key exactly once before failing over`() = runTest {
        val first = FakeLlmClient(
            LlmResult.HttpError(503, "unavailable"),
            LlmResult.HttpError(503, "unavailable")
        )
        val second = FakeLlmClient(LlmResult.Ok(validPlanJson))

        val result = chain(mapOf("s1" to first, "s2" to second)).generateChain(brief)

        assertTrue(result is ChainResult.Ok)
        assertEquals("the first key is retried exactly once", 2, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun `network error fails over to the next key`() = runTest {
        val first = FakeLlmClient(LlmResult.NetworkError(java.io.IOException("offline")))
        val second = FakeLlmClient(LlmResult.Ok(validPlanJson))

        val result = chain(mapOf("s1" to first, "s2" to second)).generateChain(brief)

        assertTrue(result is ChainResult.Ok)
        assertEquals(1, first.calls)
    }

    @Test
    fun `400 stops the chain and never touches the second key`() = runTest {
        val first = FakeLlmClient(LlmResult.HttpError(400, "bad payload"))
        val second = FakeLlmClient(LlmResult.Ok(validPlanJson))

        val result = chain(mapOf("s1" to first, "s2" to second)).generateChain(brief)

        assertTrue(result is ChainResult.Failed)
        result as ChainResult.Failed
        assertEquals(1, first.calls)
        assertEquals(0, second.calls)
        assertTrue(result.canUseStarter)
    }

    @Test
    fun `malformed output gets one repair retry then fails over`() = runTest {
        val first = FakeLlmClient(
            LlmResult.Ok("this is not json"),
            LlmResult.Ok("still not json")
        )
        val second = FakeLlmClient(LlmResult.Ok(validPlanJson))

        val result = chain(mapOf("s1" to first, "s2" to second)).generateChain(brief)

        assertTrue(result is ChainResult.Ok)
        assertEquals("one repair attempt, then fail over", 2, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun `every key failing reports the failure and offers the starter`() = runTest {
        val only = FakeLlmClient(LlmResult.HttpError(401, "unauthorized"))

        val result = chain(mapOf("s1" to only)).generateChain(brief)

        assertTrue(result is ChainResult.Failed)
        assertTrue((result as ChainResult.Failed).canUseStarter)
    }

    @Test
    fun `exhaustion can go straight to the starter when the caller allows it`() = runTest {
        val only = FakeLlmClient(LlmResult.HttpError(429, "slow down"))

        val result = chain(mapOf("s1" to only))
            .generateChain(brief, allowStarterAfterExhaustion = true)

        assertTrue(result is ChainResult.Ok)
        assertTrue((result as ChainResult.Ok).usedStarter)
    }

    @Test
    fun `generate adapts the chain result to PlanValidation`() = runTest {
        val client = FakeLlmClient(LlmResult.Ok(validPlanJson))
        val validation = chain(mapOf("s1" to client)).generate(brief)

        assertTrue(validation is PlanValidation.Valid)
        assertEquals("Coffee cart plan", (validation as PlanValidation.Valid).plan.title)
    }

    @Test
    fun `generate still yields a starter plan when nothing is configured`() = runTest {
        val validation = chain(emptyMap()).generate(brief)

        assertTrue(validation is PlanValidation.Valid)
        assertTrue((validation as PlanValidation.Valid).plan.nodes.isNotEmpty())
    }
}
