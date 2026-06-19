package com.example.gemgemgen.environment.domain

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.core.AppDefaults

data class EnvironmentStatus(
    val isGeminiInstalled: Boolean = false,
    val isChatGptInstalled: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val hasWriteSecureSettingsPermission: Boolean = false,
    val isWildcardDirectoryAccessible: Boolean = false,
    val isWildcardDirectoryWritable: Boolean = false,
    val wildcardDirectoryPath: String = "",
    val nullKeyboardTargetImeId: String = AppDefaults.NULL_KEYBOARD_IME_ID,
    val adbGrantCommand: String = ""
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
        }
    }

    val canEditWildcardFiles: Boolean
        get() = isWildcardDirectoryAccessible && isWildcardDirectoryWritable
}

