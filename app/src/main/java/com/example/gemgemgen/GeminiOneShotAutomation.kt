package com.example.gemgemgen

import android.content.Context
import android.content.Intent

class GeminiOneShotAutomation(
    private val context: Context
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

        onStateChange(AutomationUiState.Running("Gemini 앱 실행 중"))
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        service.runOneShot(
            prompt = TEST_PROMPT,
            onStateChange = onStateChange
        )
    }

    companion object {
        const val TEST_PROMPT = "M3 자동 전송 테스트입니다. 숫자 1만 답변해."
    }
}
