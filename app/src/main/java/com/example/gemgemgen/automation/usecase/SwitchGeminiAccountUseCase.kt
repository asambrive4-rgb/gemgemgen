// 역할: Gemini 계정 자동 전환, 원격 수신 기기 전환 요청 및 계정 활성화 상태 갱신을 총괄하는 유스케이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.android.GeminiAccountSwitchResult
import com.example.gemgemgen.automation.domain.GeminiAccountProfile
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

sealed interface SwitchGeminiAccountExecutionResult {
    data class Success(val updatedAccounts: List<GeminiAccountProfile>, val message: String) : SwitchGeminiAccountExecutionResult
    data class Failure(val message: String) : SwitchGeminiAccountExecutionResult
}

class SwitchGeminiAccountUseCase(
    private val manageGeminiAccounts: ManageGeminiAccountsUseCase,
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val switcherGateway: GeminiAccountSwitcherGateway
) {
    suspend fun execute(
        account: GeminiAccountProfile,
        mode: AutomationMode,
        onProgress: (phase: String, message: String) -> Unit
    ): SwitchGeminiAccountExecutionResult {
        if (mode == AutomationMode.SENDER) {
            return when (val result = manageRemoteAutomation.switchGeminiAccount(account.id, account.alias, account.identifier)) {
                is RemoteActionResult.Success -> {
                    val updated = manageGeminiAccounts.activateAccount(account.id)
                    SwitchGeminiAccountExecutionResult.Success(updated, "수신 기기 Gemini 계정을 [${account.alias}]로 전환했습니다.")
                }
                is RemoteActionResult.Failure -> SwitchGeminiAccountExecutionResult.Failure(result.message)
            }
        }

        if (!switcherGateway.isServiceAvailable) {
            return SwitchGeminiAccountExecutionResult.Failure("접근성 서비스를 먼저 활성화해주세요.")
        }
        if (account.identifier.isBlank()) {
            return SwitchGeminiAccountExecutionResult.Failure("계정 식별자(구글 이메일)가 비어 있습니다.")
        }

        return when (val result = switcherGateway.switchAccount(account.identifier, account.alias, onProgress)) {
            is GeminiAccountSwitchResult.Success -> {
                val updated = manageGeminiAccounts.activateAccount(account.id)
                SwitchGeminiAccountExecutionResult.Success(updated, result.message)
            }
            is GeminiAccountSwitchResult.Failure -> SwitchGeminiAccountExecutionResult.Failure(result.message)
            GeminiAccountSwitchResult.Unavailable -> SwitchGeminiAccountExecutionResult.Failure("접근성 서비스를 사용할 수 없습니다.")
        }
    }

    fun launchManualSwitch(): Boolean = switcherGateway.launchGeminiForManualSwitch()
}
