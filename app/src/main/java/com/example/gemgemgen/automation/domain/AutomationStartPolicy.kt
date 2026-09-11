// 역할: 권한, 입력값, 대상 앱 상태를 확인하여 자동화 시작 가능 여부를 판정합니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

object AutomationStartPolicy {
    fun hasPromptTemplate(promptTemplate: String): Boolean =
        promptTemplate.isNotBlank()

    fun hasRunRequirements(
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String
    ): Boolean =
        environmentStatus.isReadyFor(targetApp) && hasPromptTemplate(promptTemplate)

    fun canRun(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String,
        isRunning: Boolean,
        remoteAutomationStatus: RemoteAutomationStatus
    ): Boolean = when (mode) {
        AutomationMode.NORMAL ->
            hasRunRequirements(environmentStatus, targetApp, promptTemplate) && !isRunning
        AutomationMode.SENDER ->
            hasPromptTemplate(promptTemplate) &&
                remoteAutomationStatus.canSend &&
                !isRunning
        AutomationMode.RECEIVER -> false
    }
}
