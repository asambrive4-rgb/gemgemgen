package com.example.gemgemgen.environment.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.gemgemgen.automation.android.GeminiAccessibilityService
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.environment.usecase.EnvironmentGateway
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderAccessChecker
import com.example.gemgemgen.wildcard.android.WildcardFolderStore

class AndroidEnvironmentGateway(
    private val context: Context
) : EnvironmentGateway {
    override fun check(): EnvironmentStatus {
        val wildcardFolderUri = WildcardFolderStore.getFolderUri(context)

        return EnvironmentStatus(
            isGeminiInstalled = isPackageInstalled(AppDefaults.TARGET_PACKAGE_NAME),
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            hasWriteSecureSettingsPermission = hasWriteSecureSettingsPermission(),
            isWildcardDirectoryAccessible = wildcardFolderUri != null &&
                AndroidWildcardFolderAccessChecker.canReadFolder(context, wildcardFolderUri),
            isWildcardDirectoryWritable = wildcardFolderUri != null &&
                AndroidWildcardFolderAccessChecker.canWriteFolder(context, wildcardFolderUri),
            wildcardDirectoryPath = wildcardFolderUri?.toString().orEmpty(),
            nullKeyboardTargetImeId = AppDefaults.NULL_KEYBOARD_IME_ID,
            adbGrantCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (GeminiAccessibilityService.activeService != null) return true

        val expectedService = ComponentName(context, GeminiAccessibilityService::class.java)
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

    private fun hasWriteSecureSettingsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
