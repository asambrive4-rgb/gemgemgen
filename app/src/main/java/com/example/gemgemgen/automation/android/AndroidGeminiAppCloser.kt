// 역할: 최근 앱 목록을 통해 Gemini 앱 또는 자기 앱(GemGemGen) 프로세스를 종료합니다.
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

/**
 * 최근 앱에서 이 앱(GemGemGen) 카드만 닫아 프로세스를 종료한다.
 * Gemini 종료와 동일한 접근성 경로를 사용한다.
 */
class AndroidSelfAppCloser(
    private val context: Context
) : GeminiAppCloser {
    override suspend fun closeGeminiApp(): CloseGeminiAppResult {
        val service = GeminiAccessibilityService.activeService
            ?: return CloseGeminiAppResult.AccessibilityUnavailable

        val taskTitle = context.applicationInfo
            .loadLabel(context.packageManager)
            .toString()
        return service.closeAppFromRecents(
            taskTitle = taskTitle,
            closeDescription = "$taskTitle 앱 종료"
        )
    }
}
