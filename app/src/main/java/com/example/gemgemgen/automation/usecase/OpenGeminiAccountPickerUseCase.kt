// 역할: Gemini 앱의 계정 목록(ID 목록) 열기 자동화 또는 앱 전면 실행을 요청하는 유스케이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.android.GeminiAccountSwitchResult
import com.example.gemgemgen.automation.domain.GeminiAccountSwitchProgressPolicy
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteActionResult
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

sealed interface OpenGeminiAccountPickerResult {
    data class Success(val message: String) : OpenGeminiAccountPickerResult
    data class Failure(val message: String) : OpenGeminiAccountPickerResult
}

class OpenGeminiAccountPickerUseCase(
    private val manageRemoteAutomation: ManageRemoteAutomationUseCase,
    private val switcherGateway: GeminiAccountSwitcherGateway
) {
    suspend fun execute(
        mode: AutomationMode,
        onProgress: (phase: String, message: String) -> Unit = { _, _ -> }
    ): OpenGeminiAccountPickerResult {
        if (mode == AutomationMode.SENDER) {
            return when (val result = manageRemoteAutomation.switchGeminiAccount("", "", "")) {
                is RemoteActionResult.Success -> OpenGeminiAccountPickerResult.Success("수신 기기의 Gemini 계정 목록을 열었습니다.")
                is RemoteActionResult.Failure -> OpenGeminiAccountPickerResult.Failure(result.message)
            }
        }

        if (!switcherGateway.isServiceAvailable) {
            val launched = switcherGateway.launchGeminiForManualSwitch()
            return if (launched) {
                OpenGeminiAccountPickerResult.Success("Gemini 앱을 실행했습니다. 상단 프로필에서 계정을 선택해주세요.")
            } else {
                OpenGeminiAccountPickerResult.Failure(GeminiAccountSwitchProgressPolicy.ERROR_ACCESSIBILITY_REQUIRED)
            }
        }

        return when (val result = switcherGateway.openAccountPicker(onProgress)) {
            is GeminiAccountSwitchResult.Success -> OpenGeminiAccountPickerResult.Success(result.message)
            is GeminiAccountSwitchResult.Failure -> {
                switcherGateway.launchGeminiForManualSwitch()
                OpenGeminiAccountPickerResult.Failure(result.message)
            }
            GeminiAccountSwitchResult.Unavailable -> {
                switcherGateway.launchGeminiForManualSwitch()
                OpenGeminiAccountPickerResult.Failure(GeminiAccountSwitchProgressPolicy.ERROR_SERVICE_UNAVAILABLE)
            }
        }
    }
}
