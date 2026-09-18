// 역할: Gemini 계정 목록 열기 유스케이스의 성공, 실패, 접근성 미가용 시 폴백 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.GeminiAccountSwitchResult
import com.example.gemgemgen.automation.domain.GeminiAccountSwitchProgressPolicy
import com.example.gemgemgen.automation.usecase.GeminiAccountSwitcherGateway
import com.example.gemgemgen.automation.usecase.OpenGeminiAccountPickerResult
import com.example.gemgemgen.automation.usecase.OpenGeminiAccountPickerUseCase
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import com.example.gemgemgen.remote.usecase.NoOpRemoteAutomationGateway
import com.example.gemgemgen.remote.usecase.RemoteAutomationGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGeminiAccountPickerUseCaseTest {

    private class FakeGateway(
        override var isServiceAvailable: Boolean = true,
        var openResult: GeminiAccountSwitchResult = GeminiAccountSwitchResult.Success("열기 성공"),
        var launchManualResult: Boolean = true
    ) : GeminiAccountSwitcherGateway {
        var openCallCount = 0
        var launchManualCallCount = 0

        override suspend fun openAccountPicker(
            onProgress: (phase: String, message: String) -> Unit
        ): GeminiAccountSwitchResult {
            openCallCount++
            return openResult
        }

        override fun launchGeminiForManualSwitch(): Boolean {
            launchManualCallCount++
            return launchManualResult
        }
    }

    private class FakeRemoteGateway(
        private val delegate: RemoteAutomationGateway = NoOpRemoteAutomationGateway()
    ) : RemoteAutomationGateway by delegate {
        var switchCallCount = 0
        var returnSuccess = true

        override suspend fun switchGeminiAccount(id: String, alias: String, identifier: String): RemoteActionResult {
            switchCallCount++
            return if (returnSuccess) RemoteActionResult.Success else RemoteActionResult.Failure("원격 실패")
        }
    }

    @Test
    fun execute_inNormalMode_whenServiceAvailable_callsOpenAccountPicker() = runBlocking {
        val gateway = FakeGateway(isServiceAvailable = true)
        val remote = ManageRemoteAutomationUseCase(FakeRemoteGateway())
        val useCase = OpenGeminiAccountPickerUseCase(remote, gateway)

        val result = useCase.execute(AutomationMode.NORMAL)

        assertEquals(1, gateway.openCallCount)
        assertEquals(0, gateway.launchManualCallCount)
        assertTrue(result is OpenGeminiAccountPickerResult.Success)
    }

    @Test
    fun execute_inNormalMode_whenServiceUnavailable_fallsBackToLaunchGemini() = runBlocking {
        val gateway = FakeGateway(isServiceAvailable = false, launchManualResult = true)
        val remote = ManageRemoteAutomationUseCase(FakeRemoteGateway())
        val useCase = OpenGeminiAccountPickerUseCase(remote, gateway)

        val result = useCase.execute(AutomationMode.NORMAL)

        assertEquals(0, gateway.openCallCount)
        assertEquals(1, gateway.launchManualCallCount)
        assertTrue(result is OpenGeminiAccountPickerResult.Success)
    }

    @Test
    fun execute_inNormalMode_whenServiceUnavailableAndLaunchFails_returnsFailure() = runBlocking {
        val gateway = FakeGateway(isServiceAvailable = false, launchManualResult = false)
        val remote = ManageRemoteAutomationUseCase(FakeRemoteGateway())
        val useCase = OpenGeminiAccountPickerUseCase(remote, gateway)

        val result = useCase.execute(AutomationMode.NORMAL)

        assertEquals(0, gateway.openCallCount)
        assertEquals(1, gateway.launchManualCallCount)
        assertTrue(result is OpenGeminiAccountPickerResult.Failure)
        assertEquals(GeminiAccountSwitchProgressPolicy.ERROR_ACCESSIBILITY_REQUIRED, (result as OpenGeminiAccountPickerResult.Failure).message)
    }

    @Test
    fun execute_inSenderMode_requestsRemoteSwitch() = runBlocking {
        val gateway = FakeGateway()
        val fakeRemote = FakeRemoteGateway()
        val remote = ManageRemoteAutomationUseCase(fakeRemote)
        val useCase = OpenGeminiAccountPickerUseCase(remote, gateway)

        val result = useCase.execute(AutomationMode.SENDER)

        assertEquals(1, fakeRemote.switchCallCount)
        assertEquals(0, gateway.openCallCount)
        assertTrue(result is OpenGeminiAccountPickerResult.Success)
    }
}
