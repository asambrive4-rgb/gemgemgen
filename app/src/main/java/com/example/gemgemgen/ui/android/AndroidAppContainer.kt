package com.example.gemgemgen.ui.android

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemgemgen.analysis.android.AndroidEncryptedGeminiApiKeyRepository
import com.example.gemgemgen.analysis.android.AndroidEncryptedGrokAuthRepository
import com.example.gemgemgen.analysis.android.AndroidGeminiAnalysisGateway
import com.example.gemgemgen.analysis.android.AndroidGrokAnalysisGateway
import com.example.gemgemgen.analysis.android.AndroidGrokBillingGateway
import com.example.gemgemgen.analysis.android.AndroidGrokOAuthGateway
import com.example.gemgemgen.analysis.android.RoutingAnalysisAiGateway
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.analysis.usecase.AnalysisCredentialResolver
import com.example.gemgemgen.analysis.usecase.AnalyzePromptForCategoryUseCase
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisTargetUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.automation.android.AndroidGeminiAppCloser
import com.example.gemgemgen.automation.android.AndroidMemoryCleanupGateway
import com.example.gemgemgen.automation.android.AndroidSelfAppCloser
import com.example.gemgemgen.automation.android.AndroidOverlayPermissionGateway
import com.example.gemgemgen.automation.android.AndroidAutomationRuntimeProvider
import com.example.gemgemgen.automation.android.SharedPreferencesLastRunSnapshotRepository
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.CleanDeviceMemoryUseCase
import com.example.gemgemgen.automation.usecase.CloseGeminiAppUseCase
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.ui.MainViewModel
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.android.AndroidWildcardFileRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderRepository
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardFolderUseCase
import com.example.gemgemgen.wildcard.usecase.WildcardClipboardUseCase
import com.example.gemgemgen.wildcard.ui.WildcardManagerViewModel
import com.example.gemgemgen.remote.android.AndroidRemoteAutomationGateway
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

class AndroidAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val lastRunSnapshotStore = LastRunSnapshotStore(
        SharedPreferencesLastRunSnapshotRepository(appContext)
    )
    private val clipboardGateway = AndroidClipboardGateway(appContext)
    private val geminiApiKeyRepository = AndroidEncryptedGeminiApiKeyRepository(appContext)
    private val analysisAiGateway = RoutingAnalysisAiGateway(
        gemini = AndroidGeminiAnalysisGateway(),
        grok = AndroidGrokAnalysisGateway()
    )
    private val grokAuthManager = ManageGrokAuthUseCase(
        gateway = AndroidGrokOAuthGateway(),
        repository = AndroidEncryptedGrokAuthRepository(appContext),
        billingGateway = AndroidGrokBillingGateway()
    )
    private val analysisCredentialResolver = AnalysisCredentialResolver(
        apiKeyRepository = geminiApiKeyRepository,
        grokAuth = grokAuthManager
    )

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
            automation = AndroidAutomationRuntimeProvider.get(appContext),
            closeGeminiApp = CloseGeminiAppUseCase(
                AndroidGeminiAppCloser(appContext)
            ),
            terminateGeminiApp = CloseGeminiAppUseCase(
                AndroidGeminiAppCloser(appContext, relaunchAfterClose = false)
            ),
            terminateSelfApp = CloseGeminiAppUseCase(
                AndroidSelfAppCloser(appContext)
            ),
            cleanDeviceMemoryUseCase = CleanDeviceMemoryUseCase(
                AndroidMemoryCleanupGateway(appContext)
            ),
            checkAutomationStart = CheckAutomationStartUseCase(
                AndroidOverlayPermissionGateway(appContext)
            ),
            wildcardFileRepository = AndroidWildcardFileRepository(appContext),
            manageRemoteAutomation = ManageRemoteAutomationUseCase(
                AndroidRemoteAutomationGateway(appContext)
            )
        )
    }

    val wildcardViewModelFactory: ViewModelProvider.Factory = factory<WildcardManagerViewModel> {
        val wildcardFileRepository = AndroidWildcardFileRepository(appContext)
        val analysisKeyManager = ManageGeminiApiKeysUseCase(geminiApiKeyRepository)
        WildcardManagerViewModel(
            manageWildcardFiles = ManageWildcardFilesUseCase(wildcardFileRepository),
            wildcardClipboard = WildcardClipboardUseCase(clipboardGateway),
            classifyWildcardLines = ClassifyWildcardLinesUseCase(
                aiGateway = analysisAiGateway,
                credentialResolver = analysisCredentialResolver
            ),
            saveWildcardClassifyResult = SaveWildcardClassifyResultUseCase(
                repository = wildcardFileRepository
            ),
            analysisKeyManager = analysisKeyManager
        )
    }

    val analysisViewModelFactory: ViewModelProvider.Factory = factory<AnalysisViewModel> {
        val analyzePrompt = AnalyzePromptForCategoryUseCase(
            aiGateway = analysisAiGateway,
            credentialResolver = analysisCredentialResolver
        )
        val copyResults = CopyAnalysisResultsUseCase(clipboardGateway)
        AnalysisViewModel(
            resolveTarget = ResolveAnalysisTargetUseCase(analyzePrompt),
            generateTxtUseCase = GenerateAnalysisTxtUseCase(
                aiGateway = analysisAiGateway,
                credentialResolver = analysisCredentialResolver
            ),
            keyManager = ManageGeminiApiKeysUseCase(geminiApiKeyRepository),
            grokAuth = grokAuthManager,
            copyResults = copyResults,
            saveWildcardFile = SaveAnalysisWildcardFileUseCase(
                repository = AndroidWildcardFileRepository(appContext),
                copyResults = copyResults
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
