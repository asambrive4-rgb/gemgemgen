// 역할: Jetpack DataStore와 JSON 직렬화를 사용해 다중 제미나이 계정 프로필 목록(수동 소진·사용 금지 플래그 포함), 대표 계정 선택 상태 및 계정별 마지막 성공 사용량 스냅샷을 영구 저장합니다.
package com.example.gemgemgen.usage.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.usage.domain.GeminiAccountGroup
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private val Context.multiGeminiUsageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreMultiGeminiUsageDataSource.DATASTORE_NAME
)

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

            PersistedMultiGeminiUsageSnapshot(
                profiles = profiles,
                cachedUsageByAccountId = cachedMap,
                selectedAccountId = selectedAccountId
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
        val jsonString = serializeProfilesJson(normalized)

        dataStore.edit { preferences ->
            preferences[KEY_ACCOUNTS_JSON] = jsonString
            if (selectedAccountId.isNullOrBlank()) {
                preferences.remove(KEY_SELECTED_ACCOUNT_ID)
            } else {
                preferences[KEY_SELECTED_ACCOUNT_ID] = selectedAccountId
            }
        }
    }

    override suspend fun saveAccountSuccess(accountId: String, state: GeminiUsageState.Success) {
        if (accountId.isBlank()) return
        dataStore.edit { preferences ->
            val currentMap = parseCachedUsagesJson(preferences[KEY_CACHED_USAGES_JSON]).toMutableMap()
            currentMap[accountId] = state.copy(
                fiveHourPercent = state.fiveHourPercent.coerceIn(0, 100),
                weeklyPercent = state.weeklyPercent.coerceIn(0, 100)
            )
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

    companion object {
        const val DATASTORE_NAME = "multi_gemini_usage_store"

        val KEY_ACCOUNTS_JSON = stringPreferencesKey("multi_gemini_accounts_json")
        val KEY_CACHED_USAGES_JSON = stringPreferencesKey("multi_gemini_cached_usages_json")
        val KEY_SELECTED_ACCOUNT_ID = stringPreferencesKey("multi_gemini_selected_account_id")

        private const val FIELD_ID = "id"
        private const val FIELD_ALIAS = "alias"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_SORT_ORDER = "sort_order"
        private const val FIELD_WEBVIEW_PROFILE_NAME = "webview_profile_name"
        private const val FIELD_CREATED_AT = "created_at"
        private const val FIELD_GROUP = "group"
        private const val FIELD_MANUAL_EXHAUSTED_UNTIL = "manual_exhausted_until"
        private const val FIELD_IS_SUSPENDED = "is_suspended"

        private const val FIELD_FIVE_HOUR_PERCENT = "five_hour_percent"
        private const val FIELD_FIVE_HOUR_RESET_TEXT = "five_hour_reset_text"
        private const val FIELD_WEEKLY_PERCENT = "weekly_percent"
        private const val FIELD_WEEKLY_RESET_TEXT = "weekly_reset_text"
        private const val FIELD_UPDATED_AT = "updated_at"

        fun serializeProfilesJson(profiles: List<GeminiAccountProfile>): String {
            val array = buildJsonArray {
                profiles.forEach { profile ->
                    add(
                        buildJsonObject {
                            put(FIELD_ID, profile.id)
                            put(FIELD_ALIAS, profile.alias)
                            put(FIELD_EMAIL, profile.email)
                            put(FIELD_SORT_ORDER, profile.sortOrder)
                            put(FIELD_WEBVIEW_PROFILE_NAME, profile.webViewProfileName)
                            put(FIELD_CREATED_AT, profile.createdAt)
                            put(FIELD_GROUP, profile.group.name)
                            put(FIELD_MANUAL_EXHAUSTED_UNTIL, profile.manualExhaustedUntilMillis)
                            put(FIELD_IS_SUSPENDED, profile.isSuspended)
                        }
                    )
                }
            }
            return array.toString()
        }

        fun parseProfilesJson(jsonString: String?): List<GeminiAccountProfile> {
            if (jsonString.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = Json.parseToJsonElement(jsonString).jsonArray
                array.mapIndexedNotNull { index, element ->
                    val obj = element.jsonObject
                    val id = obj[FIELD_ID]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (id.isEmpty()) return@mapIndexedNotNull null
                    val alias = obj[FIELD_ALIAS]?.jsonPrimitive?.content?.trim().orEmpty()
                    val email = obj[FIELD_EMAIL]?.jsonPrimitive?.content?.trim().orEmpty()
                    val sortOrder = obj[FIELD_SORT_ORDER]?.jsonPrimitive?.intOrNull ?: index
                    val webViewProfileName = obj[FIELD_WEBVIEW_PROFILE_NAME]
                        ?.jsonPrimitive
                        ?.content
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: GeminiAccountProfile.buildWebViewProfileName(id)
                    val createdAt = obj[FIELD_CREATED_AT]?.jsonPrimitive?.longOrNull ?: 0L
                    val groupStr = obj[FIELD_GROUP]?.jsonPrimitive?.content?.trim().orEmpty()
                    val group: GeminiAccountGroup = runCatching { GeminiAccountGroup.valueOf(groupStr) }
                        .getOrDefault(GeminiAccountGroup.MAIN)
                    val manualExhaustedUntilMillis = obj[FIELD_MANUAL_EXHAUSTED_UNTIL]?.jsonPrimitive?.longOrNull ?: 0L
                    val isSuspended = obj[FIELD_IS_SUSPENDED]?.jsonPrimitive?.booleanOrNull ?: false

                    GeminiAccountProfile(
                        id = id,
                        alias = alias.ifEmpty { "계정 ${index + 1}" },
                        email = email,
                        sortOrder = sortOrder,
                        webViewProfileName = webViewProfileName,
                        createdAt = createdAt,
                        group = group,
                        manualExhaustedUntilMillis = manualExhaustedUntilMillis,
                        isSuspended = isSuspended
                    )
                }.sortedBy { it.sortOrder }
                    .mapIndexed { idx, profile -> profile.copy(sortOrder = idx) }
            }.getOrDefault(emptyList())
        }

        fun serializeCachedUsagesJson(
            usagesByAccountId: Map<String, GeminiUsageState.Success>
        ): String {
            val rootObject = buildJsonObject {
                usagesByAccountId.forEach { (accountId, success) ->
                    if (accountId.isNotBlank()) {
                        put(
                            accountId,
                            buildJsonObject {
                                put(FIELD_FIVE_HOUR_PERCENT, success.fiveHourPercent.coerceIn(0, 100))
                                put(FIELD_FIVE_HOUR_RESET_TEXT, success.fiveHourResetText)
                                put(FIELD_WEEKLY_PERCENT, success.weeklyPercent.coerceIn(0, 100))
                                put(FIELD_WEEKLY_RESET_TEXT, success.weeklyResetText)
                                put(FIELD_UPDATED_AT, success.updatedAt)
                            }
                        )
                    }
                }
            }
            return rootObject.toString()
        }

        fun parseCachedUsagesJson(
            jsonString: String?
        ): Map<String, GeminiUsageState.Success> {
            if (jsonString.isNullOrBlank()) return emptyMap()
            return runCatching {
                val rootObject = Json.parseToJsonElement(jsonString).jsonObject
                val result = linkedMapOf<String, GeminiUsageState.Success>()
                rootObject.forEach { (accountId, element) ->
                    if (accountId.isBlank()) return@forEach
                    val obj = element.jsonObject
                    val fiveHourPercent = obj[FIELD_FIVE_HOUR_PERCENT]?.jsonPrimitive?.intOrNull
                        ?: return@forEach
                    val weeklyPercent = obj[FIELD_WEEKLY_PERCENT]?.jsonPrimitive?.intOrNull
                        ?: return@forEach
                    val fiveHourResetText = obj[FIELD_FIVE_HOUR_RESET_TEXT]?.jsonPrimitive?.content.orEmpty()
                    val weeklyResetText = obj[FIELD_WEEKLY_RESET_TEXT]?.jsonPrimitive?.content.orEmpty()
                    val updatedAt = obj[FIELD_UPDATED_AT]?.jsonPrimitive?.longOrNull ?: 0L

                    result[accountId] = GeminiUsageState.Success(
                        fiveHourPercent = fiveHourPercent.coerceIn(0, 100),
                        fiveHourResetText = fiveHourResetText,
                        weeklyPercent = weeklyPercent.coerceIn(0, 100),
                        weeklyResetText = weeklyResetText,
                        updatedAt = updatedAt
                    )
                }
                result
            }.getOrDefault(emptyMap())
        }
    }
}
