package com.example.gemgemgen.ui.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemgemgen.analysis.android.AndroidEncryptedGeminiApiKeyRepository
import com.example.gemgemgen.analysis.android.AndroidGeminiAnalysisGateway
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.analysis.usecase.AnalyzePromptForCategoryUseCase
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.automation.android.AndroidGeminiAppCloser
import com.example.gemgemgen.automation.android.ActivePromptAutomationGatewayProvider
import com.example.gemgemgen.automation.android.AndroidImeSettings
import com.example.gemgemgen.automation.android.AndroidOverlayPermissionGateway
import com.example.gemgemgen.automation.android.AndroidTargetAppLauncher
import com.example.gemgemgen.automation.android.SharedPreferencesLastRunSnapshotRepository
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppUseCase
import com.example.gemgemgen.automation.usecase.ImeManager
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.RunAutomationUseCase
import com.example.gemgemgen.automation.ui.MainViewModel
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.android.AndroidWildcardFileRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardFolderUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardUseCase
import com.example.gemgemgen.wildcard.ui.WildcardManagerViewModel

class AndroidAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val lastRunSnapshotStore = LastRunSnapshotStore(
        SharedPreferencesLastRunSnapshotRepository(appContext)
    )
    private val clipboardGateway = AndroidClipboardGateway(appContext)
    private val analysisAiGateway = AndroidGeminiAnalysisGateway()
    private val geminiApiKeyRepository = AndroidEncryptedGeminiApiKeyRepository(appContext)

    val mainViewModelFactory: ViewModelProvider.Factory = factory<MainViewModel> {
        MainViewModel(
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(
                AndroidEnvironmentGateway(appContext)
            ),
            clipboardGateway = clipboardGateway,
            saveWildcardFolder = SaveWildcardFolderUseCase(
                AndroidWildcardFolderRepository(appContext)
            ),
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = RunAutomationUseCase(
                imeManager = ImeManager(AndroidImeSettings(appContext)),
                lastRunSnapshotStore = lastRunSnapshotStore,
                clipboardGateway = clipboardGateway,
                wildcardSetRepository = AndroidWildcardSetRepository(appContext),
                clock = System::currentTimeMillis,
                promptGatewayProvider = ActivePromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext)
            ),
            closeGeminiApp = CloseGeminiAppUseCase(
                AndroidGeminiAppCloser(appContext)
            ),
            terminateGeminiApp = CloseGeminiAppUseCase(
                AndroidGeminiAppCloser(appContext, relaunchAfterClose = false)
            ),
            checkAutomationStart = CheckAutomationStartUseCase(
                AndroidOverlayPermissionGateway(appContext)
            )
        )
    }

    val wildcardViewModelFactory: ViewModelProvider.Factory = factory<WildcardManagerViewModel> {
        WildcardManagerViewModel(
            manageWildcardFiles = ManageWildcardFilesUseCase(
                AndroidWildcardFileRepository(appContext)
            ),
            wildcardClipboard = WildcardClipboardUseCase(clipboardGateway)
        )
    }

    val analysisViewModelFactory: ViewModelProvider.Factory = factory<AnalysisViewModel> {
        AnalysisViewModel(
            analyzePrompt = AnalyzePromptForCategoryUseCase(
                aiGateway = analysisAiGateway,
                apiKeyRepository = geminiApiKeyRepository
            ),
            generateTxtUseCase = GenerateAnalysisTxtUseCase(
                aiGateway = analysisAiGateway,
                apiKeyRepository = geminiApiKeyRepository
            ),
            keyManager = ManageGeminiApiKeysUseCase(geminiApiKeyRepository),
            copyResults = CopyAnalysisResultsUseCase(clipboardGateway),
            saveWildcardFile = SaveAnalysisWildcardFileUseCase(
                AndroidWildcardFileRepository(appContext)
            )
        )
    }

    private inline fun <reified T : ViewModel> factory(
        crossinline create: () -> T
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                if (!modelClass.isAssignableFrom(T::class.java)) {
                    error("Unknown ViewModel class: ${modelClass.name}")
                }
                return create() as VM
            }
        }
    }
}
