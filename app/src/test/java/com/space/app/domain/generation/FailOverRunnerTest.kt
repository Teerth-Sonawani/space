package com.space.app.domain.generation

import com.space.app.data.KeyHealth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PLAN section 3's failover table, tested against the shared core both chains
 * use. If a case here fails, both the plan call and the questions call changed
 * behaviour together.
 */
class FailOverRunnerTest {

    private var now = 1_000_000L
    private val health = KeyHealthStore { now }

    private suspend fun runChain(
        vararg ids: String,
        perAttemptTimeoutMs: Long = 5_000L,
        chainBudgetMs: Long = 60_000L,
        call: suspend (String) -> ChainAttempt<String>
    ): ChainOutcome<String> = runFailOverChain(
        sourceIds = ids.toList(),
        health = health,
        clock = { now },
        perAttemptTimeoutMs = perAttemptTimeoutMs,
        chainBudgetMs = chainBudgetMs,
        call = call
    )

    @Test
    fun `no sources fails without attempting anything`() = runTest {
        var called = false
        val outcome = runChain { called = true; ChainAttempt.Success("x") }

        assertTrue(outcome is ChainOutcome.Failed)
        assertFalse(called)
        assertFalse((outcome as ChainOutcome.Failed).attemptedAny)
    }

    @Test
    fun `first healthy source wins and stops the chain`() = runTest {
        var calls = 0
        val outcome = runChain("s1", "s2") {
            calls++
            ChainAttempt.Success("plan-from-$it")
        }

        assertEquals(ChainOutcome.Ok("plan-from-s1"), outcome)
        assertEquals(1, calls)
    }

    @Test
    fun `rate limit cools the key for Retry-After and the next key is used`() = runTest {
        val outcome = runChain("s1", "s2") { id ->
            if (id == "s1") {
                ChainAttempt.FailOver(FailOverKind.RATE_LIMIT, "Rate limited", retryAfterSeconds = 120)
            } else {
                ChainAttempt.Success("ok")
            }
        }

        assertEquals(ChainOutcome.Ok("ok"), outcome)
        val cooling = health.status("s1")
        assertTrue(cooling is KeyHealth.Cooling)
        assertEquals(now + 120_000L, (cooling as KeyHealth.Cooling).untilEpochMs)
    }

    @Test
    fun `rate limit without Retry-After uses the default cooldown`() = runTest {
        runChain("s1", "s2") { id ->
            if (id == "s1") {
                ChainAttempt.FailOver(FailOverKind.RATE_LIMIT, "Rate limited")
            } else {
                ChainAttempt.Success("ok")
            }
        }

        val cooling = health.status("s1") as KeyHealth.Cooling
        assertEquals(now + DEFAULT_COOLDOWN_SECONDS * 1000L, cooling.untilEpochMs)
    }

    @Test
    fun `auth failure invalidates the key for the rest of the process`() = runTest {
        runChain("s1", "s2") { id ->
            if (id == "s1") ChainAttempt.FailOver(FailOverKind.AUTH, "Key rejected (401)")
            else ChainAttempt.Success("ok")
        }

        assertEquals(KeyHealth.Invalid, health.status("s1"))
    }

    @Test
    fun `server error retries the same key once before failing over`() = runTest {
        val attempts = mutableListOf<String>()
        val outcome = runChain("s1", "s2") { id ->
            attempts += id
            if (id == "s1") ChainAttempt.FailOver(FailOverKind.SERVER, "Server error (503)")
            else ChainAttempt.Success("ok")
        }

        assertEquals(ChainOutcome.Ok("ok"), outcome)
        assertEquals(listOf("s1", "s1", "s2"), attempts)
    }

    @Test
    fun `network and timeout failures fail over without a same-key retry`() = runTest {
        val attempts = mutableListOf<String>()
        val outcome = runChain("s1", "s2") { id ->
            attempts += id
            if (id == "s1") ChainAttempt.FailOver(FailOverKind.NETWORK, "unreachable")
            else ChainAttempt.Success("ok")
        }

        assertEquals(ChainOutcome.Ok("ok"), outcome)
        assertEquals(listOf("s1", "s2"), attempts)
        assertTrue(health.status("s1") is KeyHealth.Cooling)
    }

    @Test
    fun `hard fail stops the chain without touching the next key`() = runTest {
        var secondCalled = false
        val outcome = runChain("s1", "s2") { id ->
            if (id == "s1") ChainAttempt.HardFail("Bad request (prompt or payload bug)")
            else {
                secondCalled = true
                ChainAttempt.Success("ok")
            }
        }

        assertTrue(outcome is ChainOutcome.Failed)
        assertFalse(secondCalled)
        assertEquals("Bad request (prompt or payload bug)", (outcome as ChainOutcome.Failed).reason)
        assertTrue(outcome.attemptedAny)
    }

    @Test
    fun `cooling and invalid sources are skipped entirely`() = runTest {
        health.markCooling("s1", now + 1_000_000L)
        health.markInvalid("s2")
        val attempted = mutableListOf<String>()

        val outcome = runChain("s1", "s2", "s3") { id ->
            attempted += id
            ChainAttempt.Success("from-$id")
        }

        assertEquals(ChainOutcome.Ok("from-s3"), outcome)
        assertEquals(listOf("s3"), attempted)
    }

    @Test
    fun `a source that hangs is treated as a timeout and the chain moves on`() = runTest {
        val outcome = runChain("s1", "s2", perAttemptTimeoutMs = 100L) { id ->
            if (id == "s1") {
                kotlinx.coroutines.delay(10_000)
                ChainAttempt.Success("too-late")
            } else {
                ChainAttempt.Success("ok")
            }
        }

        assertEquals(ChainOutcome.Ok("ok"), outcome)
        assertTrue(health.status("s1") is KeyHealth.Cooling)
    }

    @Test
    fun `exhausting every source reports the last reason and that keys were tried`() = runTest {
        val outcome = runChain("s1", "s2") {
            ChainAttempt.FailOver(FailOverKind.AUTH, "Key rejected (401)")
        }

        assertTrue(outcome is ChainOutcome.Failed)
        outcome as ChainOutcome.Failed
        assertEquals("Key rejected (401)", outcome.reason)
        assertTrue(outcome.attemptedAny)
    }

    @Test
    fun `spent budget stops the chain before the next source starts`() = runTest {
        val attempted = mutableListOf<String>()
        val outcome = runChain("s1", "s2", chainBudgetMs = 1_000L) { id ->
            attempted += id
            now += 2_000L // this attempt alone blows the budget
            ChainAttempt.FailOver(FailOverKind.RATE_LIMIT, "Rate limited")
        }

        assertTrue(outcome is ChainOutcome.Failed)
        assertEquals(listOf("s1"), attempted)
        assertEquals("Timed out trying sources", (outcome as ChainOutcome.Failed).reason)
    }
}
