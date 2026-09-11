// 역할: 사용자가 선택한 테마 색상 설정을 로컬에 저장하고 복원합니다.
package com.example.gemgemgen.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePaletteStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _currentPalette = MutableStateFlow(loadPalette())
    val currentPalette: StateFlow<AppThemePalette> = _currentPalette.asStateFlow()

    private val _currentMode = MutableStateFlow(loadMode())
    val currentMode: StateFlow<AppThemeMode> = _currentMode.asStateFlow()

    private fun loadPalette(): AppThemePalette {
        val savedId = prefs.getString(KEY_THEME_PALETTE, AppThemePalette.DEFAULT.id)
        return AppThemePalette.fromId(savedId)
    }

    private fun loadMode(): AppThemeMode {
        val savedId = prefs.getString(KEY_THEME_MODE, AppThemeMode.DEFAULT.id)
        return AppThemeMode.fromId(savedId)
    }

    fun setPalette(palette: AppThemePalette) {
        prefs.edit().putString(KEY_THEME_PALETTE, palette.id).apply()
        _currentPalette.value = palette
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _currentMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "gemgemgen_theme_preferences"
        private const val KEY_THEME_PALETTE = "selected_theme_palette"
        private const val KEY_THEME_MODE = "selected_theme_mode"
    }
}
