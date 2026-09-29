// 역할: 안드로이드 시스템에서 접근성, 배터리, 오버레이 권한 상태를 직접 조회합니다.
package com.example.gemgemgen.environment.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.gemgemgen.automation.android.GeminiAccessibilityService
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.environment.domain.EnvironmentReport
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.environment.usecase.EnvironmentGateway
import com.example.gemgemgen.wildcard.android.AndroidWildcardDirectStorage
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderAccessChecker
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderRepository

class AndroidEnvironmentGateway(
    context: Context
) : EnvironmentGateway, OverlayPermissionGateway {
    private val appContext = context.applicationContext
    private val wildcardDirectStorage = AndroidWildcardDirectStorage()

    override fun check(): EnvironmentReport {
        val wildcardFolderUri = AndroidWildcardFolderRepository.getFolderUri(appContext)
        val hasAllFilesAccess = AndroidWildcardDirectStorage.hasAllFilesAccess()
        val directFolder = if (hasAllFilesAccess) {
            runCatching { wildcardDirectStorage.ensureFolder() }.getOrNull()
        } else {
            null
        }
        val enabledImeList = try {
            val imm = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.enabledInputMethodList?.map { it.id }.orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        val targetImeId = AppDefaults.NULL_KEYBOARD_IME_CANDIDATES.firstOrNull { candidate ->
            enabledImeList.any { enabled -> enabled.equals(candidate, ignoreCase = true) }
        } ?: AppDefaults.NULL_KEYBOARD_IME_ID

        val hasOverlay = isGranted()
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return EnvironmentReport(
            status = EnvironmentStatus(
                isGeminiInstalled = isPackageInstalled(AppDefaults.GEMINI_PACKAGE_NAME),
                isChatGptInstalled = isPackageInstalled(AppDefaults.CHATGPT_PACKAGE_NAME),
                isFlowInstalled = isPackageInstalled(AppDefaults.FLOW_PACKAGE_NAME),
                isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(),
                hasWriteSecureSettingsPermission = hasWriteSecureSettingsPermission(),
                isWildcardDirectoryAccessible = if (hasAllFilesAccess) {
                    directFolder != null && directFolder.isDirectory && directFolder.canRead()
                } else {
                    wildcardFolderUri != null && AndroidWildcardFolderAccessChecker.canReadFolder(appContext, wildcardFolderUri)
                },
                isWildcardDirectoryWritable = if (hasAllFilesAccess) {
                    directFolder != null && directFolder.isDirectory && directFolder.canWrite()
                } else {
                    wildcardFolderUri != null && AndroidWildcardFolderAccessChecker.canWriteFolder(appContext, wildcardFolderUri)
                },
                hasOverlayPermission = hasOverlay,
                hasNotificationPermission = hasNotification,
                hasAllFilesAccess = hasAllFilesAccess
            ),
            setupInfo = EnvironmentSetupInfo(
                wildcardDirectoryPath = if (hasAllFilesAccess) {
                    directFolder?.absolutePath ?: wildcardDirectStorage.folderPath()
                } else {
                    wildcardFolderUri?.toString().orEmpty()
                },
                nullKeyboardTargetImeId = targetImeId,
                adbGrantCommand =
                    "adb shell pm grant ${appContext.packageName} android.permission.WRITE_SECURE_SETTINGS"
            )
        )
    }

    override fun isGranted(): Boolean = Settings.canDrawOverlays(appContext)

    private fun isPackageInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (GeminiAccessibilityService.activeService != null) return true
        val expectedService = ComponentName(appContext, GeminiAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return AccessibilityServiceMatcher.containsService(
            enabledServices = enabledServices,
            expectedPackageName = expectedService.packageName,
            expectedClassName = expectedService.className
        )
    }

    private fun hasWriteSecureSettingsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
}

