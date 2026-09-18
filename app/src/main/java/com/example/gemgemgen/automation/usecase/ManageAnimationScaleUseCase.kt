// 역할: 시스템 애니메이션 배율을 임시로 끄거나 원래 배율로 복원하는 유스케이스입니다.
package com.example.gemgemgen.automation.usecase

data class AnimationScales(
    val windowScale: Float = DEFAULT_SCALE,
    val transitionScale: Float = DEFAULT_SCALE,
    val animatorScale: Float = DEFAULT_SCALE
) {
    val isZero: Boolean
        get() = windowScale == 0f && transitionScale == 0f && animatorScale == 0f

    companion object {
        const val DEFAULT_SCALE = 1.0f
        val ZERO = AnimationScales(0f, 0f, 0f)
        val DEFAULT = AnimationScales(DEFAULT_SCALE, DEFAULT_SCALE, DEFAULT_SCALE)
    }
}

interface AnimationScaleSettings {
    fun getScales(): AnimationScales
    fun setScales(scales: AnimationScales): Boolean
}

interface AnimationScaleBackupStore {
    fun load(): AnimationScales?
    fun save(scales: AnimationScales)
    fun clear()
}

data class AnimationScaleSession(
    val originalScales: AnimationScales,
    val changed: Boolean
)

class ManageAnimationScaleUseCase(
    private val settings: AnimationScaleSettings,
    private val backupStore: AnimationScaleBackupStore? = null
) {
    /**
     * 자동화 시작 시 호출:
     * 1. 이전 비정상 종료 백업이 남아있다면 이를 원래 배율로 승계
     * 2. 백업이 없다면 현재 시스템 배율을 읽어 영속 저장소에 백업
     * 3. 시스템 배율을 0.0f로 설정 (실패 시에도 비차단)
     */
    fun disableAnimations(): AnimationScaleSession {
        val persistedBackup = backupStore?.load()
        val currentScales = settings.getScales()

        val originalScales = persistedBackup ?: currentScales
        if (persistedBackup == null) {
            backupStore?.save(originalScales)
        }

        // 이미 0인 경우 추가 설정 불필요
        if (currentScales.isZero) {
            return AnimationScaleSession(
                originalScales = originalScales,
                changed = false
            )
        }

        val success = settings.setScales(AnimationScales.ZERO)
        return AnimationScaleSession(
            originalScales = originalScales,
            changed = success
        )
    }

    /**
     * 자동화 종료 시 호출:
     * 백업된 원래 배율로 복원하고 영속 백업 데이터를 정리합니다.
     */
    fun restore(session: AnimationScaleSession): Boolean {
        backupStore?.clear()

        return if (session.changed || settings.getScales() != session.originalScales) {
            settings.setScales(session.originalScales)
        } else {
            true
        }
    }
}
