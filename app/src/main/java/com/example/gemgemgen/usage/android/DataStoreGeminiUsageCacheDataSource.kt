// 역할: Jetpack DataStore를 사용해 마지막으로 조회된 제미나이 사용량 데이터를 기기에 영구 캐싱합니다.
package com.example.gemgemgen.usage.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.usecase.GeminiUsageCacheDataSource
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.geminiUsageCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreGeminiUsageCacheDataSource.DATASTORE_NAME
)

class DataStoreGeminiUsageCacheDataSource(
    private val dataStore: DataStore<Preferences>
) : GeminiUsageCacheDataSource {
    constructor(context: Context) : this(context.applicationContext.geminiUsageCacheDataStore)

    val cachedUsageFlow: Flow<GeminiUsageState.Success?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val fiveHourPercent = preferences[KEY_FIVE_HOUR_PERCENT] ?: return@map null
            val weeklyPercent = preferences[KEY_WEEKLY_PERCENT] ?: return@map null
            val fiveHourResetText = preferences[KEY_FIVE_HOUR_RESET_TEXT].orEmpty()
            val weeklyResetText = preferences[KEY_WEEKLY_RESET_TEXT].orEmpty()
            val updatedAt = preferences[KEY_UPDATED_AT] ?: 0L

            GeminiUsageState.Success(
                fiveHourPercent = fiveHourPercent.coerceIn(0, 100),
                fiveHourResetText = fiveHourResetText,
                weeklyPercent = weeklyPercent.coerceIn(0, 100),
                weeklyResetText = weeklyResetText,
                updatedAt = updatedAt
            )
        }

    override suspend fun loadLastSuccess(): GeminiUsageState.Success? {
        return cachedUsageFlow.first()
    }

    override suspend fun saveLastSuccess(state: GeminiUsageState.Success) {
        dataStore.edit { preferences ->
            preferences[KEY_FIVE_HOUR_PERCENT] = state.fiveHourPercent.coerceIn(0, 100)
            preferences[KEY_FIVE_HOUR_RESET_TEXT] = state.fiveHourResetText
            preferences[KEY_WEEKLY_PERCENT] = state.weeklyPercent.coerceIn(0, 100)
            preferences[KEY_WEEKLY_RESET_TEXT] = state.weeklyResetText
            preferences[KEY_UPDATED_AT] = state.updatedAt
        }
    }

    companion object {
        const val DATASTORE_NAME = "gemini_usage_cache"

        val KEY_FIVE_HOUR_PERCENT = intPreferencesKey("five_hour_percent")
        val KEY_FIVE_HOUR_RESET_TEXT = stringPreferencesKey("five_hour_reset_text")
        val KEY_WEEKLY_PERCENT = intPreferencesKey("weekly_percent")
        val KEY_WEEKLY_RESET_TEXT = stringPreferencesKey("weekly_reset_text")
        val KEY_UPDATED_AT = longPreferencesKey("updated_at")
    }
}
