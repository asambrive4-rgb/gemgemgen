// 역할: 앱 실행 전 메모리 확보 및 백그라운드 환경 정리 작업을 조율합니다.
package com.example.gemgemgen.automation.usecase

sealed interface MaintenanceResult {
    data class Success(val message: String = "") : MaintenanceResult
    data object Unavailable : MaintenanceResult
    data class Failure(val message: String) : MaintenanceResult
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

    suspend fun restartGemini(): MaintenanceResult {
        return when (val result = geminiRestartCloser.closeGeminiApp()) {
            is CloseGeminiAppResult.Success -> {
                val message = if (result.closedCount <= 1) {
                    "Gemini 앱을 재시작했습니다."
                } else {
                    "Gemini 앱 ${result.closedCount}개를 종료하고 재시작했습니다."
                }
                MaintenanceResult.Success(message)
            }
            CloseGeminiAppResult.AccessibilityUnavailable -> MaintenanceResult.Unavailable
            CloseGeminiAppResult.RecentsUnavailable ->
                MaintenanceResult.Failure("최근 앱 화면을 열지 못했습니다.")
            CloseGeminiAppResult.NotFound ->
                MaintenanceResult.Failure("최근 앱에서 Gemini를 찾지 못했습니다.")
            is CloseGeminiAppResult.Failure ->
                MaintenanceResult.Failure("Gemini 재시작 실패: ${result.message}")
        }
    }

    suspend fun terminateGemini(): MaintenanceResult {
        return when (val result = geminiTerminateCloser.closeGeminiApp()) {
            is CloseGeminiAppResult.Success -> {
                val message = if (result.closedCount <= 1) {
                    "Gemini 앱을 종료했습니다."
                } else {
                    "Gemini 앱 ${result.closedCount}개를 종료했습니다."
                }
                MaintenanceResult.Success(message)
            }
            CloseGeminiAppResult.AccessibilityUnavailable -> MaintenanceResult.Unavailable
            CloseGeminiAppResult.RecentsUnavailable ->
                MaintenanceResult.Failure("최근 앱 화면을 열지 못했습니다.")
            CloseGeminiAppResult.NotFound ->
                MaintenanceResult.Failure("최근 앱에서 Gemini를 찾지 못했습니다.")
            is CloseGeminiAppResult.Failure ->
                MaintenanceResult.Failure("Gemini 종료 실패: ${result.message}")
        }
    }

    suspend fun terminateSelf(): MaintenanceResult {
        return when (val result = selfAppCloser.closeGeminiApp()) {
            is CloseGeminiAppResult.Success -> {
                val message = if (result.closedCount <= 1) {
                    "앱을 종료했습니다."
                } else {
                    "앱 ${result.closedCount}개를 종료했습니다."
                }
                MaintenanceResult.Success(message)
            }
            CloseGeminiAppResult.AccessibilityUnavailable -> MaintenanceResult.Unavailable
            CloseGeminiAppResult.RecentsUnavailable ->
                MaintenanceResult.Failure("최근 앱 화면을 열지 못했습니다.")
            CloseGeminiAppResult.NotFound ->
                MaintenanceResult.Failure("최근 앱에서 GemGemGen을 찾지 못했습니다.")
            is CloseGeminiAppResult.Failure ->
                MaintenanceResult.Failure("앱 종료 실패: ${result.message}")
        }
    }

    suspend fun cleanMemory(): MaintenanceResult {
        return when (val result = memoryCleanupGateway.cleanMemory()) {
            MemoryCleanupResult.Success ->
                MaintenanceResult.Success("메모리 정리를 완료했습니다.")
            MemoryCleanupResult.AccessibilityUnavailable ->
                MaintenanceResult.Unavailable
            MemoryCleanupResult.InProgress ->
                MaintenanceResult.Failure("메모리 정리가 이미 진행 중입니다.")
            is MemoryCleanupResult.Failure ->
                MaintenanceResult.Failure("메모리 정리 실패: ${result.message}")
        }
    }
}
