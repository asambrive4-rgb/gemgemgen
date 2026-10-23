// 역할: 앱 시작 전 유지보수, 모드별 메모리 확보 및 Gemini 계정 전환(로컬/원격 동시) 유스케이스 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.AppMaintenanceUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.GeminiAccountSwitchGateway
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.MaintenanceResult
import com.example.gemgemgen.automation.usecase.MemoryCleanupGateway
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.usecase.SwitchGeminiAccountResult
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.domain.RemoteAutomationRequest
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMaintenanceUseCaseTest {

    @Test
    fun restartGemini_successSingle_returnsSuccessMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("Gemini 앱을 재시작했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, closer.callCount)
    }

    @Test
    fun restartGemini_successMultiple_returnsCountInMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 3))
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("Gemini 앱 3개를 종료하고 재시작했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun restartGemini_accessibilityUnavailable_returnsUnavailable() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.AccessibilityUnavailable)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.restartGemini()

        assertEquals(MaintenanceResult.Unavailable, result)
    }

    @Test
    fun restartGemini_recentsUnavailable_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.RecentsUnavailable)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("최근 앱 화면을 열지 못했습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun restartGemini_notFound_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("최근 앱에서 Gemini를 찾지 못했습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun restartGemini_failure_returnsFailureWithMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Failure("crash"))
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("Gemini 재시작 실패: crash", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun terminateSelf_success_returnsSuccessMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.terminateSelf()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("앱을 종료했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun terminateSelf_notFound_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        )

        val result = useCase.terminateSelf()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("최근 앱에서 GemGemGen을 찾지 못했습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun cleanMemory_success_returnsSuccessMessage() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = gateway
        )

        val result = useCase.cleanMemory()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("메모리 정리를 완료했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, gateway.callCount)
    }

    @Test
    fun cleanMemory_accessibilityUnavailable_returnsUnavailable() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.AccessibilityUnavailable)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = gateway
        )

        val result = useCase.cleanMemory()

        assertEquals(MaintenanceResult.Unavailable, result)
    }

    @Test
    fun cleanMemory_inProgress_returnsFailure() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.InProgress)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = gateway
        )

        val result = useCase.cleanMemory()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("메모리 정리가 이미 진행 중입니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun cleanMemory_failure_returnsFailureWithMessage() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.Failure("timeout"))
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = gateway
        )

        val result = useCase.cleanMemory()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("메모리 정리 실패: timeout", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun cleanMemory_senderMode_withoutRemoteManager_returnsFailure() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = gateway
        )

        val result = useCase.cleanMemory(AutomationMode.SENDER)

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("원격 메모리 정리를 수행할 수 없습니다.", (result as MaintenanceResult.Failure).message)
        assertEquals(0, gateway.callCount)
    }

    @Test
    fun cleanMemory_receiverMode_cleansLocalMemory() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val remoteGateway = FakeRemoteGateway(cleanMemoryResult = RemoteActionResult.Success, canSend = true)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = gateway,
            manageRemoteAutomation = ManageRemoteAutomationUseCase(remoteGateway)
        )

        val result = useCase.cleanMemory(AutomationMode.RECEIVER)

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("메모리 정리를 완료했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, gateway.callCount)
        assertEquals(0, remoteGateway.cleanCount)
    }

    @Test
    fun customClosers_delegateToRespectiveCloser() = runBlocking {
        val restartCloser = FakeGeminiCloser(CloseGeminiAppResult.Success(1))
        val selfCloser = FakeGeminiCloser(CloseGeminiAppResult.Success(3))
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)

        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = restartCloser,
            selfAppCloser = selfCloser,
            memoryCleanupGateway = memoryGateway
        )

        useCase.restartGemini()
        assertEquals(1, restartCloser.callCount)
        assertEquals(0, selfCloser.callCount)

        useCase.terminateSelf()
        assertEquals(1, restartCloser.callCount)
        assertEquals(1, selfCloser.callCount)
    }

    @Test
    fun cleanMemory_senderMode_withManageRemoteAutomation_success() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val remoteGateway = FakeRemoteGateway(cleanMemoryResult = RemoteActionResult.Success, canSend = true)
        val manageRemote = ManageRemoteAutomationUseCase(remoteGateway)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = memoryGateway,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase.cleanMemory(AutomationMode.SENDER)

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("수신 기기 메모리를 정리했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(0, memoryGateway.callCount)
        assertEquals(1, remoteGateway.cleanCount)
    }

    @Test
    fun cleanMemory_senderMode_withManageRemoteAutomation_failure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val remoteGateway = FakeRemoteGateway(
            cleanMemoryResult = RemoteActionResult.Failure("연결 시간 초과"),
            canSend = true
        )
        val manageRemote = ManageRemoteAutomationUseCase(remoteGateway)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = memoryGateway,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase.cleanMemory(AutomationMode.SENDER)

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("연결 시간 초과", (result as MaintenanceResult.Failure).message)
        assertEquals(0, memoryGateway.callCount)
        assertEquals(1, remoteGateway.cleanCount)
    }

    @Test
    fun cleanLocalMemory_invokesGatewayDirectly() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = memoryGateway
        )

        val result = useCase.cleanLocalMemory()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("메모리 정리를 완료했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, memoryGateway.callCount)
    }

    @Test
    fun cleanRemoteMemory_withManageRemoteAutomation_invokesRemoteDirectly() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val remoteGateway = FakeRemoteGateway(cleanMemoryResult = RemoteActionResult.Success, canSend = true)
        val manageRemote = ManageRemoteAutomationUseCase(remoteGateway)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = memoryGateway,
            manageRemoteAutomation = manageRemote
        )

        val result = useCase.cleanRemoteMemory()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("수신 기기 메모리를 정리했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, remoteGateway.cleanCount)
    }

    @Test
    fun cleanRemoteMemory_withoutRemoteManager_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = closer,
            memoryCleanupGateway = memoryGateway
        )

        val result = useCase.cleanRemoteMemory()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("원격 메모리 정리를 수행할 수 없습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun switchGeminiAccount_normalMode_switchesLocalAccountOnly() = runBlocking {
        val accountGateway = FakeAccountSwitchGateway(
            SwitchGeminiAccountResult.Success
        )
        val remoteGateway = FakeRemoteGateway(canSend = true)
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success),
            manageRemoteAutomation = ManageRemoteAutomationUseCase(remoteGateway),
            geminiAccountSwitchGateway = accountGateway
        )

        val result = useCase.switchGeminiAccount(
            email = "joajoajoa31@gmail.com",
            alias = "계정 1",
            mode = AutomationMode.NORMAL
        )

        assertTrue(result is MaintenanceResult.Success)
        assertEquals(
            "Gemini 계정을 '계정 1'(으)로 전환했습니다.",
            (result as MaintenanceResult.Success).message
        )
        assertEquals(1, accountGateway.callCount)
        assertEquals(true, accountGateway.lastReturnToHostApp)
        assertEquals(0, remoteGateway.switchAccountCount)
    }

    @Test
    fun switchGeminiAccount_receiverMode_switchesLocalAccountWithoutReturningToHost() = runBlocking {
        val accountGateway = FakeAccountSwitchGateway(
            SwitchGeminiAccountResult.Success
        )
        val useCase = AppMaintenanceUseCase(
            geminiAccountSwitchGateway = accountGateway
        )

        val result = useCase.switchGeminiAccount(
            email = "receiver@gmail.com",
            alias = "수신 계정",
            mode = AutomationMode.RECEIVER
        )

        assertTrue(result is MaintenanceResult.Success)
        assertEquals(
            "Gemini 계정을 '수신 계정'(으)로 전환했습니다.",
            (result as MaintenanceResult.Success).message
        )
        assertEquals(1, accountGateway.callCount)
        assertEquals(false, accountGateway.lastReturnToHostApp)
    }

    @Test
    fun switchGeminiAccount_senderMode_bothSucceed_returnsCombinedSuccessMessage() = runBlocking {
        val accountGateway = FakeAccountSwitchGateway(
            SwitchGeminiAccountResult.Success
        )
        val remoteGateway = FakeRemoteGateway(
            switchAccountResult = RemoteActionResult.Success,
            canSend = true
        )
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success),
            manageRemoteAutomation = ManageRemoteAutomationUseCase(remoteGateway),
            geminiAccountSwitchGateway = accountGateway
        )

        val result = useCase.switchGeminiAccount(
            email = "jjoajjoajjoa31@gmail.com",
            alias = "계정 2",
            mode = AutomationMode.SENDER
        )

        assertTrue(result is MaintenanceResult.Success)
        assertEquals(
            "송신·수신 기기의 Gemini 계정을 '계정 2'(으)로 전환했습니다.",
            (result as MaintenanceResult.Success).message
        )
        assertEquals(1, accountGateway.callCount)
        assertEquals(1, remoteGateway.switchAccountCount)
        assertEquals("jjoajjoajjoa31@gmail.com", remoteGateway.lastSwitchedEmail)
        assertEquals("계정 2", remoteGateway.lastSwitchedAlias)
    }

    @Test
    fun switchGeminiAccount_senderMode_localSuccessAndRemoteFailure_keepsLocalAndReportsPartialFailure() = runBlocking {
        val accountGateway = FakeAccountSwitchGateway(
            SwitchGeminiAccountResult.Success
        )
        val remoteGateway = FakeRemoteGateway(
            switchAccountResult = RemoteActionResult.Failure("수신 기기 연결 시간 초과"),
            canSend = true
        )
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success),
            manageRemoteAutomation = ManageRemoteAutomationUseCase(remoteGateway),
            geminiAccountSwitchGateway = accountGateway
        )

        val result = useCase.switchGeminiAccount(
            email = "jjoajjoajjoa31@gmail.com",
            alias = "계정 2",
            mode = AutomationMode.SENDER
        )

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals(
            "송신 기기는 '계정 2'(으)로 전환했으나 수신 기기 전환 실패: 수신 기기 연결 시간 초과",
            (result as MaintenanceResult.Failure).message
        )
        assertEquals(1, accountGateway.callCount)
        assertEquals(1, remoteGateway.switchAccountCount)
    }

    @Test
    fun switchGeminiAccount_senderMode_bothFail_returnsCombinedFailureMessage() = runBlocking {
        val accountGateway = FakeAccountSwitchGateway(
            SwitchGeminiAccountResult.AccessibilityUnavailable
        )
        val remoteGateway = FakeRemoteGateway(
            switchAccountResult = RemoteActionResult.Failure("수신 기기와 먼저 페어링해 주세요."),
            canSend = false
        )
        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = FakeGeminiCloser(CloseGeminiAppResult.NotFound),
            memoryCleanupGateway = FakeMemoryGateway(MemoryCleanupResult.Success),
            manageRemoteAutomation = ManageRemoteAutomationUseCase(remoteGateway),
            geminiAccountSwitchGateway = accountGateway
        )

        val result = useCase.switchGeminiAccount(
            email = "jjoajjoajjoa31@gmail.com",
            alias = "계정 2",
            mode = AutomationMode.SENDER
        )

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals(
            "송신·수신 기기 계정 전환 실패 (송신: 접근성 서비스가 켜져 있지 않습니다. / 수신: 연결된 수신 기기를 찾지 못했습니다.)",
            (result as MaintenanceResult.Failure).message
        )
    }

    private class FakeGeminiCloser(val result: CloseGeminiAppResult) : GeminiAppCloser {
        var callCount = 0
        override suspend fun closeGeminiApp(): CloseGeminiAppResult {
            callCount++
            return result
        }
    }

    private class FakeMemoryGateway(val result: MemoryCleanupResult) : MemoryCleanupGateway {
        var callCount = 0
        override suspend fun cleanMemory(): MemoryCleanupResult {
            callCount++
            return result
        }
    }

    private class FakeAccountSwitchGateway(
        val result: SwitchGeminiAccountResult
    ) : GeminiAccountSwitchGateway {
        var callCount = 0
        var lastReturnToHostApp: Boolean? = null
        override suspend fun switchAccount(
            email: String,
            alias: String,
            returnToHostApp: Boolean
        ): SwitchGeminiAccountResult {
            callCount++
            lastReturnToHostApp = returnToHostApp
            return result
        }
    }

    private class FakeRemoteGateway(
        var cleanMemoryResult: RemoteActionResult = RemoteActionResult.Success,
        var switchAccountResult: RemoteActionResult = RemoteActionResult.Success,
        canSend: Boolean = true
    ) : RemoteAutomationGateway {
        var cleanCount = 0
        var switchAccountCount = 0
        var lastSwitchedEmail: String = ""
        var lastSwitchedAlias: String = ""
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
        override suspend fun send(request: RemoteAutomationRequest) = Unit
        override fun updateRepeatCount(requestId: String, repeatCount: Int) = Unit
        override fun forceStop(requestId: String?) = Unit

        override suspend fun cleanMemory(): RemoteActionResult {
            cleanCount++
            return cleanMemoryResult
        }

        override suspend fun switchGeminiAccount(email: String, alias: String): RemoteActionResult {
            switchAccountCount++
            lastSwitchedEmail = email
            lastSwitchedAlias = alias
            return switchAccountResult
        }
    }
}

