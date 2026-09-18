// 역할: 자동화 모드에 따라 로컬 또는 원격 메모리 정리 라우팅 유스케이스를 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanDeviceMemoryUseCaseTest {

    private class FakeRemoteGateway(
        var cleanMemoryResult: RemoteActionResult = RemoteActionResult.Success,
        canSend: Boolean = true
    ) : RemoteAutomationGateway {
        private val _status = MutableStateFlow(
            RemoteAutomationStatus(
                mode = AutomationMode.SENDER,
                discoveredDeviceName = if (canSend) "Test Device" else "",
                isPaired = canSend
            )
        )
        override val status: StateFlow<RemoteAutomationStatus> = _status

        override fun selectMode(mode: AutomationMode) = Unit
        override suspend fun pair(pairingCode: String): RemoteActionResult = RemoteActionResult.Success
        override suspend fun disconnect(): RemoteActionResult = RemoteActionResult.Success
        override suspend fun send(
            request: com.example.gemgemgen.remote.domain.RemoteAutomationRequest,
            onStateChange: (com.example.gemgemgen.automation.domain.AutomationRunState) -> Unit
        ) = Unit
        override fun forceStop(requestId: String?) = Unit
        override suspend fun cleanMemory(): RemoteActionResult = cleanMemoryResult
        override suspend fun switchGeminiAccount(id: String, alias: String, identifier: String): RemoteActionResult = RemoteActionResult.Success
    }

    @Test
    fun invoke_inNormalMode_callsLocalAppMaintenance(): Unit = runBlocking {
        val appMaintenance = AppMaintenanceUseCase(
            geminiAppCloser = { CloseGeminiAppResult.Success(1) },
            memoryCleanupGateway = { MemoryCleanupResult.Success }
        )
        val manageRemote = ManageRemoteAutomationUseCase(FakeRemoteGateway())
        val useCase = CleanDeviceMemoryUseCase(
            appMaintenance = appMaintenance,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase(AutomationMode.NORMAL)

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("메모리 정리를 완료했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun invoke_inSenderMode_whenRemoteCleanMemorySucceeds_returnsSuccess(): Unit = runBlocking {
        val appMaintenance = AppMaintenanceUseCase(
            geminiAppCloser = { CloseGeminiAppResult.AccessibilityUnavailable },
            memoryCleanupGateway = { MemoryCleanupResult.AccessibilityUnavailable }
        )
        val fakeGateway = FakeRemoteGateway(cleanMemoryResult = RemoteActionResult.Success, canSend = true)
        val manageRemote = ManageRemoteAutomationUseCase(fakeGateway)
        val useCase = CleanDeviceMemoryUseCase(
            appMaintenance = appMaintenance,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase(AutomationMode.SENDER)

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("수신 기기 메모리를 정리했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun invoke_inSenderMode_whenRemoteCleanMemoryFails_returnsFailure(): Unit = runBlocking {
        val appMaintenance = AppMaintenanceUseCase(
            geminiAppCloser = { CloseGeminiAppResult.AccessibilityUnavailable },
            memoryCleanupGateway = { MemoryCleanupResult.AccessibilityUnavailable }
        )
        val fakeGateway = FakeRemoteGateway(
            cleanMemoryResult = RemoteActionResult.Failure("연결 시간 초과"),
            canSend = true
        )
        val manageRemote = ManageRemoteAutomationUseCase(fakeGateway)
        val useCase = CleanDeviceMemoryUseCase(
            appMaintenance = appMaintenance,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase(AutomationMode.SENDER)

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("연결 시간 초과", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun execute_inNormalMode_worksSameAsInvoke(): Unit = runBlocking {
        val appMaintenance = AppMaintenanceUseCase(
            geminiAppCloser = { CloseGeminiAppResult.AccessibilityUnavailable },
            memoryCleanupGateway = { MemoryCleanupResult.AccessibilityUnavailable }
        )
        val manageRemote = ManageRemoteAutomationUseCase(FakeRemoteGateway())
        val useCase = CleanDeviceMemoryUseCase(
            appMaintenance = appMaintenance,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase.execute(AutomationMode.NORMAL)

        assertTrue(result is MaintenanceResult.Unavailable)
    }

    @Test
    fun defaultConstructor_operatesSafely(): Unit = runBlocking {
        val useCase = CleanDeviceMemoryUseCase()

        val result = useCase(AutomationMode.NORMAL)

        assertTrue(result is MaintenanceResult.Unavailable)
    }
}
