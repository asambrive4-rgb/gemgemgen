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
    fun decide_rejectsAdditionalRequestWhileBusy() {
        assertEquals(
            RemoteExecutionDecision.Rejected("S25 FE에서 다른 자동화를 실행 중입니다."),
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
