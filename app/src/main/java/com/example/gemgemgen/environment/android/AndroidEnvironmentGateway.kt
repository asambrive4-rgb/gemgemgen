// 역할: 안드로이드 시스템에서 접근성, 배터리, 오버레이 권한 상태를 직접 조회합니다.
package com.example.gemgemgen.environment.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderAccessChecker
import com.example.gemgemgen.wildcard.android.AndroidWildcardDirectStorage
import com.example.gemgemgen.wildcard.android.WildcardFolderStore

class AndroidEnvironmentGateway(
    context: Context
) : EnvironmentGateway, OverlayPermissionGateway {
    private val appContext = context.applicationContext
    private val packageInstallChecker = AndroidPackageInstallChecker(appContext)
    private val accessibilityStatus = AndroidAccessibilityServiceStatus(appContext)
    private val secureSettingsPermission = AndroidSecureSettingsPermissionChecker(appContext)
    private val wildcardDirectoryStatus = AndroidWildcardDirectoryStatus(appContext)
    private val wildcardDirectStorage = AndroidWildcardDirectStorage()

    override fun check(): EnvironmentReport {
        val wildcardFolderUri = wildcardDirectoryStatus.folderUri()
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
                isGeminiInstalled = packageInstallChecker.isInstalled(
                    AppDefaults.GEMINI_PACKAGE_NAME
                ),
                isChatGptInstalled = packageInstallChecker.isInstalled(
                    AppDefaults.CHATGPT_PACKAGE_NAME
                ),
                isFlowInstalled = packageInstallChecker.isInstalled(
                    AppDefaults.FLOW_PACKAGE_NAME
                ),
                isAccessibilityServiceEnabled = accessibilityStatus.isEnabled(),
                hasWriteSecureSettingsPermission = secureSettingsPermission.isGranted(),
                isWildcardDirectoryAccessible = if (hasAllFilesAccess) {
                    directFolder != null && wildcardDirectStorage.canReadFolder()
                } else {
                    wildcardFolderUri != null && wildcardDirectoryStatus.canRead(wildcardFolderUri)
                },
                isWildcardDirectoryWritable = if (hasAllFilesAccess) {
                    directFolder != null && wildcardDirectStorage.canWriteFolder()
                } else {
                    wildcardFolderUri != null && wildcardDirectoryStatus.canWrite(wildcardFolderUri)
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

    override fun isGranted(): Boolean {
        return Settings.canDrawOverlays(appContext)
    }
}

private class AndroidPackageInstallChecker(
    private val context: Context
) {
    fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}

private class AndroidAccessibilityServiceStatus(
    private val context: Context
) {
    fun isEnabled(): Boolean {
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
}

private class AndroidSecureSettingsPermissionChecker(
    private val context: Context
) {
    fun isGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private class AndroidWildcardDirectoryStatus(
    private val context: Context
) {
    fun folderUri(): Uri? {
        return WildcardFolderStore.getFolderUri(context)
    }

    fun canRead(folderUri: Uri): Boolean {
        return AndroidWildcardFolderAccessChecker.canReadFolder(context, folderUri)
    }

    fun canWrite(folderUri: Uri): Boolean {
        return AndroidWildcardFolderAccessChecker.canWriteFolder(context, folderUri)
    }
}
