// 역할: 자동화 관련 사용자 설정(시작 시 클립보드 복사 등)을 로컬 저장소에 저장하고 복원합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * =========================================================================================
 * [주의: 사용자 명시적 요구 기능 - 임의 삭제 절대 금지]
 * - 기능: 자동화 시작 시 사용자의 원본 프롬프트 템플릿을 시스템 클립보드에 1회 백업할지 여부 제어
 * - 목적: 자동화 오류 발생 시 수동 복구 및 타 앱 수동 붙여넣기 편의성 제공
 * - 규칙: 성능 최적화, 클립보드 오염 방지, 미사용 코드 정리 등의 이유로 이 설정을
 *         삭제하거나 기본값을 false로 임의 변경하지 마세요.
 * =========================================================================================
 */
class AutomationSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _copyPromptOnStart = MutableStateFlow(loadCopyPromptOnStart())
    val copyPromptOnStart: StateFlow<Boolean> = _copyPromptOnStart.asStateFlow()

    private fun loadCopyPromptOnStart(): Boolean {
        // 기본값: true (사용자 요구사항: 기본값으로 허용)
        return prefs.getBoolean(KEY_COPY_PROMPT_ON_START, DEFAULT_COPY_PROMPT_ON_START)
    }

    fun setCopyPromptOnStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COPY_PROMPT_ON_START, enabled).apply()
        _copyPromptOnStart.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "gemgemgen_automation_preferences"
        private const val KEY_COPY_PROMPT_ON_START = "copy_prompt_on_start"
        const val DEFAULT_COPY_PROMPT_ON_START = true
    }
}
