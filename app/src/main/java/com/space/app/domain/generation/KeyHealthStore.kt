package com.space.app.domain.generation

import com.space.app.data.KeyHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory per-source health with short TTL cooldowns. Survives for the
 * process lifetime so Settings can show Active / Cooling / Invalid without
 * hammering a dead key on every request.
 */
class KeyHealthStore(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val _health = MutableStateFlow<Map<String, KeyHealth>>(emptyMap())
    val health: StateFlow<Map<String, KeyHealth>> = _health.asStateFlow()

    fun status(sourceId: String): KeyHealth {
        val now = clock()
        return when (val current = _health.value[sourceId]) {
            is KeyHealth.Cooling -> {
                if (current.untilEpochMs <= now) {
                    clear(sourceId)
                    KeyHealth.Active
                } else {
                    current
                }
            }
            is KeyHealth.Invalid -> KeyHealth.Invalid
            else -> KeyHealth.Active
        }
    }

    fun markCooling(sourceId: String, untilEpochMs: Long) {
        update(sourceId, KeyHealth.Cooling(untilEpochMs))
    }

    fun markInvalid(sourceId: String) {
        update(sourceId, KeyHealth.Invalid)
    }

    fun clear(sourceId: String) {
        _health.value = _health.value - sourceId
    }

    fun clearAll() {
        _health.value = emptyMap()
    }

    private fun update(sourceId: String, value: KeyHealth) {
        _health.value = _health.value + (sourceId to value)
    }
}

/**
 * Applies one failed attempt to this store, per PLAN section 3: a rate limit
 * cools the key for `Retry-After` (or the default window), an auth failure
 * invalidates it for the rest of the process, and everything else gets a short
 * soft-cool so the same dead key is not hammered by the next request. Shared by
 * both chains so the table cannot drift between the two calls.
 */
fun KeyHealthStore.applyFailOver(
    sourceId: String,
    kind: FailOverKind,
    retryAfterSeconds: Long?,
    now: Long,
    defaultCooldownSeconds: Long = DEFAULT_COOLDOWN_SECONDS,
    softCoolMs: Long = SOFT_COOL_MS
) {
    when (kind) {
        FailOverKind.RATE_LIMIT -> markCooling(
            sourceId,
            now + (retryAfterSeconds ?: defaultCooldownSeconds) * 1000L
        )

        FailOverKind.AUTH -> markInvalid(sourceId)

        FailOverKind.SERVER,
        FailOverKind.NETWORK,
        FailOverKind.TIMEOUT,
        FailOverKind.MALFORMED -> markCooling(sourceId, now + softCoolMs)
    }
}

/** How long a key stays cool when the server did not tell us. */
const val DEFAULT_COOLDOWN_SECONDS = 60L

/** Soft-cool for transient failures, long enough to skip one retry, short enough to recover. */
const val SOFT_COOL_MS = 15_000L
