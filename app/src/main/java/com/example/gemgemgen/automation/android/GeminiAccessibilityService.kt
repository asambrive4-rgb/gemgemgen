package com.example.gemgemgen.automation.android

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway

class GeminiAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val geminiAutomation by lazy {
        GeminiPromptAutomation(
            handler = handler,
            rootProvider = { rootInActiveWindow }
        )
    }
    private val chatGptAutomation by lazy {
        ChatGptPromptAutomation(
            handler = handler,
            rootProvider = { rootInActiveWindow }
        )
    }

    override fun onServiceConnected() {
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        if (activeService == this) {
            activeService = null
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    internal fun gatewayFor(targetApp: AutomationTargetApp): PromptAutomationGateway {
        return when (targetApp) {
            AutomationTargetApp.GEMINI -> geminiAutomation
            AutomationTargetApp.CHATGPT -> chatGptAutomation
        }
    }

    companion object {
        var activeService: GeminiAccessibilityService? = null
            private set
    }
}
