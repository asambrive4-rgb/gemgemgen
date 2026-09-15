// 역할: 현재 기기의 접근성 자동화 게이트웨이와 대상 앱 실행기를 제공하는 안드로이드 연결부입니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGatewayProvider
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGateway
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGatewayProvider
import com.example.gemgemgen.core.AppDefaults

object ActivePromptAutomationGatewayProvider : PromptAutomationGatewayProvider {
    override fun current(targetApp: AutomationTargetApp): PromptAutomationGateway? {
        return GeminiAccessibilityService.activeService?.gatewayFor(targetApp)
    }
}

object ActiveVariationPromptAutomationGatewayProvider : VariationPromptAutomationGatewayProvider {
    override fun current(): VariationPromptAutomationGateway? {
        return GeminiAccessibilityService.activeService?.variationGateway()
    }
}

class AndroidTargetAppLauncher(
    private val context: Context
) : TargetAppLauncher {
    override fun launch(targetApp: AutomationTargetApp): Boolean {
        val packageName = when (targetApp) {
            AutomationTargetApp.GEMINI -> AppDefaults.GEMINI_PACKAGE_NAME
            AutomationTargetApp.CHATGPT -> AppDefaults.CHATGPT_PACKAGE_NAME
            AutomationTargetApp.FLOW -> AppDefaults.FLOW_PACKAGE_NAME
        }
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?: return false

        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
