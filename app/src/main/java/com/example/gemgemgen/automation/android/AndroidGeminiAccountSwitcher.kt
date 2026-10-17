// 역할: Gemini 앱을 실행하여 지정한 Google 계정으로 전환하고 완료 후 GemGemGen 앱으로 복귀하는 안드로이드 게이트웨이입니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.usecase.GeminiAccountSwitchGateway
import com.example.gemgemgen.automation.usecase.SwitchGeminiAccountResult
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.ui.MainActivity

class AndroidGeminiAccountSwitcher(
    private val context: Context
) : GeminiAccountSwitchGateway {
    override suspend fun switchAccount(
        email: String,
        alias: String,
        returnToHostApp: Boolean
    ): SwitchGeminiAccountResult {
        val service = GeminiAccessibilityService.activeService
            ?: return SwitchGeminiAccountResult.AccessibilityUnavailable

        return service.switchGeminiAccount(
            targetEmail = email,
            targetAlias = alias,
            returnToHostApp = returnToHostApp,
            launchGemini = { launchGeminiApp(service) },
            launchHostApp = { launchGemGemGenApp(service) }
        )
    }

    private fun launchGeminiApp(activityStarterContext: Context): Boolean {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(AppDefaults.GEMINI_PACKAGE_NAME)
            ?: packageManager.getLaunchIntentForPackage(AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME)
            ?: return false

        return try {
            activityStarterContext.startActivity(
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchGemGemGenApp(activityStarterContext: Context): Boolean {
        return try {
            val hostIntent = Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            activityStarterContext.startActivity(hostIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
