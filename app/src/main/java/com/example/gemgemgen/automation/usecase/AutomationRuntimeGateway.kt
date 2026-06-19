package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp

fun interface PromptAutomationGatewayProvider {
    fun current(targetApp: AutomationTargetApp): PromptAutomationGateway?
}

fun interface TargetAppLauncher {
    fun launch(targetApp: AutomationTargetApp): Boolean
}
