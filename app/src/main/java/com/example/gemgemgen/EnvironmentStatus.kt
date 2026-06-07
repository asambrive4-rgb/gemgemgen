package com.example.gemgemgen

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

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
    val wildcardDirectoryPath: String = "",
    val nullKeyboardTargetImeId: String = AppDefaults.NULL_KEYBOARD_IME_ID,
    val adbGrantCommand: String = ""
) {
    val isReady: Boolean
        get() = isGeminiInstalled &&
            isAccessibilityServiceEnabled &&
            hasWriteSecureSettingsPermission &&
            isWildcardDirectoryAccessible
}

object EnvironmentStatusChecker {
    fun check(context: Context): EnvironmentStatus {
        val wildcardFolderUri = WildcardFolderStore.getFolderUri(context)

        return EnvironmentStatus(
            isGeminiInstalled = isPackageInstalled(context, AppDefaults.TARGET_PACKAGE_NAME),
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            hasWriteSecureSettingsPermission = hasWriteSecureSettingsPermission(context),
            isWildcardDirectoryAccessible = wildcardFolderUri != null &&
                WildcardRepository.canReadFolder(context, wildcardFolderUri),
            wildcardDirectoryPath = wildcardFolderUri?.toString().orEmpty(),
            nullKeyboardTargetImeId = AppDefaults.NULL_KEYBOARD_IME_ID,
            adbGrantCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        )
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (GeminiAccessibilityService.activeService != null) return true

        val expectedService = ComponentName(
            context,
            GeminiAccessibilityService::class.java
        )
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return AccessibilityServiceMatcher.containsService(
            enabledServices = enabledServices,
            expectedPackageName = expectedService.packageName,
            expectedClassName = expectedService.className
        )
    }

    private fun hasWriteSecureSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
