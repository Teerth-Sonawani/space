package com.space.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** JVM-friendly settings store for unit tests and offline demos. */
class InMemorySettingsRepository : SettingsRepository {

    private val _sources = MutableStateFlow<List<ApiSource>>(emptyList())
    private val keys = linkedMapOf<String, String>()

    override val sources: Flow<List<ApiSource>> = _sources.asStateFlow()

    override suspend fun addSource(
        provider: ProviderKind,
        label: String,
        apiKey: String
    ): ApiSource {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "API key is blank" }
        val id = UUID.randomUUID().toString()
        val source = ApiSource(
            id = id,
            provider = provider,
            label = label.trim().ifEmpty { provider.displayName() },
            maskedKeyHint = mask(trimmed),
            order = _sources.value.size
        )
        keys[id] = trimmed
        _sources.value = _sources.value + source
        return source
    }

    override suspend fun removeSource(id: String) {
        keys.remove(id)
        _sources.value = _sources.value
            .filterNot { it.id == id }
            .mapIndexed { index, source -> source.copy(order = index) }
    }

    override suspend fun reorder(ids: List<String>) {
        val byId = _sources.value.associateBy { it.id }
        val reordered = ids.mapNotNull { byId[it] }
            .mapIndexed { index, source -> source.copy(order = index) }
        val leftover = _sources.value
            .filterNot { it.id in ids }
            .mapIndexed { index, source -> source.copy(order = reordered.size + index) }
        _sources.value = reordered + leftover
    }

    override suspend fun getApiKey(id: String): String? = keys[id]

    companion object {
        fun mask(key: String): String {
            val trimmed = key.trim()
            if (trimmed.length <= 4) return "••••"
            return "••••" + trimmed.takeLast(4)
        }
    }
}
