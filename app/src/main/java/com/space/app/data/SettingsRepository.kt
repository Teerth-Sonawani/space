package com.space.app.data

import kotlinx.coroutines.flow.Flow

/**
 * Ordered API sources and encrypted key material. Metadata is safe to observe;
 * [getApiKey] is the only way to read a raw key and must never be logged.
 */
interface SettingsRepository {
    /** Sources newest-added may sit anywhere; list is always sorted by [ApiSource.order]. */
    val sources: Flow<List<ApiSource>>

    suspend fun addSource(provider: ProviderKind, label: String, apiKey: String): ApiSource

    suspend fun removeSource(id: String)

    /** Reorders so [ids] become order 0..n-1. Unknown ids are ignored. */
    suspend fun reorder(ids: List<String>)

    /** Raw key for [id], or null if missing. */
    suspend fun getApiKey(id: String): String?
}
