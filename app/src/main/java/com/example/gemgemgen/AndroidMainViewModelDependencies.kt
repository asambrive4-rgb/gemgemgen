package com.example.gemgemgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AndroidEnvironmentStatusProvider(
    private val context: Context
) : EnvironmentStatusProvider {
    override fun check(): EnvironmentStatus {
        return EnvironmentStatusChecker.check(context)
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
        return MainViewModel(
            environmentStatusProvider = AndroidEnvironmentStatusProvider(appContext),
            clipboardTextProvider = AndroidClipboardTextProvider(appContext),
            wildcardFolderSaver = AndroidWildcardFolderSaver(appContext),
            runLogger = runLogger,
            lastRunSnapshotStore = LastRunSnapshotStore.android(appContext),
            automation = GeminiMvpAutomation(
                context = appContext,
                runLogger = runLogger
            )
        ) as T
    }
}
