// 역할: 앱 실행 전 메모리 확보 및 백그라운드 환경 정리 작업을 조율합니다.
package com.example.gemgemgen.automation.usecase

sealed interface CloseGeminiAppResult {
    data class Success(val closedCount: Int) : CloseGeminiAppResult
    data object AccessibilityUnavailable : CloseGeminiAppResult
    data object RecentsUnavailable : CloseGeminiAppResult
    data object NotFound : CloseGeminiAppResult
    data class Failure(val message: String) : CloseGeminiAppResult
}

fun interface GeminiAppCloser {
    suspend fun closeGeminiApp(): CloseGeminiAppResult
}

sealed interface MemoryCleanupResult {
    data object Success : MemoryCleanupResult
    data object AccessibilityUnavailable : MemoryCleanupResult
    data object InProgress : MemoryCleanupResult
    data class Failure(val message: String) : MemoryCleanupResult
}

fun interface MemoryCleanupGateway {
    suspend fun cleanMemory(): MemoryCleanupResult
}

sealed interface MaintenanceResult {
    val displayMessage: String
    data class Success(val message: String = "") : MaintenanceResult {
        override val displayMessage: String get() = message
    }
    data object Unavailable : MaintenanceResult {
        override val displayMessage: String get() = "접근성 서비스가 켜져 있지 않습니다."
    }
    data class Failure(val message: String) : MaintenanceResult {
        override val displayMessage: String get() = message
    }
}

class AppMaintenanceUseCase(
    private val geminiRestartCloser: GeminiAppCloser,
    private val geminiTerminateCloser: GeminiAppCloser = geminiRestartCloser,
    private val selfAppCloser: GeminiAppCloser = geminiRestartCloser,
    private val memoryCleanupGateway: MemoryCleanupGateway
) {
    constructor(
        geminiAppCloser: GeminiAppCloser,
        memoryCleanupGateway: MemoryCleanupGateway
    ) : this(
        geminiRestartCloser = geminiAppCloser,
        geminiTerminateCloser = geminiAppCloser,
        selfAppCloser = geminiAppCloser,
        memoryCleanupGateway = memoryCleanupGateway
    )

    suspend fun restartGemini(): MaintenanceResult =
        closeApp(geminiRestartCloser, "Gemini 앱을 재시작했습니다.", "Gemini 앱 %d개를 종료하고 재시작했습니다.", "Gemini 재시작 실패: %s", "최근 앱에서 Gemini를 찾지 못했습니다.")

    suspend fun terminateGemini(): MaintenanceResult =
        closeApp(geminiTerminateCloser, "Gemini 앱을 종료했습니다.", "Gemini 앱 %d개를 종료했습니다.", "Gemini 종료 실패: %s", "최근 앱에서 Gemini를 찾지 못했습니다.")

    suspend fun terminateSelf(): MaintenanceResult =
        closeApp(selfAppCloser, "앱을 종료했습니다.", "앱 %d개를 종료했습니다.", "앱 종료 실패: %s", "최근 앱에서 GemGemGen을 찾지 못했습니다.")

    private suspend fun closeApp(
        closer: GeminiAppCloser,
        singleSuccess: String,
        multiSuccess: String,
        failureTemplate: String,
        notFoundMessage: String
    ): MaintenanceResult = when (val result = closer.closeGeminiApp()) {
        is CloseGeminiAppResult.Success -> MaintenanceResult.Success(
            if (result.closedCount <= 1) singleSuccess else multiSuccess.format(result.closedCount)
        )
        CloseGeminiAppResult.AccessibilityUnavailable -> MaintenanceResult.Unavailable
        CloseGeminiAppResult.RecentsUnavailable -> MaintenanceResult.Failure("최근 앱 화면을 열지 못했습니다.")
        CloseGeminiAppResult.NotFound -> MaintenanceResult.Failure(notFoundMessage)
        is CloseGeminiAppResult.Failure -> MaintenanceResult.Failure(failureTemplate.format(result.message))
    }

    suspend fun cleanMemory(): MaintenanceResult = when (val result = memoryCleanupGateway.cleanMemory()) {
        MemoryCleanupResult.Success -> MaintenanceResult.Success("메모리 정리를 완료했습니다.")
        MemoryCleanupResult.AccessibilityUnavailable -> MaintenanceResult.Unavailable
        MemoryCleanupResult.InProgress -> MaintenanceResult.Failure("메모리 정리가 이미 진행 중입니다.")
        is MemoryCleanupResult.Failure -> MaintenanceResult.Failure("메모리 정리 실패: ${result.message}")
    }
}
