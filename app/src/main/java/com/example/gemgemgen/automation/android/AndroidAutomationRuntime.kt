package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGatewayProvider
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.core.AppDefaults

object ActivePromptAutomationGatewayProvider : PromptAutomationGatewayProvider {
    override fun current(targetApp: AutomationTargetApp): PromptAutomationGateway? {
        return GeminiAccessibilityService.activeService?.gatewayFor(targetApp)
    }
}

class AndroidTargetAppLauncher(
    private val context: Context
) : TargetAppLauncher {
    override fun launch(targetApp: AutomationTargetApp): Boolean {
        val packageName = when (targetApp) {
            AutomationTargetApp.GEMINI -> AppDefaults.GEMINI_PACKAGE_NAME
            AutomationTargetApp.CHATGPT -> AppDefaults.CHATGPT_PACKAGE_NAME
        }
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?: return false

        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
