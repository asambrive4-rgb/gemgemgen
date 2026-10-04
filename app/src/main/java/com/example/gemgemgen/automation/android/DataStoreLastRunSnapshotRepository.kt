// 역할: Jetpack DataStore를 통해 마지막 자동화 실행 스냅샷(프롬프트, 회차, 대상 앱 등)을 영구 저장하고 불러옵니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.LastRunSnapshot
import com.example.gemgemgen.automation.usecase.LastRunSnapshotRepository
import com.example.gemgemgen.core.AppDefaults
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.lastRunSnapshotDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreLastRunSnapshotRepository.DATASTORE_NAME
)

class DataStoreLastRunSnapshotRepository(
    private val dataStore: DataStore<Preferences>
) : LastRunSnapshotRepository {
    constructor(context: Context) : this(context.applicationContext.lastRunSnapshotDataStore)

    @Volatile
    private var cachedSnapshot: LastRunSnapshot? = null
    @Volatile
    private var isLoaded: Boolean = false

    val snapshotFlow: Flow<LastRunSnapshot> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            LastRunSnapshot(
                promptTemplate = preferences[KEY_PROMPT_TEMPLATE].orEmpty(),
                repeatCountText = preferences[KEY_REPEAT_COUNT_TEXT].orEmpty(),
                targetApp = AutomationTargetApp.fromStorageValue(preferences[KEY_TARGET_APP].orEmpty()),
                flowImageCount = preferences[KEY_FLOW_IMAGE_COUNT] ?: AppDefaults.DEFAULT_FLOW_IMAGE_COUNT
            ).also {
                cachedSnapshot = it
                isLoaded = true
            }
        }

    override fun load(): LastRunSnapshot? {
        if (isLoaded) return cachedSnapshot
        return runBlocking {
            snapshotFlow.first()
        }
    }

    override fun save(snapshot: LastRunSnapshot) {
        cachedSnapshot = snapshot
        isLoaded = true
        runBlocking {
            saveAsync(snapshot)
        }
    }

    suspend fun saveAsync(snapshot: LastRunSnapshot) {
        cachedSnapshot = snapshot
        isLoaded = true
        dataStore.edit { preferences ->
            preferences[KEY_PROMPT_TEMPLATE] = snapshot.promptTemplate
            preferences[KEY_REPEAT_COUNT_TEXT] = snapshot.repeatCountText
            preferences[KEY_TARGET_APP] = snapshot.targetApp.storageValue
            preferences[KEY_FLOW_IMAGE_COUNT] = snapshot.flowImageCount
        }
    }

    companion object {
        const val DATASTORE_NAME = "last_run_snapshot"
        val KEY_PROMPT_TEMPLATE = stringPreferencesKey("prompt_template")
        val KEY_REPEAT_COUNT_TEXT = stringPreferencesKey("repeat_count_text")
        val KEY_TARGET_APP = stringPreferencesKey("target_app")
        val KEY_FLOW_IMAGE_COUNT = intPreferencesKey("flow_image_count")
    }
}
