package com.example.gemgemgen.automation.usecase

sealed interface CloseGeminiAppResult {
    data class Success(val closedCount: Int) : CloseGeminiAppResult
    data object AccessibilityUnavailable : CloseGeminiAppResult
    data object RecentsUnavailable : CloseGeminiAppResult
    data object NotFound : CloseGeminiAppResult
    data class Failure(val message: String) : CloseGeminiAppResult
}

interface GeminiAppCloser {
    suspend fun closeGeminiApp(): CloseGeminiAppResult
}

@Deprecated("AppMaintenanceUseCase로 대체되었습니다. AppMaintenanceUseCase를 사용하세요.")
class CloseGeminiAppUseCase(
    private val geminiAppCloser: GeminiAppCloser
) {
    suspend fun close(): CloseGeminiAppResult {
        return geminiAppCloser.closeGeminiApp()
    }
}
