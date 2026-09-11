// 역할: 앱 실행에 필요한 접근성, 배터리, 오버레이 권한의 활성화 여부 상태를 정의합니다.
package com.example.gemgemgen.environment.domain

import com.example.gemgemgen.automation.domain.AutomationTargetApp

data class EnvironmentStatus(
    val isGeminiInstalled: Boolean = false,
    val isChatGptInstalled: Boolean = false,
    val isFlowInstalled: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val hasWriteSecureSettingsPermission: Boolean = false,
    val isWildcardDirectoryAccessible: Boolean = false,
    val isWildcardDirectoryWritable: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasAllFilesAccess: Boolean = false
) {
    fun isReadyFor(targetApp: AutomationTargetApp): Boolean {
        return isTargetAppInstalled(targetApp) &&
            isAccessibilityServiceEnabled &&
            hasWriteSecureSettingsPermission &&
            isWildcardDirectoryAccessible
    }

    fun isTargetAppInstalled(targetApp: AutomationTargetApp): Boolean {
        return when (targetApp) {
            AutomationTargetApp.GEMINI -> isGeminiInstalled
            AutomationTargetApp.CHATGPT -> isChatGptInstalled
            AutomationTargetApp.FLOW -> isFlowInstalled
        }
    }

    val canEditWildcardFiles: Boolean
        get() = isWildcardDirectoryAccessible && isWildcardDirectoryWritable
}

data class EnvironmentSetupInfo(
    val wildcardDirectoryPath: String = "",
    val nullKeyboardTargetImeId: String = "",
    val adbGrantCommand: String = ""
)

data class EnvironmentReport(
    val status: EnvironmentStatus = EnvironmentStatus(),
    val setupInfo: EnvironmentSetupInfo = EnvironmentSetupInfo()
)

