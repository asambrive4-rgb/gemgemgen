package com.example.gemgemgen

import com.example.gemgemgen.automation.usecase.AppMaintenanceUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.automation.usecase.MaintenanceResult
import com.example.gemgemgen.automation.usecase.MemoryCleanupGateway
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMaintenanceUseCaseTest {

    @Test
    fun restartGemini_successSingle_returnsSuccessMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("Gemini 앱을 재시작했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, closer.callCount)
    }

    @Test
    fun restartGemini_successMultiple_returnsCountInMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 3))
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("Gemini 앱 3개를 종료하고 재시작했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun restartGemini_accessibilityUnavailable_returnsUnavailable() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.AccessibilityUnavailable)
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.restartGemini()

        assertEquals(MaintenanceResult.Unavailable, result)
    }

    @Test
    fun restartGemini_recentsUnavailable_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.RecentsUnavailable)
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("최근 앱 화면을 열지 못했습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun restartGemini_notFound_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("최근 앱에서 Gemini를 찾지 못했습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun restartGemini_failure_returnsFailureWithMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Failure("crash"))
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.restartGemini()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("Gemini 재시작 실패: crash", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun terminateGemini_success_returnsSuccessMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.terminateGemini()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("Gemini 앱을 종료했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun terminateGemini_successMultiple_returnsCountInMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 2))
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.terminateGemini()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("Gemini 앱 2개를 종료했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun terminateGemini_accessibilityUnavailable_returnsUnavailable() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.AccessibilityUnavailable)
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.terminateGemini()

        assertEquals(MaintenanceResult.Unavailable, result)
    }

    @Test
    fun terminateSelf_success_returnsSuccessMessage() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.Success(closedCount = 1))
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.terminateSelf()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("앱을 종료했습니다.", (result as MaintenanceResult.Success).message)
    }

    @Test
    fun terminateSelf_notFound_returnsFailure() = runBlocking {
        val closer = FakeGeminiCloser(CloseGeminiAppResult.NotFound)
        val useCase = AppMaintenanceUseCase(closer, FakeMemoryGateway(MemoryCleanupResult.Success))

        val result = useCase.terminateSelf()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("최근 앱에서 GemGemGen을 찾지 못했습니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun cleanMemory_success_returnsSuccessMessage() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.Success)
        val useCase = AppMaintenanceUseCase(FakeGeminiCloser(CloseGeminiAppResult.NotFound), gateway)

        val result = useCase.cleanMemory()

        assertTrue(result is MaintenanceResult.Success)
        assertEquals("메모리 정리를 완료했습니다.", (result as MaintenanceResult.Success).message)
        assertEquals(1, gateway.callCount)
    }

    @Test
    fun cleanMemory_accessibilityUnavailable_returnsUnavailable() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.AccessibilityUnavailable)
        val useCase = AppMaintenanceUseCase(FakeGeminiCloser(CloseGeminiAppResult.NotFound), gateway)

        val result = useCase.cleanMemory()

        assertEquals(MaintenanceResult.Unavailable, result)
    }

    @Test
    fun cleanMemory_inProgress_returnsFailure() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.InProgress)
        val useCase = AppMaintenanceUseCase(FakeGeminiCloser(CloseGeminiAppResult.NotFound), gateway)

        val result = useCase.cleanMemory()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("메모리 정리가 이미 진행 중입니다.", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun cleanMemory_failure_returnsFailureWithMessage() = runBlocking {
        val gateway = FakeMemoryGateway(MemoryCleanupResult.Failure("timeout"))
        val useCase = AppMaintenanceUseCase(FakeGeminiCloser(CloseGeminiAppResult.NotFound), gateway)

        val result = useCase.cleanMemory()

        assertTrue(result is MaintenanceResult.Failure)
        assertEquals("메모리 정리 실패: timeout", (result as MaintenanceResult.Failure).message)
    }

    @Test
    fun customClosers_delegateToRespectiveCloser() = runBlocking {
        val restartCloser = FakeGeminiCloser(CloseGeminiAppResult.Success(1))
        val terminateCloser = FakeGeminiCloser(CloseGeminiAppResult.Success(2))
        val selfCloser = FakeGeminiCloser(CloseGeminiAppResult.Success(3))
        val memoryGateway = FakeMemoryGateway(MemoryCleanupResult.Success)

        val useCase = AppMaintenanceUseCase(
            geminiRestartCloser = restartCloser,
            geminiTerminateCloser = terminateCloser,
            selfAppCloser = selfCloser,
            memoryCleanupGateway = memoryGateway
        )

        useCase.restartGemini()
        assertEquals(1, restartCloser.callCount)
        assertEquals(0, terminateCloser.callCount)
        assertEquals(0, selfCloser.callCount)

        useCase.terminateGemini()
        assertEquals(1, restartCloser.callCount)
        assertEquals(1, terminateCloser.callCount)
        assertEquals(0, selfCloser.callCount)

        useCase.terminateSelf()
        assertEquals(1, restartCloser.callCount)
        assertEquals(1, terminateCloser.callCount)
        assertEquals(1, selfCloser.callCount)
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
}
