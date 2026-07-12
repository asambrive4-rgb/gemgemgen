package com.example.gemgemgen.automation.android

import android.content.Context
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.GeminiAppCloser

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
