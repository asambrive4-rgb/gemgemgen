// 역할: Jetpack DataStore를 통해 사용자가 선택한 테마 색상 및 모드 설정을 비동기로 로컬에 저장하고 Flow/StateFlow로 제공합니다.
package com.example.gemgemgen.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ThemePaletteStore.DATASTORE_NAME
)

class ThemePaletteStore(
    private val dataStore: DataStore<Preferences>,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    constructor(context: Context) : this(context.applicationContext.themeDataStore)

    private val scope = coroutineScope

    val paletteFlow: Flow<AppThemePalette> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val id = preferences[KEY_THEME_PALETTE] ?: AppThemePalette.DEFAULT.id
            AppThemePalette.fromId(id)
        }

    val modeFlow: Flow<AppThemeMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val id = preferences[KEY_THEME_MODE] ?: AppThemeMode.DEFAULT.id
            AppThemeMode.fromId(id)
        }

    private val _currentPalette = MutableStateFlow(AppThemePalette.DEFAULT)
    val currentPalette: StateFlow<AppThemePalette> = _currentPalette.asStateFlow()

    private val _currentMode = MutableStateFlow(AppThemeMode.DEFAULT)
    val currentMode: StateFlow<AppThemeMode> = _currentMode.asStateFlow()

    init {
        scope.launch {
            paletteFlow.collect { palette ->
                _currentPalette.value = palette
            }
        }
        scope.launch {
            modeFlow.collect { mode ->
                _currentMode.value = mode
            }
        }
    }

    fun loadPalette(): AppThemePalette = currentPalette.value

    fun loadMode(): AppThemeMode = currentMode.value

    fun setPalette(palette: AppThemePalette) {
        _currentPalette.value = palette
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_THEME_PALETTE] = palette.id
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _currentMode.value = mode
        scope.launch {
            dataStore.edit { preferences ->
                preferences[KEY_THEME_MODE] = mode.id
            }
        }
    }

    suspend fun setPaletteAsync(palette: AppThemePalette) {
        _currentPalette.value = palette
        dataStore.edit { preferences ->
            preferences[KEY_THEME_PALETTE] = palette.id
        }
    }

    suspend fun setThemeModeAsync(mode: AppThemeMode) {
        _currentMode.value = mode
        dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.id
        }
    }

    companion object {
        const val DATASTORE_NAME = "theme_preferences"
        val KEY_THEME_PALETTE = stringPreferencesKey("selected_theme_palette")
        val KEY_THEME_MODE = stringPreferencesKey("selected_theme_mode")
    }
}
