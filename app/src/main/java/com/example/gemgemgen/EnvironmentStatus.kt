package com.example.gemgemgen

object AppDefaults {
    const val TARGET_PACKAGE_NAME = "com.google.android.apps.bard"
    const val WILDCARD_DIRECTORY = "Download/wildcard"
    const val NULL_KEYBOARD_IME_ID = "com.wparam.nullkeyboard/.NullInputMethod"
    const val DEFAULT_REPEAT_COUNT = 10
}

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
