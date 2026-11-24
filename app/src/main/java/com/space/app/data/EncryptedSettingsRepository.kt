package com.space.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.settingsDataStore by preferencesDataStore(name = "space_settings")

/**
 * Metadata in DataStore; raw keys in EncryptedSharedPreferences (Android Keystore).
 * Prefs file is excluded from backup via [backup_rules.xml].
 */
class EncryptedSettingsRepository(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SettingsRepository {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore
    private val keyPrefs: SharedPreferences = createEncryptedPrefs(appContext)

    override val sources: Flow<List<ApiSource>> = dataStore.data.map { prefs ->
        val raw = prefs[SOURCES_KEY].orEmpty()
        if (raw.isBlank()) emptyList()
        else {
            runCatching {
                json.decodeFromString(ListSerializer(ApiSource.serializer()), raw)
                    .sortedBy { it.order }
            }.getOrDefault(emptyList())
        }
    }

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
            maskedKeyHint = InMemorySettingsRepository.mask(trimmed),
            order = 0
        )
        keyPrefs.edit().putString(keyName(id), trimmed).apply()
        dataStore.edit { prefs ->
            val current = decodeList(prefs[SOURCES_KEY])
            val bumped = current.map { it.copy(order = it.order + 1) }
            // New source goes first (highest priority).
            prefs[SOURCES_KEY] = encodeList(listOf(source) + bumped)
        }
        return source.copy(order = 0)
    }

    override suspend fun removeSource(id: String) {
        keyPrefs.edit().remove(keyName(id)).apply()
        dataStore.edit { prefs ->
            val updated = decodeList(prefs[SOURCES_KEY])
                .filterNot { it.id == id }
                .mapIndexed { index, source -> source.copy(order = index) }
            prefs[SOURCES_KEY] = encodeList(updated)
        }
    }

    override suspend fun reorder(ids: List<String>) {
        dataStore.edit { prefs ->
            val byId = decodeList(prefs[SOURCES_KEY]).associateBy { it.id }
            val reordered = ids.mapNotNull { byId[it] }
                .mapIndexed { index, source -> source.copy(order = index) }
            val leftover = byId.values
                .filterNot { it.id in ids }
                .mapIndexed { index, source -> source.copy(order = reordered.size + index) }
            prefs[SOURCES_KEY] = encodeList(reordered + leftover)
        }
    }

    override suspend fun getApiKey(id: String): String? =
        keyPrefs.getString(keyName(id), null)

    private fun decodeList(raw: String?): List<ApiSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ApiSource.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun encodeList(list: List<ApiSource>): String =
        json.encodeToString(ListSerializer(ApiSource.serializer()), list)

    companion object {
        private val SOURCES_KEY = stringPreferencesKey("api_sources")
        private const val ENCRYPTED_PREFS = "space_api_keys"

        private fun keyName(id: String) = "key_$id"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
