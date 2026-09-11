// 역할: 디바이스 케어를 통한 메모리 정리 작업을 요청하고 결과를 반환합니다.
package com.example.gemgemgen.automation.usecase

sealed interface MemoryCleanupResult {
    data object Success : MemoryCleanupResult
    data object AccessibilityUnavailable : MemoryCleanupResult
    data object InProgress : MemoryCleanupResult
    data class Failure(val message: String) : MemoryCleanupResult
}

interface MemoryCleanupGateway {
    suspend fun cleanMemory(): MemoryCleanupResult
}

@Deprecated("AppMaintenanceUseCase로 대체되었습니다. AppMaintenanceUseCase를 사용하세요.")
class CleanDeviceMemoryUseCase(
    private val memoryCleanupGateway: MemoryCleanupGateway
) {
    suspend fun clean(): MemoryCleanupResult {
        return memoryCleanupGateway.cleanMemory()
    }
}
