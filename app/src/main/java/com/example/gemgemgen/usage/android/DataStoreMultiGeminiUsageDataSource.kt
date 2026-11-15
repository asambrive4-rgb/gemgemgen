// 역할: Jetpack DataStore와 kotlinx.serialization을 사용해 다중 제미나이 계정 프로필 목록(수동 소진·사용 금지 플래그 포함), 대표 계정 선택 상태, 메인만 사용 모드 및 계정별 마지막 성공 사용량 스냅샷을 영구 저장합니다.
package com.example.gemgemgen.usage.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.usecase.MultiGeminiUsageLocalDataSource
import com.example.gemgemgen.usage.usecase.PersistedMultiGeminiUsageSnapshot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.multiGeminiUsageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreMultiGeminiUsageDataSource.DATASTORE_NAME
)

private val usageJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

class DataStoreMultiGeminiUsageDataSource(
    private val dataStore: DataStore<Preferences>
) : MultiGeminiUsageLocalDataSource {
    constructor(context: Context) : this(context.applicationContext.multiGeminiUsageDataStore)

    val snapshotFlow: Flow<PersistedMultiGeminiUsageSnapshot> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val profiles = parseProfilesJson(preferences[KEY_ACCOUNTS_JSON])
            val cachedMap = parseCachedUsagesJson(preferences[KEY_CACHED_USAGES_JSON])
            val selectedAccountId = preferences[KEY_SELECTED_ACCOUNT_ID]?.takeIf { it.isNotBlank() }
            val isMainOnlyMode = preferences[KEY_IS_MAIN_ONLY_MODE] ?: false

            PersistedMultiGeminiUsageSnapshot(
                profiles = profiles,
                cachedUsageByAccountId = cachedMap,
                selectedAccountId = selectedAccountId,
                isMainOnlyMode = isMainOnlyMode
            )
        }

    override val selectedAccountIdFlow: Flow<String?> = snapshotFlow
        .map { it.selectedAccountId }
        .distinctUntilChanged()

    override suspend fun loadSnapshot(): PersistedMultiGeminiUsageSnapshot {
        return snapshotFlow.first()
    }

    override suspend fun saveProfiles(
        profiles: List<GeminiAccountProfile>,
        selectedAccountId: String?
    ) {
        val normalized = profiles
            .sortedBy { it.sortOrder }
            .mapIndexed { index, profile -> profile.copy(sortOrder = index) }
        dataStore.edit { preferences ->
            preferences[KEY_ACCOUNTS_JSON] = serializeProfilesJson(normalized)
            if (selectedAccountId != null) {
                preferences[KEY_SELECTED_ACCOUNT_ID] = selectedAccountId
            } else {
                val current = preferences[KEY_SELECTED_ACCOUNT_ID]
                if (current == null || normalized.none { it.id == current }) {
                    val fallback = normalized.firstOrNull()?.id
                    if (fallback != null) {
                        preferences[KEY_SELECTED_ACCOUNT_ID] = fallback
                    } else {
                        preferences.remove(KEY_SELECTED_ACCOUNT_ID)
                    }
                }
            }
        }
    }

    override suspend fun saveAccountSuccess(accountId: String, state: GeminiUsageState.Success) {
        if (accountId.isBlank()) return
        dataStore.edit { preferences ->
            val currentMap = parseCachedUsagesJson(preferences[KEY_CACHED_USAGES_JSON]).toMutableMap()
            currentMap[accountId] = state
            preferences[KEY_CACHED_USAGES_JSON] = serializeCachedUsagesJson(currentMap)
        }
    }

    override suspend fun removeAccountData(accountId: String) {
        if (accountId.isBlank()) return
        dataStore.edit { preferences ->
            val currentProfiles = parseProfilesJson(preferences[KEY_ACCOUNTS_JSON])
                .filterNot { it.id == accountId }
                .mapIndexed { index, profile -> profile.copy(sortOrder = index) }
            preferences[KEY_ACCOUNTS_JSON] = serializeProfilesJson(currentProfiles)

            val currentMap = parseCachedUsagesJson(preferences[KEY_CACHED_USAGES_JSON]).toMutableMap()
            currentMap.remove(accountId)
            preferences[KEY_CACHED_USAGES_JSON] = serializeCachedUsagesJson(currentMap)

            if (preferences[KEY_SELECTED_ACCOUNT_ID] == accountId) {
                val fallbackId = currentProfiles.firstOrNull()?.id
                if (fallbackId != null) {
                    preferences[KEY_SELECTED_ACCOUNT_ID] = fallbackId
                } else {
                    preferences.remove(KEY_SELECTED_ACCOUNT_ID)
                }
            }
        }
    }

    override suspend fun saveSelectedAccountId(accountId: String?) {
        dataStore.edit { preferences ->
            if (accountId.isNullOrBlank()) {
                preferences.remove(KEY_SELECTED_ACCOUNT_ID)
            } else {
                preferences[KEY_SELECTED_ACCOUNT_ID] = accountId
            }
        }
    }

    override suspend fun saveMainOnlyMode(isMainOnlyMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_MAIN_ONLY_MODE] = isMainOnlyMode
        }
    }

    suspend fun selectAccountByEmailOrAlias(email: String, alias: String): String? {
        val normEmail = email.trim().lowercase()
        val normAlias = alias.trim().lowercase()
        if (normEmail.isBlank() && normAlias.isBlank()) return null

        var matchedId: String? = null
        dataStore.edit { preferences ->
            val profiles = parseProfilesJson(preferences[KEY_ACCOUNTS_JSON])
            val matched = profiles.firstOrNull { profile ->
                normEmail.isNotBlank() && profile.email.trim().lowercase() == normEmail
            } ?: profiles.firstOrNull { profile ->
                val localPart = profile.email.trim().lowercase().substringBefore("@")
                val targetLocal = normEmail.substringBefore("@")
                targetLocal.isNotBlank() && localPart.isNotBlank() && localPart == targetLocal
            } ?: profiles.firstOrNull { profile ->
                normAlias.isNotBlank() && profile.alias.trim().lowercase() == normAlias
            }
            if (matched != null) {
                preferences[KEY_SELECTED_ACCOUNT_ID] = matched.id
                matchedId = matched.id
            }
        }
        return matchedId
    }

    @Serializable
    private data class CachedUsageDto(
        @SerialName("five_hour_percent") val fiveHourPercent: Int,
        @SerialName("five_hour_reset_text") val fiveHourResetText: String = "",
        @SerialName("weekly_percent") val weeklyPercent: Int,
        @SerialName("weekly_reset_text") val weeklyResetText: String = "",
        @SerialName("updated_at") val updatedAt: Long = 0L
    ) {
        fun toDomain(): GeminiUsageState.Success = GeminiUsageState.Success(
            fiveHourPercent = fiveHourPercent.coerceIn(0, 100),
            fiveHourResetText = fiveHourResetText,
            weeklyPercent = weeklyPercent.coerceIn(0, 100),
            weeklyResetText = weeklyResetText,
            updatedAt = updatedAt
        )

        companion object {
            fun fromDomain(success: GeminiUsageState.Success): CachedUsageDto = CachedUsageDto(
                fiveHourPercent = success.fiveHourPercent.coerceIn(0, 100),
                fiveHourResetText = success.fiveHourResetText,
                weeklyPercent = success.weeklyPercent.coerceIn(0, 100),
                weeklyResetText = success.weeklyResetText,
                updatedAt = success.updatedAt
            )
        }
    }

    companion object {
        const val DATASTORE_NAME = "multi_gemini_usage_store"

        val KEY_ACCOUNTS_JSON = stringPreferencesKey("multi_gemini_accounts_json")
        val KEY_CACHED_USAGES_JSON = stringPreferencesKey("multi_gemini_cached_usages_json")
        val KEY_SELECTED_ACCOUNT_ID = stringPreferencesKey("multi_gemini_selected_account_id")
        val KEY_IS_MAIN_ONLY_MODE = booleanPreferencesKey("multi_gemini_is_main_only_mode")

        fun serializeProfilesJson(profiles: List<GeminiAccountProfile>): String {
            return usageJson.encodeToString(profiles)
        }

        fun parseProfilesJson(jsonString: String?): List<GeminiAccountProfile> {
            if (jsonString.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = usageJson.decodeFromString<List<GeminiAccountProfile>>(jsonString)
                array.mapIndexedNotNull { index, profile ->
                    if (profile.id.isBlank()) return@mapIndexedNotNull null
                    profile.copy(
                        sortOrder = profile.sortOrder,
                        alias = profile.alias.ifEmpty { "계정 ${index + 1}" }
                    )
                }.sortedBy { it.sortOrder }
                    .mapIndexed { idx, profile -> profile.copy(sortOrder = idx) }
            }.getOrDefault(emptyList())
        }

        fun serializeCachedUsagesJson(
            usagesByAccountId: Map<String, GeminiUsageState.Success>
        ): String {
            val dtoMap = usagesByAccountId
                .filterKeys { it.isNotBlank() }
                .mapValues { CachedUsageDto.fromDomain(it.value) }
            return usageJson.encodeToString(dtoMap)
        }

        fun parseCachedUsagesJson(
            jsonString: String?
        ): Map<String, GeminiUsageState.Success> {
            if (jsonString.isNullOrBlank()) return emptyMap()
            return runCatching {
                usageJson.decodeFromString<Map<String, CachedUsageDto>>(jsonString)
                    .filterKeys { it.isNotBlank() }
                    .mapValues { it.value.toDomain() }
            }.getOrDefault(emptyMap())
        }
    }
}
