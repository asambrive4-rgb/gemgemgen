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
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisCredentialUseCase
import com.example.gemgemgen.analysis.usecase.AnalyzePromptForCategoryUseCase
import com.example.gemgemgen.analysis.usecase.CopyAnalysisResultsUseCase
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisTargetUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.automation.android.AndroidAutomationRuntimeProvider
import com.example.gemgemgen.automation.android.ActiveVariationPromptAutomationGatewayProvider
import com.example.gemgemgen.automation.android.AndroidTargetAppLauncher
import com.example.gemgemgen.automation.android.AndroidGeminiAccountSwitcherGateway
import com.example.gemgemgen.automation.android.AndroidGeminiAppCloser
import com.example.gemgemgen.automation.android.AndroidMemoryCleanupGateway
import com.example.gemgemgen.automation.android.AndroidSelfAppCloser
import com.example.gemgemgen.automation.android.SharedPreferencesLastRunSnapshotRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptHistoryRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptInstructionRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptSnippetRepository
import com.example.gemgemgen.automation.android.SharedPreferencesVariationPromptRepository
import com.example.gemgemgen.automation.usecase.OpenGeminiAccountPickerUseCase
import com.example.gemgemgen.automation.usecase.AppMaintenanceUseCase
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.RecordAutomationHistoryUseCase
import com.example.gemgemgen.automation.usecase.CoordinateAutomationExecutionUseCase
import com.example.gemgemgen.automation.usecase.ResolveVariationPromptUseCase
import com.example.gemgemgen.automation.usecase.RunVariationPromptUseCase
import com.example.gemgemgen.automation.ui.AutomationViewModel
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
import com.example.gemgemgen.wildcard.ui.WildcardViewModel
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
    private val recordAutomationStart = RecordAutomationHistoryUseCase(
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
    private val analysisCredentialResolver = ResolveAnalysisCredentialUseCase(
        apiKeyRepository = geminiApiKeyRepository,
        grokAuth = grokAuthManager
    )

    val themePaletteStore = com.example.gemgemgen.ui.theme.ThemePaletteStore(appContext)
    val promptWorkspace = PromptWorkspace()
    val promptInstructionRepository = SharedPreferencesPromptInstructionRepository(appContext)
    val variationPromptRepository = SharedPreferencesVariationPromptRepository(appContext)
    val promptSnippetRepository = SharedPreferencesPromptSnippetRepository(appContext)

    val automationViewModelFactory: ViewModelProvider.Factory = factory<AutomationViewModel> {
        val automation = AndroidAutomationRuntimeProvider.get(appContext)
        val environmentGateway = AndroidEnvironmentGateway(appContext)
        val checkAutomationStart = CheckAutomationStartUseCase(environmentGateway)
        val manageRemoteAutomation = ManageRemoteAutomationUseCase(
            gateway = AndroidRemoteAutomationGateway(appContext),
            automationHistoryRecorder = recordAutomationStart,
            wildcardSetRepository = AndroidWildcardSetRepository(appContext)
        )
        val switcherGateway = AndroidGeminiAccountSwitcherGateway(appContext)
        val openGeminiAccountPicker = OpenGeminiAccountPickerUseCase(
            manageRemoteAutomation = manageRemoteAutomation,
            switcherGateway = switcherGateway
        )
        val executeAutomation = CoordinateAutomationExecutionUseCase(
            checkAutomationStart = checkAutomationStart,
            automationHistoryRecorder = recordAutomationStart,
            automation = automation,
            manageRemoteAutomation = manageRemoteAutomation,
            promptHistoryStore = promptHistoryStore
        )
        AutomationViewModel(
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(environmentGateway),
            clipboardGateway = clipboardGateway,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automation,
            appMaintenance = AppMaintenanceUseCase(
                geminiRestartCloser = AndroidGeminiAppCloser(appContext),
                geminiTerminateCloser = AndroidGeminiAppCloser(appContext, relaunchAfterClose = false),
                selfAppCloser = AndroidSelfAppCloser(appContext),
                memoryCleanupGateway = AndroidMemoryCleanupGateway(appContext),
                manageRemoteAutomation = manageRemoteAutomation
            ),
            checkAutomationStart = checkAutomationStart,
            executeAutomation = executeAutomation,
            wildcardFileRepository = AndroidWildcardFileRepository(appContext),
            manageRemoteAutomation = manageRemoteAutomation,
            soundAlertGateway = AndroidSoundAlertGateway(appContext),
            promptHistoryStore = promptHistoryStore,
            themePaletteStore = themePaletteStore,
            promptWorkspace = promptWorkspace,
            openGeminiAccountPicker = openGeminiAccountPicker,
            promptInstructionRepository = promptInstructionRepository,
            variationPromptRepository = variationPromptRepository,
            promptSnippetRepository = promptSnippetRepository,
            runVariationPrompt = RunVariationPromptUseCase(
                gatewayProvider = ActiveVariationPromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext)
            ),
            resolveVariationPrompt = ResolveVariationPromptUseCase()
        )
    }

    val wildcardViewModelFactory: ViewModelProvider.Factory = factory<WildcardViewModel> {
        val wildcardFileRepository = AndroidWildcardFileRepository(appContext)
        val wildcardFolderRepository = AndroidWildcardFolderRepository(appContext)
        val environmentGateway = AndroidEnvironmentGateway(appContext)
        val analysisKeyManager = ManageGeminiApiKeysUseCase(geminiApiKeyRepository)
        WildcardViewModel(
            manageWildcardFiles = ManageWildcardFilesUseCase(wildcardFileRepository),
            wildcardClipboard = WildcardClipboardUseCase(clipboardGateway),
            classifyWildcardLines = ClassifyWildcardLinesUseCase(
                aiGateway = analysisAiGateway,
                credentialResolver = analysisCredentialResolver
            ),
            saveWildcardClassifyResult = SaveWildcardClassifyResultUseCase(
                repository = wildcardFileRepository
            ),
            analysisKeyManager = analysisKeyManager,
            saveWildcardFolder = SaveWildcardFolderUseCase(wildcardFolderRepository),
            checkEnvironmentStatus = CheckEnvironmentStatusUseCase(environmentGateway)
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
