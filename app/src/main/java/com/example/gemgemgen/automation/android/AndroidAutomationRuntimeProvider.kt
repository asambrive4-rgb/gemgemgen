package com.example.gemgemgen.automation.android

import android.content.Context
import com.example.gemgemgen.automation.usecase.ImeManager
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository

object AndroidAutomationRuntimeProvider {
    fun get(context: Context): RunAutomationUseCase {
        val appContext = context.applicationContext
        return ProcessAutomationHolder.getOrCreate {
            RunAutomationUseCase(
                imeManager = ImeManager(AndroidImeSettings(appContext)),
                lastRunSnapshotStore = LastRunSnapshotStore(
                    SharedPreferencesLastRunSnapshotRepository(appContext)
                ),
                clipboardGateway = AndroidClipboardGateway(appContext),
                wildcardSetRepository = AndroidWildcardSetRepository(appContext),
                promptGatewayProvider = ActivePromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext)
            )
        }
    }
}
