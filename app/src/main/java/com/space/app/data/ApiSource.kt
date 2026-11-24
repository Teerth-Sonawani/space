package com.space.app.data

import kotlinx.serialization.Serializable

/**
 * Metadata for one configured API key. The raw key never lives here — only a
 * last-4 hint for the UI. Order is priority for [com.space.app.domain.generation.ProviderChain].
 */
@Serializable
data class ApiSource(
    val id: String,
    val provider: ProviderKind,
    val label: String,
    val maskedKeyHint: String,
    val order: Int
)

sealed interface KeyHealth {
    data object Active : KeyHealth
    data class Cooling(val untilEpochMs: Long) : KeyHealth
    data object Invalid : KeyHealth
}
