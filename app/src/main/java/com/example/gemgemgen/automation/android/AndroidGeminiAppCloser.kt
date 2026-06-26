package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.GeminiAppCloser

class AndroidGeminiAppCloser(
    private val context: Context
) : GeminiAppCloser {
    override suspend fun closeGeminiApp(): CloseGeminiAppResult {
        val service = GeminiAccessibilityService.activeService
            ?: return CloseGeminiAppResult.AccessibilityUnavailable

        val result = service.closeGeminiFromRecents()
        bringAppToFront()
        return result
    }

    private fun bringAppToFront() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return

        context.startActivity(
            launchIntent
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
