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
import com.example.gemgemgen.analysis.usecase.GenerateAnalysisTxtUseCase
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.analysis.usecase.ManageGrokAuthUseCase
import com.example.gemgemgen.analysis.usecase.ResolveAnalysisTargetUseCase
import com.example.gemgemgen.analysis.usecase.SaveAnalysisWildcardFileUseCase
import com.example.gemgemgen.automation.android.AndroidAutomationRuntimeProvider
import com.example.gemgemgen.automation.android.ActiveVariationPromptAutomationGatewayProvider
import com.example.gemgemgen.automation.android.AndroidTargetAppLauncher
import com.example.gemgemgen.automation.android.AndroidGeminiAppCloser
import com.example.gemgemgen.automation.android.AndroidMemoryCleanupGateway
import com.example.gemgemgen.automation.android.AndroidSelfAppCloser
import com.example.gemgemgen.automation.android.SharedPreferencesLastRunSnapshotRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptHistoryRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptInstructionRepository
import com.example.gemgemgen.automation.android.SharedPreferencesPromptSnippetRepository
import com.example.gemgemgen.automation.android.SharedPreferencesVariationPromptRepository
import com.example.gemgemgen.automation.usecase.AppMaintenanceUseCase
import com.example.gemgemgen.automation.usecase.CheckAutomationStartUseCase
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import com.example.gemgemgen.automation.usecase.RecordAutomationHistoryUseCase
import com.example.gemgemgen.automation.usecase.CoordinateAutomationExecutionUseCase
import com.example.gemgemgen.automation.usecase.RunVariationPromptUseCase
import com.example.gemgemgen.automation.ui.AutomationViewModel
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.core.android.AndroidSoundAlertGateway
import com.example.gemgemgen.core.PromptWorkspace
import com.example.gemgemgen.environment.android.AndroidEnvironmentGateway
import com.example.gemgemgen.wildcard.android.AndroidWildcardFileRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardFolderRepository
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository
import com.example.gemgemgen.wildcard.usecase.ClassifyWildcardLinesUseCase
import com.example.gemgemgen.wildcard.usecase.ManageWildcardFilesUseCase
import com.example.gemgemgen.wildcard.usecase.SaveWildcardClassifyResultUseCase
import com.example.gemgemgen.wildcard.ui.WildcardViewModel
import com.example.gemgemgen.remote.android.AndroidRemoteAutomationGateway
import com.example.gemgemgen.remote.usecase.ManageRemoteAutomationUseCase

class AndroidAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val lastRunSnapshotStore by lazy {
        LastRunSnapshotStore(
            SharedPreferencesLastRunSnapshotRepository(appContext)
        )
    }
    private val promptHistoryStore by lazy {
        PromptHistoryStore(
            SharedPreferencesPromptHistoryRepository(appContext)
        )
    }
    private val clipboardGateway by lazy { AndroidClipboardGateway(appContext) }
    private val recordAutomationStart by lazy {
        RecordAutomationHistoryUseCase(
            lastRunSnapshotStore = lastRunSnapshotStore,
            promptHistoryStore = promptHistoryStore
        )
    }
    private val geminiApiKeyRepository by lazy { AndroidEncryptedGeminiApiKeyRepository(appContext) }
    private val analysisAiGateway by lazy {
        RoutingAnalysisAiGateway(
            gemini = AndroidGeminiAnalysisGateway(),
            grok = AndroidGrokAnalysisGateway()
        )
    }
    private val grokAuthManager by lazy {
        ManageGrokAuthUseCase(
            gateway = AndroidGrokOAuthGateway(),
            repository = AndroidEncryptedGrokAuthRepository(appContext),
            billingGateway = AndroidGrokBillingGateway()
        )
    }
    private val analysisCredentialResolver by lazy {
        ResolveAnalysisCredentialUseCase(
            apiKeyRepository = geminiApiKeyRepository,
            grokAuth = grokAuthManager
        )
    }
    private val environmentGateway by lazy { AndroidEnvironmentGateway(appContext) }
    private val wildcardFileRepository by lazy { AndroidWildcardFileRepository(appContext) }
    private val wildcardSetRepository by lazy { AndroidWildcardSetRepository(wildcardFileRepository) }
    private val wildcardFolderRepository by lazy { AndroidWildcardFolderRepository(appContext) }

    val themePaletteStore = com.example.gemgemgen.ui.theme.ThemePaletteStore(appContext)
    val promptWorkspace = PromptWorkspace()
    val promptInstructionRepository by lazy { SharedPreferencesPromptInstructionRepository(appContext) }
    val variationPromptRepository by lazy { SharedPreferencesVariationPromptRepository(appContext) }
    val promptSnippetRepository by lazy { SharedPreferencesPromptSnippetRepository(appContext) }
    val automationSettingsStore by lazy { com.example.gemgemgen.automation.android.AutomationSettingsStore(appContext) }

    val automationViewModelFactory: ViewModelProvider.Factory = factory<AutomationViewModel> {
        val automation = AndroidAutomationRuntimeProvider.get(appContext)
        val checkAutomationStart = CheckAutomationStartUseCase(environmentGateway)
        val manageRemoteAutomation = ManageRemoteAutomationUseCase(
            gateway = AndroidRemoteAutomationGateway(appContext),
            automationHistoryRecorder = recordAutomationStart,
            wildcardSetRepository = wildcardSetRepository
        )
        val executeAutomation = CoordinateAutomationExecutionUseCase(
            checkAutomationStart = checkAutomationStart,
            automationHistoryRecorder = recordAutomationStart,
            automation = automation,
            manageRemoteAutomation = manageRemoteAutomation
        )
        AutomationViewModel(
            checkEnvironmentStatus = environmentGateway,
            clipboardGateway = clipboardGateway,
            lastRunSnapshotStore = lastRunSnapshotStore,
            automation = automation,
            appMaintenance = AppMaintenanceUseCase(
                geminiRestartCloser = AndroidGeminiAppCloser(appContext),
                selfAppCloser = AndroidSelfAppCloser(appContext),
                memoryCleanupGateway = AndroidMemoryCleanupGateway(appContext),
                manageRemoteAutomation = manageRemoteAutomation
            ),
            checkAutomationStart = checkAutomationStart,
            executeAutomation = executeAutomation,
            wildcardFileRepository = wildcardFileRepository,
            manageRemoteAutomation = manageRemoteAutomation,
            soundAlertGateway = AndroidSoundAlertGateway(appContext),
            promptHistoryStore = promptHistoryStore,
            themePaletteStore = themePaletteStore,
            promptWorkspace = promptWorkspace,
            promptInstructionRepository = promptInstructionRepository,
            variationPromptRepository = variationPromptRepository,
            promptSnippetRepository = promptSnippetRepository,
            runVariationPrompt = RunVariationPromptUseCase(
                gatewayProvider = ActiveVariationPromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext)
            ),
            automationSettingsStore = automationSettingsStore
        )
    }

    val wildcardViewModelFactory: ViewModelProvider.Factory = factory<WildcardViewModel> {
        val analysisKeyManager = ManageGeminiApiKeysUseCase(geminiApiKeyRepository)
        WildcardViewModel(
            manageWildcardFiles = ManageWildcardFilesUseCase(wildcardFileRepository),
            clipboardGateway = clipboardGateway,
            classifyWildcardLines = ClassifyWildcardLinesUseCase(
                aiGateway = analysisAiGateway,
                credentialResolver = analysisCredentialResolver
            ),
            saveWildcardClassifyResult = SaveWildcardClassifyResultUseCase(
                repository = wildcardFileRepository
            ),
            analysisKeyManager = analysisKeyManager,
            saveWildcardFolder = wildcardFolderRepository
        )
    }

    val analysisViewModelFactory: ViewModelProvider.Factory = factory<AnalysisViewModel> {
        AnalysisViewModel(
            resolveTarget = ResolveAnalysisTargetUseCase(
                aiGateway = analysisAiGateway,
                credentialResolver = analysisCredentialResolver
            ),
            generateTxtUseCase = GenerateAnalysisTxtUseCase(
                aiGateway = analysisAiGateway,
                credentialResolver = analysisCredentialResolver
            ),
            keyManager = ManageGeminiApiKeysUseCase(geminiApiKeyRepository),
            grokAuth = grokAuthManager,
            clipboardGateway = clipboardGateway,
            saveWildcardFile = SaveAnalysisWildcardFileUseCase(
                repository = wildcardFileRepository,
                clipboardGateway = clipboardGateway
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
