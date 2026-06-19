package com.example.gemgemgen.environment.domain

import com.example.gemgemgen.core.AppDefaults

data class EnvironmentStatus(
    val isGeminiInstalled: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val hasWriteSecureSettingsPermission: Boolean = false,
    val isWildcardDirectoryAccessible: Boolean = false,
    val isWildcardDirectoryWritable: Boolean = false,
    val wildcardDirectoryPath: String = "",
    val nullKeyboardTargetImeId: String = AppDefaults.NULL_KEYBOARD_IME_ID,
    val adbGrantCommand: String = ""
) {
    val isReady: Boolean
        get() = isGeminiInstalled &&
            isAccessibilityServiceEnabled &&
            hasWriteSecureSettingsPermission &&
            isWildcardDirectoryAccessible

    val canEditWildcardFiles: Boolean
        get() = isWildcardDirectoryAccessible && isWildcardDirectoryWritable
}

