// 역할: 앱 전반에서 필요한 저장소, 유스케이스, 게이트웨이 인스턴스를 주입하는 의존성 컨테이너입니다.
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
import com.example.gemgemgen.automation.android.AndroidAutomationRuntimeProvider
import com.example.gemgemgen.automation.android.AndroidGeminiAppCloser
import com.example.gemgemgen.automation.android.AndroidMemoryCleanupGateway
import com.example.gemgemgen.automation.android.AndroidSelfAppCloser
import com.example.gemgemgen.automation.android.SharedPreferencesGeminiAccountRepository
import com.example.gemgemgen.automation.android.SharedPreferencesLastRunSnapshotRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptHistoryRepository
import com.example.gemgemgen.automation.usecase.ManageGeminiAccountsUseCase
import com.example.gemgemgen.automation.usecase.AppMaintenanceUseCase
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.RecordAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.ExecuteAutomationUseCase
import com.example.gemgemgen.automation.ui.MainViewModel
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.core.android.AndroidSoundAlertGateway
import com.example.gemgemgen.core.PromptWorkspace
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.environment.usecase.CheckEnvironmentStatusUseCase
import com.example.gemgemgen.wildcard.android.AndroidWildcardFileRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository
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
    private val promptHistoryStore = PromptHistoryStore(
        SharedPreferencesPromptHistoryRepository(appContext)
    )
    private val clipboardGateway = AndroidClipboardGateway(appContext)
    private val recordAutomationStart = RecordAutomationStartUseCase(
        lastRunSnapshotStore = lastRunSnapshotStore,
        clipboardGateway = clipboardGateway,
        promptHistoryStore = promptHistoryStore
    )
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

    val themePaletteStore = com.example.gemgemgen.ui.theme.ThemePaletteStore(appContext)
    val promptWorkspace = PromptWorkspace()
    val geminiAccountRepository = SharedPreferencesGeminiAccountRepository(appContext)
    val manageGeminiAccounts = ManageGeminiAccountsUseCase(geminiAccountRepository)

    val mainViewModelFactory: ViewModelProvider.Factory = factory<MainViewModel> {
        val automation = AndroidAutomationRuntimeProvider.get(appContext)
        val environmentGateway = AndroidEnvironmentGateway(appContext)
        val checkAutomationStart = CheckAutomationStartUseCase(environmentGateway)
        val manageRemoteAutomation = ManageRemoteAutomationUseCase(
            gateway = AndroidRemoteAutomationGateway(appContext),
            automationStartRecorder = recordAutomationStart,
            wildcardSetRepository = AndroidWildcardSetRepository(appContext)
        )
        val executeAutomation = ExecuteAutomationUseCase(
            checkAutomationStart = checkAutomationStart,
            automationStartRecorder = recordAutomationStart,
            automation = automation,
            manageRemoteAutomation = manageRemoteAutomation,
            promptHistoryStore = promptHistoryStore
        )
        MainViewModel(
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(environmentGateway),
            clipboardGateway = clipboardGateway,
            saveWildcardFolder = SaveWildcardFolderUseCase(
                AndroidWildcardFolderRepository(appContext)
            ),
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automation,
            appMaintenance = AppMaintenanceUseCase(
                geminiRestartCloser = AndroidGeminiAppCloser(appContext),
                geminiTerminateCloser = AndroidGeminiAppCloser(appContext, relaunchAfterClose = false),
                selfAppCloser = AndroidSelfAppCloser(appContext),
                memoryCleanupGateway = AndroidMemoryCleanupGateway(appContext)
            ),
            checkAutomationStart = checkAutomationStart,
            executeAutomation = executeAutomation,
            wildcardFileRepository = AndroidWildcardFileRepository(appContext),
            manageRemoteAutomation = manageRemoteAutomation,
            soundAlertGateway = AndroidSoundAlertGateway(appContext),
            promptHistoryStore = promptHistoryStore,
            themePaletteStore = themePaletteStore,
            promptWorkspace = promptWorkspace,
            manageGeminiAccounts = manageGeminiAccounts
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
            ),
            promptWorkspace = promptWorkspace
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
