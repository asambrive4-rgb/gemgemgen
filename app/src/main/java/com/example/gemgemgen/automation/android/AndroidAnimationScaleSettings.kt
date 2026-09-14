// 역할: 시스템 글로벌 설정을 통해 창, 전환, 애니메이터 배율을 조회하고 변경합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.provider.Settings
import com.example.gemgemgen.automation.usecase.AnimationScaleSettings
import com.example.gemgemgen.automation.usecase.AnimationScales

class AndroidAnimationScaleSettings(
    private val context: Context
) : AnimationScaleSettings {

    override fun getScales(): AnimationScales {
        return try {
            val resolver = context.contentResolver
            val window = Settings.Global.getFloat(
                resolver,
                Settings.Global.WINDOW_ANIMATION_SCALE,
                AnimationScales.DEFAULT_SCALE
            )
            val transition = Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                AnimationScales.DEFAULT_SCALE
            )
            val animator = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                AnimationScales.DEFAULT_SCALE
            )
            AnimationScales(
                windowScale = window,
                transitionScale = transition,
                animatorScale = animator
            )
        } catch (_: Throwable) {
            AnimationScales.DEFAULT
        }
    }

    override fun setScales(scales: AnimationScales): Boolean {
        return try {
            val resolver = context.contentResolver
            val windowSuccess = Settings.Global.putFloat(
                resolver,
                Settings.Global.WINDOW_ANIMATION_SCALE,
                scales.windowScale
            )
            val transitionSuccess = Settings.Global.putFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                scales.transitionScale
            )
            val animatorSuccess = Settings.Global.putFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                scales.animatorScale
            )
            windowSuccess && transitionSuccess && animatorSuccess
        } catch (_: Throwable) {
            false
        }
    }
}
