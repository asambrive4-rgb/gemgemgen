package com.example.gemgemgen

import android.content.Context
import android.content.Intent

class GeminiOneShotAutomation(
    private val context: Context,
    private val imeManager: ImeManager = ImeManager.android(context)
) {
    fun run(onStateChange: (AutomationUiState) -> Unit) {
        val service = GeminiAccessibilityService.activeService
        if (service == null) {
            onStateChange(AutomationUiState.Failure("접근성 서비스가 켜져 있지 않습니다."))
            return
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(AppDefaults.TARGET_PACKAGE_NAME)
        if (launchIntent == null) {
            onStateChange(AutomationUiState.Failure("Gemini 앱을 찾지 못했습니다."))
            return
        }

        onStateChange(AutomationUiState.Running("Null Keyboard로 전환 중"))
        val imeSwitchResult = imeManager.switchToNullKeyboard()
        if (imeSwitchResult is ImeSwitchResult.Failure) {
            onStateChange(
                AutomationUiState.Failure(
                    "${imeSwitchResult.message} WRITE_SECURE_SETTINGS 권한과 Null Keyboard 설치 상태를 확인해주세요."
                )
            )
            return
        }
        val imeSession = (imeSwitchResult as ImeSwitchResult.Success).session

        onStateChange(AutomationUiState.Running("Gemini 앱 실행 중"))
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        service.runOneShot(
            prompt = TEST_PROMPT,
            onStateChange = restoringStateCallback(
                imeSession = imeSession,
                onStateChange = onStateChange
            )
        )
    }

    private fun restoringStateCallback(
        imeSession: ImeSwitchSession,
        onStateChange: (AutomationUiState) -> Unit
    ): (AutomationUiState) -> Unit {
        var terminalStateHandled = false

        return callback@{ state ->
            when (state) {
                AutomationUiState.Success,
                is AutomationUiState.Failure -> {
                    if (terminalStateHandled) return@callback

                    terminalStateHandled = true
                    onStateChange(AutomationUiState.Running("원래 입력기로 복구 중"))

                    when (val restoreResult = imeManager.restore(imeSession)) {
                        ImeRestoreResult.Success -> onStateChange(state)
                        is ImeRestoreResult.Failure -> {
                            onStateChange(
                                AutomationUiState.Failure(
                                    "입력기 복구 실패. 원래 입력기: " +
                                        "${restoreResult.originalImeId}, 현재 입력기: " +
                                        "${restoreResult.currentImeId ?: "확인 불가"}"
                                )
                            )
                        }
                    }
                }
                else -> onStateChange(state)
            }
        }
    }

    companion object {
        const val TEST_PROMPT = "M3 자동 전송 테스트입니다. 숫자 1만 답변해."
    }
}
