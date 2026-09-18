// 역할: GeminiAccessibilityService 인스턴스를 통해 계정 목록 열기 매크로를 실행하고 Gemini 앱을 실행하는 안드로이드 게이트웨이입니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.usecase.GeminiAccountSwitcherGateway
import com.example.gemgemgen.core.AppDefaults

class AndroidGeminiAccountSwitcherGateway(
    private val context: Context? = null
) : GeminiAccountSwitcherGateway {
    override val isServiceAvailable: Boolean
        get() = GeminiAccessibilityService.activeService != null

    override suspend fun openAccountPicker(
        onProgress: (phase: String, message: String) -> Unit
    ): GeminiAccountSwitchResult {
        val service = GeminiAccessibilityService.activeService ?: return GeminiAccountSwitchResult.Unavailable
        return service.openGeminiAccountPicker(onProgress)
    }

    override fun launchGeminiForManualSwitch(): Boolean {
        val service = GeminiAccessibilityService.activeService
        val ctx = service ?: context ?: return false
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(AppDefaults.GEMINI_PACKAGE_NAME)
            ?: pm.getLaunchIntentForPackage(AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }
}
