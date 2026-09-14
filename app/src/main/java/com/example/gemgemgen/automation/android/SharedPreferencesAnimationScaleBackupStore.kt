// 역할: 자동화 실행 전 시스템 애니메이션 배율 백업 데이터를 로컬에 영구 저장하고 복구합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.SharedPreferences
import com.example.gemgemgen.automation.usecase.AnimationScaleBackupStore
import com.example.gemgemgen.automation.usecase.AnimationScales

class SharedPreferencesAnimationScaleBackupStore(
    context: Context
) : AnimationScaleBackupStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): AnimationScales? {
        if (!preferences.getBoolean(KEY_HAS_BACKUP, false)) {
            return null
        }
        return AnimationScales(
            windowScale = preferences.getFloat(KEY_WINDOW_SCALE, AnimationScales.DEFAULT_SCALE),
            transitionScale = preferences.getFloat(KEY_TRANSITION_SCALE, AnimationScales.DEFAULT_SCALE),
            animatorScale = preferences.getFloat(KEY_ANIMATOR_SCALE, AnimationScales.DEFAULT_SCALE)
        )
    }

    override fun save(scales: AnimationScales) {
        preferences.edit()
            .putBoolean(KEY_HAS_BACKUP, true)
            .putFloat(KEY_WINDOW_SCALE, scales.windowScale)
            .putFloat(KEY_TRANSITION_SCALE, scales.transitionScale)
            .putFloat(KEY_ANIMATOR_SCALE, scales.animatorScale)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "animation_scale_backup"
        const val KEY_HAS_BACKUP = "has_backup"
        const val KEY_WINDOW_SCALE = "window_scale"
        const val KEY_TRANSITION_SCALE = "transition_scale"
        const val KEY_ANIMATOR_SCALE = "animator_scale"
    }
}
