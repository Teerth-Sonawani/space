package com.space.app.domain.generation

import com.space.app.data.KeyHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHealthStoreTest {

    private var now = 1_000L
    private val store = KeyHealthStore { now }

    @Test
    fun `a source with no history is Active`() {
        assertEquals(KeyHealth.Active, store.status("s1"))
    }

    @Test
    fun `a cooling source stays cool until the window passes`() {
        store.markCooling("s1", untilEpochMs = 2_000L)

        assertTrue(store.status("s1") is KeyHealth.Cooling)

        now = 2_001L
        assertEquals(KeyHealth.Active, store.status("s1"))
    }

    @Test
    fun `an expired cooling entry clears itself`() {
        store.markCooling("s1", untilEpochMs = 2_000L)
        now = 5_000L
        store.status("s1")

        assertTrue(store.health.value.isEmpty())
    }

    @Test
    fun `invalid persists and is not time-limited`() {
        store.markInvalid("s1")
        now = Long.MAX_VALUE / 2

        assertEquals(KeyHealth.Invalid, store.status("s1"))
    }

    @Test
    fun `clear and clearAll reset health`() {
        store.markInvalid("s1")
        store.markCooling("s2", untilEpochMs = 9_999L)

        store.clear("s1")
        assertEquals(KeyHealth.Active, store.status("s1"))
        assertTrue(store.status("s2") is KeyHealth.Cooling)

        store.clearAll()
        assertTrue(store.health.value.isEmpty())
    }

    @Test
    fun `applyFailOver honors Retry-After on a rate limit`() {
        store.applyFailOver("s1", FailOverKind.RATE_LIMIT, retryAfterSeconds = 90, now = now)

        assertEquals(now + 90_000L, (store.status("s1") as KeyHealth.Cooling).untilEpochMs)
    }

    @Test
    fun `applyFailOver falls back to the default window without Retry-After`() {
        store.applyFailOver("s1", FailOverKind.RATE_LIMIT, retryAfterSeconds = null, now = now)

        assertEquals(
            now + DEFAULT_COOLDOWN_SECONDS * 1000L,
            (store.status("s1") as KeyHealth.Cooling).untilEpochMs
        )
    }

    @Test
    fun `applyFailOver invalidates on auth`() {
        store.applyFailOver("s1", FailOverKind.AUTH, retryAfterSeconds = null, now = now)

        assertEquals(KeyHealth.Invalid, store.status("s1"))
    }

    @Test
    fun `applyFailOver soft-cools transient failures for a short window`() {
        for (kind in listOf(
            FailOverKind.SERVER,
            FailOverKind.NETWORK,
            FailOverKind.TIMEOUT,
            FailOverKind.MALFORMED
        )) {
            val store = KeyHealthStore { now }
            store.applyFailOver("s1", kind, retryAfterSeconds = null, now = now)

            assertEquals(
                "short soft cool for $kind",
                now + SOFT_COOL_MS,
                (store.status("s1") as KeyHealth.Cooling).untilEpochMs
            )
        }
    }
}
