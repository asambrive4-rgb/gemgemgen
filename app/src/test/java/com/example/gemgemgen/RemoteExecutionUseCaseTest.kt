// 역할: 수신된 원격 명령의 실행 인가 정책 및 선점 허용 결정을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.remote.usecase.CheckRemoteExecutionUseCase
import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.domain.RemoteExecutionDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteExecutionUseCaseTest {
    private val useCase = CheckRemoteExecutionUseCase()

    @Test
    fun decide_allowsReadyUnlockedReceiver() {
        assertEquals(RemoteExecutionDecision.Allowed, useCase.decide(readyConditions()))
    }

    @Test
    fun decide_rejectsLockedOrScreenOffReceiver() {
        assertEquals(
            RemoteExecutionDecision.Rejected("S25 FE의 화면이 꺼져 있거나 잠겨 있습니다."),
            useCase.decide(readyConditions().copy(isDeviceLocked = true))
        )
        assertEquals(
            RemoteExecutionDecision.Rejected("S25 FE의 화면이 꺼져 있거나 잠겨 있습니다."),
            useCase.decide(readyConditions().copy(isScreenInteractive = false))
        )
    }

    @Test
    fun decide_allowsAdditionalRequestWhileBusy_forPreemption() {
        // 송신이 주(Master)이므로 수신 기기가 자동화 실행 중이더라도 거부하지 않고 허용되어 기존 작업을 취소 후 선점할 수 있어야 한다.
        assertEquals(
            RemoteExecutionDecision.Allowed,
            useCase.decide(readyConditions().copy(isAutomationBusy = true))
        )
    }

    @Test
    fun decide_allowsWhenWildcardsNotRequired_evenIfDirectoryInaccessible() {
        assertEquals(
            RemoteExecutionDecision.Allowed,
            useCase.decide(
                readyConditions().copy(isWildcardDirectoryAccessible = false),
                requiresWildcardDirectory = false
            )
        )
    }

    @Test
    fun decide_rejectsWhenWildcardsRequired_andDirectoryInaccessible() {
        assertEquals(
            RemoteExecutionDecision.Rejected("S25 FE의 wildcard 폴더에 접근할 수 없습니다."),
            useCase.decide(
                readyConditions().copy(isWildcardDirectoryAccessible = false),
                requiresWildcardDirectory = true
            )
        )
    }

    private fun readyConditions() = RemoteExecutionConditions(
        isWifiConnected = true,
        isScreenInteractive = true,
        isDeviceLocked = false,
        isTargetAppInstalled = true,
        isAccessibilityServiceEnabled = true,
        hasWriteSecureSettingsPermission = true,
        isWildcardDirectoryAccessible = true,
        hasOverlayPermission = true,
        isAutomationBusy = false
    )
}
