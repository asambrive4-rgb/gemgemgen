// 역할: Jetpack DataStore를 통해 자동화 실행 전 시스템 애니메이션 배율 백업 데이터를 로컬에 영구 저장하고 복구합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gemgemgen.automation.usecase.AnimationScaleBackupStore
import com.example.gemgemgen.automation.usecase.AnimationScales
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.animationScaleBackupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreAnimationScaleBackupStore.DATASTORE_NAME
)

class DataStoreAnimationScaleBackupStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AnimationScaleBackupStore {
    constructor(context: Context) : this(context.applicationContext.animationScaleBackupDataStore)

    @Volatile
    private var cachedScales: AnimationScales? = null
    @Volatile
    private var hasBackup: Boolean = false

    val scalesFlow: Flow<AnimationScales?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            if (preferences[KEY_HAS_BACKUP] != true) {
                null
            } else {
                AnimationScales(
                    windowScale = preferences[KEY_WINDOW_SCALE] ?: AnimationScales.DEFAULT_SCALE,
                    transitionScale = preferences[KEY_TRANSITION_SCALE] ?: AnimationScales.DEFAULT_SCALE,
                    animatorScale = preferences[KEY_ANIMATOR_SCALE] ?: AnimationScales.DEFAULT_SCALE
                )
            }
        }

    init {
        scope.launch {
            scalesFlow.collect { scales ->
                cachedScales = scales
                hasBackup = scales != null
            }
        }
    }

    override fun load(): AnimationScales? {
        return cachedScales
    }

    override fun save(scales: AnimationScales) {
        cachedScales = scales
        hasBackup = true
        scope.launch {
            saveAsync(scales)
        }
    }

    suspend fun saveAsync(scales: AnimationScales) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_BACKUP] = true
            preferences[KEY_WINDOW_SCALE] = scales.windowScale
            preferences[KEY_TRANSITION_SCALE] = scales.transitionScale
            preferences[KEY_ANIMATOR_SCALE] = scales.animatorScale
        }
    }

    override fun clear() {
        cachedScales = null
        hasBackup = false
        scope.launch {
            clearAsync()
        }
    }

    suspend fun clearAsync() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        const val DATASTORE_NAME = "animation_scale_backup"
        val KEY_HAS_BACKUP = booleanPreferencesKey("has_backup")
        val KEY_WINDOW_SCALE = floatPreferencesKey("window_scale")
        val KEY_TRANSITION_SCALE = floatPreferencesKey("transition_scale")
        val KEY_ANIMATOR_SCALE = floatPreferencesKey("animator_scale")
    }
}
