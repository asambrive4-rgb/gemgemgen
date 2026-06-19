package com.example.gemgemgen.ui.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemgemgen.automation.android.ActivePromptAutomationGatewayProvider
import com.example.gemgemgen.automation.android.AndroidTargetAppLauncher
import com.example.gemgemgen.automation.android.AndroidImeSettings
import com.example.gemgemgen.automation.android.SharedPreferencesLastRunSnapshotStorage
import com.example.gemgemgen.automation.android.SharedPreferencesRunLogStorage
import com.example.gemgemgen.automation.usecase.ImeManager
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.automation.usecase.RunLogger
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.ui.MainViewModel
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository
import com.example.gemgemgen.wildcard.usecase.SaveWildcardFolderUseCase

class MainViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            error("Unknown ViewModel class: ${modelClass.name}")
        }

        val appContext = context.applicationContext
        val runLogger = RunLogger(SharedPreferencesRunLogStorage(appContext))
        val lastRunSnapshotStore = LastRunSnapshotStore(
            SharedPreferencesLastRunSnapshotStorage(appContext)
        )
        val clipboardGateway = AndroidClipboardGateway(appContext)
        return MainViewModel(
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(
                AndroidEnvironmentGateway(appContext)
            ),
            clipboardGateway = clipboardGateway,
            saveWildcardFolder = SaveWildcardFolderUseCase(
                AndroidWildcardFolderRepository(appContext)
            ),
            runLogger = runLogger,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = RunAutomationUseCase(
                imeManager = ImeManager(AndroidImeSettings(appContext)),
                runLogger = runLogger,
                lastRunSnapshotStore = lastRunSnapshotStore,
                clipboardGateway = clipboardGateway,
                wildcardSetRepository = AndroidWildcardSetRepository(appContext),
                clock = System::currentTimeMillis,
                promptGatewayProvider = ActivePromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext)
            )
        ) as T
    }
}
