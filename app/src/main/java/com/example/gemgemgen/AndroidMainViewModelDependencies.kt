package com.example.gemgemgen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AndroidEnvironmentStatusReader(
    private val context: Context
) : EnvironmentStatusReader {
    override fun check(): EnvironmentStatus {
        val wildcardFolderUri = WildcardFolderStore.getFolderUri(context)

        return EnvironmentStatus(
            isGeminiInstalled = isPackageInstalled(AppDefaults.TARGET_PACKAGE_NAME),
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            hasWriteSecureSettingsPermission = hasWriteSecureSettingsPermission(),
            isWildcardDirectoryAccessible = wildcardFolderUri != null &&
                WildcardRepository.canReadFolder(context, wildcardFolderUri),
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

    private fun hasWriteSecureSettingsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class AndroidClipboardTextProvider(
    private val context: Context
) : ClipboardTextProvider {
    override fun readText(): String {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        return clipboardManager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
    }
}

class AndroidClipboardTextWriter(
    private val context: Context
) : ClipboardTextWriter {
    override fun writeText(text: String) {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText("wildcard", text))
    }
}

class AndroidWildcardFolderSaver(
    private val context: Context
) : WildcardFolderSaver {
    override fun save(folderUri: String): FolderSelectionResult {
        return try {
            WildcardFolderStore.saveFolderUri(context, Uri.parse(folderUri))
            FolderSelectionResult(message = "wildcard 폴더를 선택했습니다.")
        } catch (error: SecurityException) {
            FolderSelectionResult(error = "폴더 권한 저장 실패: ${error.message ?: "다시 선택해주세요."}")
        }
    }
}

class AndroidWildcardSetLoader(
    private val context: Context
) : WildcardSetLoader {
    override fun load(): List<WildcardSet> {
        return WildcardRepository(context).load()
    }
}

class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            error("Unknown ViewModel class: ${modelClass.name}")
        }

        val appContext = context.applicationContext
        val runLogger = RunLogger.android(appContext)
        val lastRunSnapshotStore = LastRunSnapshotStore.android(appContext)
        val clipboardTextWriter = AndroidClipboardTextWriter(appContext)
        return MainViewModel(
            environmentStatusReader = AndroidEnvironmentStatusReader(appContext),
            clipboardTextProvider = AndroidClipboardTextProvider(appContext),
            wildcardFolderSaver = AndroidWildcardFolderSaver(appContext),
            runLogger = runLogger,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = RunGeminiAutomationUseCase(
                imeManager = ImeManager.android(appContext),
                runLogger = runLogger,
                lastRunSnapshotStore = lastRunSnapshotStore,
                clipboardTextWriter = clipboardTextWriter,
                wildcardSetLoader = AndroidWildcardSetLoader(appContext),
                clock = System::currentTimeMillis,
                promptGatewayProvider = { GeminiAccessibilityService.activeService },
                launchGeminiApp = { launchGeminiApp(appContext) }
            )
        ) as T
    }
}

private fun launchGeminiApp(context: Context): Boolean {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(AppDefaults.TARGET_PACKAGE_NAME)
        ?: return false

    context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}
