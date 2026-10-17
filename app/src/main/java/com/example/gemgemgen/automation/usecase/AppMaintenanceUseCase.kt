// 역할: 앱 유지보수(앱 재시작/종료), 모드별 로컬·원격 메모리 정리 및 제미나이 구글 계정 자동 전환 오케스트레이션을 수행하는 유스케이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

sealed interface SwitchGeminiAccountResult {
    data object Success : SwitchGeminiAccountResult
    data object AccessibilityUnavailable : SwitchGeminiAccountResult
    data object InProgress : SwitchGeminiAccountResult
    data class Failure(val message: String) : SwitchGeminiAccountResult
}

fun interface GeminiAccountSwitchGateway {
    suspend fun switchAccount(
        email: String,
        alias: String,
        returnToHostApp: Boolean
    ): SwitchGeminiAccountResult
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
    private val geminiRestartCloser: GeminiAppCloser = GeminiAppCloser { CloseGeminiAppResult.AccessibilityUnavailable },
    private val selfAppCloser: GeminiAppCloser = geminiRestartCloser,
    private val memoryCleanupGateway: MemoryCleanupGateway = MemoryCleanupGateway { MemoryCleanupResult.AccessibilityUnavailable },
    private val geminiAccountSwitchGateway: GeminiAccountSwitchGateway = GeminiAccountSwitchGateway { _, _, _ ->
        SwitchGeminiAccountResult.AccessibilityUnavailable
    },
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase? = null
) {
    suspend fun restartGemini(): MaintenanceResult =
        closeApp(geminiRestartCloser, "Gemini 앱을 재시작했습니다.", "Gemini 앱 %d개를 종료하고 재시작했습니다.", "Gemini 재시작 실패: %s", "최근 앱에서 Gemini를 찾지 못했습니다.")

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

    suspend fun cleanMemory(
        mode: AutomationMode = AutomationMode.NORMAL
    ): MaintenanceResult = when (mode) {
        AutomationMode.SENDER -> cleanRemoteMemory()
        AutomationMode.NORMAL,
        AutomationMode.RECEIVER -> cleanLocalMemory()
    }

    suspend fun cleanLocalMemory(): MaintenanceResult =
        when (val result = memoryCleanupGateway.cleanMemory()) {
            MemoryCleanupResult.Success -> MaintenanceResult.Success("메모리 정리를 완료했습니다.")
            MemoryCleanupResult.AccessibilityUnavailable -> MaintenanceResult.Unavailable
            MemoryCleanupResult.InProgress -> MaintenanceResult.Failure("메모리 정리가 이미 진행 중입니다.")
            is MemoryCleanupResult.Failure -> MaintenanceResult.Failure("메모리 정리 실패: ${result.message}")
        }

    suspend fun cleanRemoteMemory(): MaintenanceResult =
        if (manageRemoteAutomation != null) {
            when (val result = manageRemoteAutomation.cleanMemory()) {
                RemoteActionResult.Success -> MaintenanceResult.Success("수신 기기 메모리를 정리했습니다.")
                is RemoteActionResult.Failure -> MaintenanceResult.Failure(result.message)
            }
        } else {
            MaintenanceResult.Failure("원격 메모리 정리를 수행할 수 없습니다.")
        }

    suspend fun switchGeminiAccount(
        email: String,
        alias: String,
        mode: AutomationMode = AutomationMode.NORMAL
    ): MaintenanceResult {
        val displayLabel = alias.trim().ifBlank { email.trim().ifBlank { "선택한 계정" } }
        return when (mode) {
            AutomationMode.NORMAL,
            AutomationMode.RECEIVER -> switchLocalGeminiAccount(
                email = email,
                alias = alias,
                returnToHostApp = true
            )
            AutomationMode.SENDER -> coroutineScope {
                val remoteDeferred = async {
                    switchRemoteGeminiAccount(email = email, alias = alias)
                }
                val localResult = switchLocalGeminiAccount(
                    email = email,
                    alias = alias,
                    returnToHostApp = true
                )
                val remoteResult = remoteDeferred.await()
                combineSenderSwitchResults(
                    displayLabel = displayLabel,
                    localResult = localResult,
                    remoteResult = remoteResult
                )
            }
        }
    }

    suspend fun switchLocalGeminiAccount(
        email: String,
        alias: String,
        returnToHostApp: Boolean = true
    ): MaintenanceResult {
        val displayLabel = alias.trim().ifBlank { email.trim().ifBlank { "선택한 계정" } }
        return when (val result = geminiAccountSwitchGateway.switchAccount(email, alias, returnToHostApp)) {
            SwitchGeminiAccountResult.Success ->
                MaintenanceResult.Success("Gemini 계정을 '$displayLabel'(으)로 전환했습니다.")
            SwitchGeminiAccountResult.AccessibilityUnavailable ->
                MaintenanceResult.Unavailable
            SwitchGeminiAccountResult.InProgress ->
                MaintenanceResult.Failure("Gemini 계정 전환이 이미 진행 중입니다.")
            is SwitchGeminiAccountResult.Failure ->
                MaintenanceResult.Failure("Gemini 계정 전환 실패: ${result.message}")
        }
    }

    suspend fun switchRemoteGeminiAccount(
        email: String,
        alias: String
    ): MaintenanceResult {
        val displayLabel = alias.trim().ifBlank { email.trim().ifBlank { "선택한 계정" } }
        return if (manageRemoteAutomation != null) {
            when (val result = manageRemoteAutomation.switchGeminiAccount(email = email, alias = alias)) {
                RemoteActionResult.Success ->
                    MaintenanceResult.Success("수신 기기 Gemini 계정을 '$displayLabel'(으)로 전환했습니다.")
                is RemoteActionResult.Failure ->
                    MaintenanceResult.Failure(result.message)
            }
        } else {
            MaintenanceResult.Failure("원격 계정 전환을 수행할 수 없습니다.")
        }
    }

    private fun combineSenderSwitchResults(
        displayLabel: String,
        localResult: MaintenanceResult,
        remoteResult: MaintenanceResult
    ): MaintenanceResult {
        val localSuccess = localResult is MaintenanceResult.Success
        val remoteSuccess = remoteResult is MaintenanceResult.Success
        return when {
            localSuccess && remoteSuccess ->
                MaintenanceResult.Success("송신·수신 기기의 Gemini 계정을 '$displayLabel'(으)로 전환했습니다.")
            localSuccess && !remoteSuccess ->
                MaintenanceResult.Failure(
                    "송신 기기는 '$displayLabel'(으)로 전환했으나 수신 기기 전환 실패: ${remoteResult.displayMessage}"
                )
            !localSuccess && remoteSuccess ->
                MaintenanceResult.Failure(
                    "수신 기기는 '$displayLabel'(으)로 전환했으나 송신 기기 전환 실패: ${localResult.displayMessage}"
                )
            else ->
                MaintenanceResult.Failure(
                    "송신·수신 기기 계정 전환 실패 (송신: ${localResult.displayMessage} / 수신: ${remoteResult.displayMessage})"
                )
        }
    }
}

