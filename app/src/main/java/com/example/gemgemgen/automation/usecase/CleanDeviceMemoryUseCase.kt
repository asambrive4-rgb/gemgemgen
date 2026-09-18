// 역할: AutomationMode(NORMAL vs SENDER)에 따라 로컬 메모리 정리(AppMaintenanceUseCase) 또는 원격 기기 메모리 정리(ManageRemoteAutomationUseCase)를 라우팅하여 수행하는 유스케이스.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.NoOpRemoteAutomationGateway

class CleanDeviceMemoryUseCase(
    private val appMaintenance: AppMaintenanceUseCase = AppMaintenanceUseCase(
        geminiAppCloser = object : GeminiAppCloser {
            override suspend fun closeGeminiApp(): CloseGeminiAppResult =
                CloseGeminiAppResult.AccessibilityUnavailable
        },
        memoryCleanupGateway = object : MemoryCleanupGateway {
            override suspend fun cleanMemory(): MemoryCleanupResult =
                MemoryCleanupResult.AccessibilityUnavailable
        }
    ),
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase =
        ManageRemoteAutomationUseCase(NoOpRemoteAutomationGateway())
) {
    suspend operator fun invoke(mode: AutomationMode): MaintenanceResult =
        if (mode == AutomationMode.SENDER) {
            when (val result = manageRemoteAutomation.cleanMemory()) {
                RemoteActionResult.Success -> MaintenanceResult.Success("수신 기기 메모리를 정리했습니다.")
                is RemoteActionResult.Failure -> MaintenanceResult.Failure(result.message)
            }
        } else {
            appMaintenance.cleanMemory()
        }

    suspend fun execute(mode: AutomationMode): MaintenanceResult = invoke(mode)
}
