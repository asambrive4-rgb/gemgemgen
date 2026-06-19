package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.usecase.GeminiAppLauncher
import com.example.gemgemgen.automation.usecase.GeminiPromptGateway
import com.example.gemgemgen.automation.usecase.GeminiPromptGatewayProvider
import com.example.gemgemgen.core.AppDefaults

object ActiveGeminiPromptGatewayProvider : GeminiPromptGatewayProvider {
    override fun current(): GeminiPromptGateway? {
        return GeminiAccessibilityService.activeService
    }
}

class AndroidGeminiAppLauncher(
    private val context: Context
) : GeminiAppLauncher {
    override fun launch(): Boolean {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(AppDefaults.TARGET_PACKAGE_NAME)
            ?: return false

        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
