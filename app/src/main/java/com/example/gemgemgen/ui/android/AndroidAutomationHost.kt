// 역할: 접근성 서비스 연결, 액션 바인딩 및 라이프사이클 이벤트 제어로 자동화 화면을 호스팅합니다.
package com.example.gemgemgen.ui.android

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.automation.android.FloatingAutomationBarController
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.ui.MainViewModel
import com.example.gemgemgen.core.android.AndroidExternalBrowserLauncher
import com.example.gemgemgen.ui.AnalysisAppActions
import com.example.gemgemgen.ui.AutomationApp
import com.example.gemgemgen.ui.AutomationAppActions
import com.example.gemgemgen.ui.MainTab
import com.example.gemgemgen.ui.WildcardAppActions
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import com.example.gemgemgen.wildcard.domain.WildcardFolderAction
import com.example.gemgemgen.wildcard.ui.WildcardManagerViewModel
import com.example.gemgemgen.remote.domain.AutomationMode

@Composable
fun AndroidAutomationHost(container: AndroidAppContainer) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val browserLauncher = remember(context) { AndroidExternalBrowserLauncher(context) }
    val platformNavigator = remember(context) { AndroidHostPlatformNavigator(context) }
    val clearInputFocus = remember(focusManager) {
        { focusManager.clearFocus(force = true) }
    }
    val mainViewModel: MainViewModel = viewModel(factory = container.mainViewModelFactory)
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val automationBarUiState by mainViewModel.automationBarUiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.AUTOMATION) }
    val wildcardStoreOwner = remember { TabViewModelStoreOwner() }
    val analysisStoreOwner = remember { TabViewModelStoreOwner() }
    val analysisViewModel: AnalysisViewModel = viewModel(
        viewModelStoreOwner = analysisStoreOwner,
        factory = container.analysisViewModelFactory
    )
    val analysisUiState by analysisViewModel.uiState.collectAsStateWithLifecycle()
    val analysisPromptState = analysisViewModel.sourcePromptTextFieldState
    val wildcardViewModel: WildcardManagerViewModel = viewModel(
        viewModelStoreOwner = wildcardStoreOwner,
        factory = container.wildcardViewModelFactory
    )
    val wildcardUiState by wildcardViewModel.uiState.collectAsStateWithLifecycle()
    val floatingBarController = remember(activity) {
        activity?.let(::FloatingAutomationBarController)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    DisposableEffect(Unit) {
        onDispose {
            wildcardStoreOwner.clear()
            analysisStoreOwner.clear()
        }
    }

    val wildcardFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            mainViewModel.saveWildcardFolder(uri.toString())
            wildcardViewModel.onFolderChanged()
        }
    }

    fun launchWildcardFolderPicker() {
        val initialUri = mainViewModel.getInitialWildcardFolderUri()?.let { android.net.Uri.parse(it) }
        wildcardFolderLauncher.launch(initialUri)
    }

    fun openWildcardStorageSettings() {
        platformNavigator.openAllFilesAccessSettings()
    }

    fun selectSafWildcardFolder() {
        if (!wildcardViewModel.requestFolderSelection()) {
            selectedTab = MainTab.WILDCARD
            return
        }
        launchWildcardFolderPicker()
    }

    fun selectWildcardFolder() {
        when (mainViewModel.decideWildcardFolderAction()) {
            WildcardFolderAction.OpenDirectFolder -> {
                if (!wildcardViewModel.requestFolderSelection()) return
                wildcardViewModel.onFolderChanged()
                selectedTab = MainTab.WILDCARD
                mainViewModel.refreshStatus()
            }
            WildcardFolderAction.OpenStorageSettings -> openWildcardStorageSettings()
            WildcardFolderAction.LaunchSafPicker -> selectSafWildcardFolder()
        }
    }

    fun trimInactiveTabs(exceptTab: MainTab? = null) {
        if (exceptTab != MainTab.ANALYSIS) {
            // 결과·설정·타겟 구간 및 진행 중 AI 작업은 유지하고 일시적 다이얼로그 상태만 정리.
            analysisViewModel.trimForInactiveTab()
        }
        if (exceptTab != MainTab.WILDCARD) {
            // 미저장 여부와 무관하게 ViewModel과 텍스트 본문은 보존하고, 무거운 Undo 버퍼만 정리하여 재진입 시 0ms 즉시 표시
            wildcardViewModel.trimForInactiveTab()
        }
    }

    fun selectMainTab(tab: MainTab) {
        if (tab != MainTab.AUTOMATION) {
            mainViewModel.cancelParagraphSelection()
        }
        if (selectedTab != tab) {
            trimInactiveTabs(exceptTab = tab)
        }
        // 와일드카드 탭에서 파일 추가/이름변경 후 돌아와도 추천 목록이 갱신되게 한다.
        if (tab == MainTab.AUTOMATION) {
            mainViewModel.refreshWildcardTokenCandidates()
        }
        selectedTab = tab
    }

    fun bringMainActivityToFront() {
        platformNavigator.bringMainActivityToFront()
    }

    fun runAutomation() {
        trimInactiveTabs()
        when (mainViewModel.runAutomation()) {
            AutomationStartDecision.Started -> {
                if (!mainViewModel.uiState.value.isRunning) return
                floatingBarController?.showOrUpdate(
                    uiStateFlow = mainViewModel.automationBarUiState,
                    onCancelAutomation = mainViewModel::cancelAutomation,
                    onRepeatCountChange = mainViewModel::onRepeatCountChange,
                    onAutomationFinished = {
                        floatingBarController?.hide()
                        bringMainActivityToFront()
                    }
                )
            }
            AutomationStartDecision.RemoteStarted,
            AutomationStartDecision.Rejected -> Unit
            AutomationStartDecision.PermissionRequired -> platformNavigator.openOverlayPermissionSettings()
        }
    }

    fun selectAutomationMode(mode: AutomationMode) {
        if (mode == AutomationMode.RECEIVER && !mainUiState.environmentStatus.hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        mainViewModel.onAutomationModeSelected(mode)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.refreshStatus()
                // 멀티윈도우에서는 RESUME만으로 포커스가 안 풀릴 수 있어 force clear.
                clearInputFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 스플릿/멀티윈도우: 다른 창을 탭해 우리 창이 포커스를 잃으면 커서·키보드 즉시 해제. (초기 시작 시점 중복 트리거 방지)
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .distinctUntilChanged()
            .drop(1)
            .collect { focused ->
                if (!focused) {
                    clearInputFocus()
                }
            }
    }

    // 첫 화면 렌더링이 완전히 안착된 후 300ms 시차를 두어 플로팅 오버레이를 준비(Staggered Warm-up)하여 콜드 스타트 지연 제거
    LaunchedEffect(floatingBarController) {
        delay(300L)
        floatingBarController?.warmUp()
    }

    DisposableEffect(floatingBarController) {
        onDispose { floatingBarController?.hide() }
    }

    LaunchedEffect(
        wildcardViewModel,
        mainUiState.environmentStatus.canEditWildcardFiles
    ) {
        wildcardViewModel.onFolderAccessChanged(
            mainUiState.environmentStatus.canEditWildcardFiles
        )
    }

    LaunchedEffect(selectedTab, wildcardViewModel) {
        if (selectedTab == MainTab.WILDCARD) {
            wildcardViewModel.onTabEntered()
        }
    }

    GemgemgenTheme(
        palette = mainUiState.selectedThemePalette,
        themeMode = mainUiState.selectedThemeMode
    ) {
        AutomationApp(
            selectedTab = selectedTab,
            mainUiState = mainUiState,
            automationBarUiState = automationBarUiState,
            promptTemplateState = mainViewModel.promptTemplateTextFieldState,
            analysisUiState = analysisUiState,
            analysisPromptState = analysisPromptState,
            wildcardUiState = wildcardUiState,
            automationActions = remember(mainViewModel, platformNavigator, clearInputFocus) {
                createAutomationActions(
                    mainViewModel = mainViewModel,
                    platformNavigator = platformNavigator,
                    clearInputFocus = clearInputFocus,
                    selectMainTab = ::selectMainTab,
                    selectWildcardFolder = ::selectWildcardFolder,
                    selectSafWildcardFolder = ::selectSafWildcardFolder,
                    openWildcardStorageSettings = ::openWildcardStorageSettings,
                    runAutomation = ::runAutomation,
                    selectAutomationMode = ::selectAutomationMode
                )
            },
            analysisActions = remember(analysisViewModel, platformNavigator, browserLauncher, clearInputFocus) {
                createAnalysisActions(
                    analysisViewModel = analysisViewModel,
                    platformNavigator = platformNavigator,
                    browserLauncher = browserLauncher,
                    clearInputFocus = clearInputFocus,
                    onCompleteSave = { selectMainTab(MainTab.AUTOMATION) }
                )
            },
            wildcardActions = remember(wildcardViewModel) {
                createWildcardActions(
                    wildcardViewModel = wildcardViewModel,
                    selectWildcardFolder = ::selectWildcardFolder
                )
            }
        )
    }
}

private fun createAutomationActions(
    mainViewModel: MainViewModel,
    platformNavigator: AndroidHostPlatformNavigator,
    clearInputFocus: () -> Unit,
    selectMainTab: (MainTab) -> Unit,
    selectWildcardFolder: () -> Unit,
    selectSafWildcardFolder: () -> Unit,
    openWildcardStorageSettings: () -> Unit,
    runAutomation: () -> Unit,
    selectAutomationMode: (AutomationMode) -> Unit
): AutomationAppActions = AutomationAppActions(
    onSelectTab = selectMainTab,
    onShowSettings = mainViewModel::showSettings,
    onSelectThemePalette = mainViewModel::onSelectThemePalette,
    onSelectThemeMode = mainViewModel::onSelectThemeMode,
    onClearFocus = clearInputFocus,
    onHideSettings = mainViewModel::hideSettings,
    onConfirmAccessibilityPrompt = {
        mainViewModel.confirmAccessibilityPrompt()
        platformNavigator.openAccessibilitySettings()
    },
    onDismissAccessibilityPromptToSettings = mainViewModel::dismissAccessibilityPromptToSettings,
    onRefreshStatus = mainViewModel::refreshStatus,
    onSelectWildcardFolder = selectWildcardFolder,
    onSelectSafWildcardFolder = selectSafWildcardFolder,
    onOpenWildcardStorageSettings = openWildcardStorageSettings,
    onOpenAccessibilitySettings = platformNavigator::openAccessibilitySettings,
    onTargetAppSelected = mainViewModel::onTargetAppSelected,
    onFlowImageCountSelected = mainViewModel::onFlowImageCountSelected,
    onPromptTemplateChange = mainViewModel::onPromptTemplateFromEditor,
    onWildcardTokenSuggestionClick = mainViewModel::applyWildcardTokenSuggestion,
    onNavigateHistoryBack = mainViewModel::navigatePromptHistoryBack,
    onNavigateHistoryForward = mainViewModel::navigatePromptHistoryForward,
    onInsertTopInstruction = mainViewModel::insertTopInstruction,
    onInsertBottomInstruction = mainViewModel::insertBottomInstruction,
    onOpenInstructionConfigDialog = mainViewModel::openInstructionConfigDialog,
    onCloseInstructionConfigDialog = mainViewModel::closeInstructionConfigDialog,
    onSaveInstructionConfig = mainViewModel::saveInstructionConfig,
    onToggleParagraphSelectionMode = mainViewModel::toggleParagraphSelectionMode,
    onParagraphOffsetSelected = mainViewModel::selectPromptParagraphAt,
    onDeleteSelectedParagraph = mainViewModel::deleteSelectedPromptParagraph,
    onReplaceSelectedParagraph = mainViewModel::replaceSelectedPromptParagraph,
    onImportFromClipboard = mainViewModel::importPromptFromClipboard,
    onCopyPromptToClipboard = mainViewModel::copyPromptToClipboard,
    onPasteFromClipboard = mainViewModel::pastePromptFromClipboard,
    onCloseGeminiApp = mainViewModel::closeGeminiApp,
    onCleanDeviceMemory = mainViewModel::cleanDeviceMemory,
    onTerminateSelfApp = mainViewModel::terminateSelfApp,
    onRepeatCountChange = mainViewModel::onRepeatCountChange,
    onRunAutomation = runAutomation,
    onCancelAutomation = mainViewModel::cancelAutomation,
    onAutomationModeSelected = selectAutomationMode,
    onPairRemoteDevice = mainViewModel::pairRemoteDevice,
    onDisconnectRemoteDevice = mainViewModel::disconnectRemoteDevice,
    onOpenPromptHistory = mainViewModel::openPromptHistory,
    onClosePromptHistory = mainViewModel::closePromptHistory,
    onSelectPromptHistoryItem = mainViewModel::selectPromptHistoryItem,
    onClearPromptHistory = mainViewModel::clearPromptHistory,
    onOpenGeminiAccountDialog = mainViewModel::openGeminiAccountDialog,
    onCloseGeminiAccountDialog = mainViewModel::closeGeminiAccountDialog,
    onSwitchGeminiAccount = mainViewModel::switchGeminiAccount,
    onCycleNextGeminiAccount = mainViewModel::cycleNextGeminiAccount,
    onAddGeminiAccount = mainViewModel::addGeminiAccount,
    onDeleteGeminiAccount = mainViewModel::deleteGeminiAccount,
    onRetrySwitchGeminiAccount = mainViewModel::retrySwitchGeminiAccount,
    onOpenGeminiManualSwitch = mainViewModel::openGeminiForManualSwitch,
    onClearAccountSwitchError = mainViewModel::clearAccountSwitchError,
    onRunVariation = { selectedText -> mainViewModel.runVariation(selectedText) },
    onOpenVariationPromptConfigDialog = mainViewModel::openVariationPromptConfigDialog,
    onCloseVariationPromptConfigDialog = mainViewModel::closeVariationPromptConfigDialog,
    onSaveVariationPromptConfig = mainViewModel::saveVariationPromptConfig
)

private fun createAnalysisActions(
    analysisViewModel: AnalysisViewModel,
    platformNavigator: AndroidHostPlatformNavigator,
    browserLauncher: AndroidExternalBrowserLauncher,
    clearInputFocus: () -> Unit,
    onCompleteSave: () -> Unit
): AnalysisAppActions = AnalysisAppActions(
    onClearFocus = clearInputFocus,
    onSourcePromptChange = analysisViewModel::onSourcePromptChange,
    onImportFromAutomation = analysisViewModel::importSourcePromptFromAutomation,
    onCategorySelected = analysisViewModel::onCategorySelected,
    onClearTargetSegment = analysisViewModel::clearTargetSegment,
    onGenerate = analysisViewModel::generate,
    onGenerateTxt = analysisViewModel::generateTxt,
    onCancelWork = analysisViewModel::cancelActiveWork,
    onRequestResetSession = analysisViewModel::requestResetSession,
    onConfirmResetSession = analysisViewModel::confirmResetSession,
    onDismissResetSession = analysisViewModel::dismissResetSession,
    onTxtCountChange = analysisViewModel::onTxtCountChange,
    onToggleDirection = analysisViewModel::toggleDirection,
    onCustomHintChange = analysisViewModel::onCustomHintChange,
    onResultFileNameChange = analysisViewModel::onResultFileNameChange,
    onApplyCandidate = { index -> analysisViewModel.applyCandidate(index = index) },
    onCopyCandidate = analysisViewModel::copyCandidate,
    onRestoreOriginalPrompt = analysisViewModel::restoreOriginalPrompt,
    onCopyResults = analysisViewModel::copyGeneratedResults,
    onSaveResults = {
        analysisViewModel.saveGeneratedResults()
        onCompleteSave()
    },
    onConfirmOverwrite = {
        analysisViewModel.confirmOverwrite()
        onCompleteSave()
    },
    onDismissOverwrite = analysisViewModel::dismissOverwrite,
    onShowKeyDialog = analysisViewModel::showKeyDialog,
    onDismissKeyDialog = analysisViewModel::dismissKeyDialog,
    onKeyLabelChange = analysisViewModel::onKeyLabelChange,
    onKeyValueChange = analysisViewModel::onKeyValueChange,
    onRoleProviderSelected = analysisViewModel::onRoleProviderSelected,
    onRoleModelSelected = analysisViewModel::onRoleModelSelected,
    onStartGrokLogin = analysisViewModel::startGrokLogin,
    onCancelGrokLogin = analysisViewModel::cancelGrokLogin,
    onLogoutGrok = analysisViewModel::logoutGrok,
    onOpenGrokLoginUrl = { url -> platformNavigator.openUrlPreferFirefox(browserLauncher, url) },
    onAddApiKey = analysisViewModel::addApiKey,
    onDeleteApiKey = analysisViewModel::deleteApiKey,
    onActivateApiKey = analysisViewModel::activateApiKey,
    onStartEditApiKey = analysisViewModel::startEditingApiKey,
    onEditKeyLabelChange = analysisViewModel::onEditingKeyLabelChange,
    onCancelEditApiKey = analysisViewModel::cancelEditingApiKey,
    onUpdateKeyLabel = analysisViewModel::updateApiKeyLabel
)

private fun createWildcardActions(
    wildcardViewModel: WildcardManagerViewModel,
    selectWildcardFolder: () -> Unit
): WildcardAppActions = WildcardAppActions(
    onRefresh = { wildcardViewModel.refreshFiles(openFirstFile = true) },
    onSelectFolder = selectWildcardFolder,
    onFileClick = wildcardViewModel::selectFile,
    onTextChange = wildcardViewModel::onTextChange,
    onSave = { wildcardViewModel.saveCurrent() },
    onRequestNewFile = wildcardViewModel::requestNewFile,
    onNewFileNameChange = wildcardViewModel::onNewFileNameChange,
    onCreateNewFile = wildcardViewModel::createNewFile,
    onDismissNewFile = wildcardViewModel::dismissNewFileDialog,
    onRequestDelete = wildcardViewModel::requestDeleteSelectedFile,
    onConfirmDelete = wildcardViewModel::confirmDeleteSelectedFile,
    onDismissDelete = wildcardViewModel::dismissDeleteConfirm,
    onRequestRename = wildcardViewModel::requestRenameSelectedFile,
    onRenameFileNameChange = wildcardViewModel::onRenameFileNameChange,
    onConfirmRename = wildcardViewModel::renameSelectedFile,
    onDismissRename = wildcardViewModel::dismissRenameDialog,
    onPaste = wildcardViewModel::pasteFromClipboard,
    onPasteBelow = wildcardViewModel::pasteBelowFromClipboard,
    onCopy = wildcardViewModel::copyToClipboard,
    onUndo = wildcardViewModel::undoClipboardEdit,
    onEnterLineSelectionMode = wildcardViewModel::enterLineSelectionMode,
    onExitLineSelectionMode = wildcardViewModel::exitLineSelectionMode,
    onToggleLineSelection = wildcardViewModel::toggleLineSelection,
    onSelectAllLines = wildcardViewModel::selectAllLines,
    onDeselectAllLines = wildcardViewModel::deselectAllLines,
    onComposeDynamicPrompt = wildcardViewModel::composeDynamicPromptToClipboard,
    onRequestClassify = wildcardViewModel::requestClassify,
    onClassifyCriteriaChange = wildcardViewModel::onClassifyCriteriaChange,
    onClassifyProviderSelected = wildcardViewModel::onClassifyProviderSelected,
    onClassifyModelSelected = wildcardViewModel::onClassifyModelSelected,
    onDismissClassifyCriteria = wildcardViewModel::dismissClassifyCriteriaDialog,
    onRunClassify = wildcardViewModel::runClassify,
    onDismissClassifyPreview = wildcardViewModel::dismissClassifyPreview,
    onClassifyFileNameChange = wildcardViewModel::onClassifyFileNameChange,
    onToggleClassifyFileNameEdit = wildcardViewModel::onToggleClassifyFileNameEdit,
    onSaveClassifyResult = { wildcardViewModel.saveClassifyResult(overwrite = false) },
    onConfirmClassifyOverwrite = wildcardViewModel::confirmClassifyOverwrite,
    onDismissClassifyOverwrite = wildcardViewModel::dismissClassifyOverwrite,
    onConfirmPendingSave = {
        wildcardViewModel.confirmPendingWithSave {
            selectWildcardFolder()
        }
    },
    onConfirmPendingDiscard = {
        if (wildcardViewModel.confirmPendingWithDiscard()) {
            selectWildcardFolder()
        }
    },
    onCancelPending = wildcardViewModel::cancelPendingAction
)
