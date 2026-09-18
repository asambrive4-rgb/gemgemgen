// 역할: 시스템 애니메이션 배율 임시 비활성화 및 원래 배율 복구 유스케이스의 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.usecase.AnimationScaleBackupStore
import com.example.gemgemgen.automation.usecase.AnimationScaleSettings
import com.example.gemgemgen.automation.usecase.AnimationScales
import com.example.gemgemgen.automation.usecase.ManageAnimationScaleUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageAnimationScaleUseCaseTest {

    @Test
    fun disableAnimations_savesCurrentScalesAndSetsToZero() {
        val initialScales = AnimationScales(windowScale = 1.0f, transitionScale = 1.0f, animatorScale = 1.0f)
        val settings = FakeAnimationScaleSettings(currentScales = initialScales)
        val backupStore = FakeAnimationScaleBackupStore()
        val useCase = ManageAnimationScaleUseCase(settings, backupStore)

        val session = useCase.disableAnimations()

        assertEquals(initialScales, session.originalScales)
        assertTrue(session.changed)
        assertEquals(AnimationScales.ZERO, settings.currentScales)
        assertEquals(initialScales, backupStore.load())
    }

    @Test
    fun restore_restoresOriginalScalesAndClearsBackup() {
        val initialScales = AnimationScales(windowScale = 1.0f, transitionScale = 1.0f, animatorScale = 1.0f)
        val settings = FakeAnimationScaleSettings(currentScales = initialScales)
        val backupStore = FakeAnimationScaleBackupStore()
        val useCase = ManageAnimationScaleUseCase(settings, backupStore)

        val session = useCase.disableAnimations()
        val result = useCase.restore(session)

        assertTrue(result)
        assertEquals(initialScales, settings.currentScales)
        assertNull(backupStore.load())
    }

    @Test
    fun disableAnimations_whenScalesAlreadyZero_preservesZeroWithoutCallingSetScales() {
        val zeroScales = AnimationScales.ZERO
        val settings = FakeAnimationScaleSettings(currentScales = zeroScales)
        val backupStore = FakeAnimationScaleBackupStore()
        val useCase = ManageAnimationScaleUseCase(settings, backupStore)

        val session = useCase.disableAnimations()

        assertEquals(zeroScales, session.originalScales)
        assertFalse(session.changed)
        assertEquals(0, settings.setCallCount)
        assertEquals(zeroScales, backupStore.load())

        val result = useCase.restore(session)
        assertTrue(result)
        assertEquals(zeroScales, settings.currentScales)
        assertNull(backupStore.load())
    }

    @Test
    fun disableAnimations_whenSetScalesFails_returnsSessionWithoutThrowing() {
        val initialScales = AnimationScales(windowScale = 0.5f, transitionScale = 0.5f, animatorScale = 0.5f)
        val settings = FakeAnimationScaleSettings(currentScales = initialScales, setSuccess = false)
        val backupStore = FakeAnimationScaleBackupStore()
        val useCase = ManageAnimationScaleUseCase(settings, backupStore)

        val session = useCase.disableAnimations()

        assertEquals(initialScales, session.originalScales)
        assertFalse(session.changed)
        assertEquals(initialScales, backupStore.load())
    }

    @Test
    fun disableAnimations_whenPreviousRunLeftBackup_inheritsExistingBackup() {
        val previousOriginalScales = AnimationScales(windowScale = 1.0f, transitionScale = 1.0f, animatorScale = 1.0f)
        // 이전 비정상 종료로 시스템 설정은 이미 0.0f이고 백업 스토어에는 1.0f가 남아있는 상황
        val settings = FakeAnimationScaleSettings(currentScales = AnimationScales.ZERO)
        val backupStore = FakeAnimationScaleBackupStore(initialBackup = previousOriginalScales)
        val useCase = ManageAnimationScaleUseCase(settings, backupStore)

        val session = useCase.disableAnimations()

        // 현재 시스템 배율(0.0)이 아니라 이전 백업 배율(1.0)을 계승해야 함
        assertEquals(previousOriginalScales, session.originalScales)

        // 자동화 완료 후 복원 시 1.0f로 정상 복원되어야 함
        val result = useCase.restore(session)
        assertTrue(result)
        assertEquals(previousOriginalScales, settings.currentScales)
        assertNull(backupStore.load())
    }

    private class FakeAnimationScaleSettings(
        var currentScales: AnimationScales,
        var setSuccess: Boolean = true
    ) : AnimationScaleSettings {
        var setCallCount = 0

        override fun getScales(): AnimationScales = currentScales

        override fun setScales(scales: AnimationScales): Boolean {
            setCallCount++
            if (!setSuccess) return false
            currentScales = scales
            return true
        }
    }

    private class FakeAnimationScaleBackupStore(
        var initialBackup: AnimationScales? = null
    ) : AnimationScaleBackupStore {
        private var backup: AnimationScales? = initialBackup

        override fun load(): AnimationScales? = backup

        override fun save(scales: AnimationScales) {
            backup = scales
        }

        override fun clear() {
            backup = null
        }
    }
}
