// 역할: 자동화 완료 후 백그라운드에 열려 있는 Gemini 앱 프로세스를 종료합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.GeminiAppCloser
import com.example.gemgemgen.core.AppDefaults

class AndroidGeminiAppCloser(
    private val context: Context,
    private val relaunchAfterClose: Boolean = true
) : GeminiAppCloser {
    override suspend fun closeGeminiApp(): CloseGeminiAppResult {
        val service = GeminiAccessibilityService.activeService
            ?: return CloseGeminiAppResult.AccessibilityUnavailable

        val result = service.closeGeminiFromRecents()
        if (result !is CloseGeminiAppResult.Success) return result
        if (!relaunchAfterClose) return result

        return if (launchGemini()) {
            result
        } else {
            CloseGeminiAppResult.Failure("Gemini 앱 재실행에 실패했습니다.")
        }
    }

    private fun launchGemini(): Boolean {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(AppDefaults.GEMINI_PACKAGE_NAME)
            ?: return false

        context.startActivity(
            launchIntent
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        return true
    }
}
