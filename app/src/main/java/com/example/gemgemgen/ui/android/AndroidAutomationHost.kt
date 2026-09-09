package com.example.gemgemgen.ui.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.gemgemgen.analysis.ui.AnalysisUiState
import com.example.gemgemgen.analysis.ui.AnalysisViewModel
import com.example.gemgemgen.automation.android.FloatingAutomationBarController
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.ui.MainViewModel
import com.example.gemgemgen.core.android.AndroidExternalBrowserLauncher
import com.example.gemgemgen.ui.AnalysisAppActions
import com.example.gemgemgen.ui.AutomationApp
import com.example.gemgemgen.ui.AutomationAppActions
import com.example.gemgemgen.ui.MainActivity
import com.example.gemgemgen.ui.MainTab
import com.example.gemgemgen.ui.WildcardAppActions
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import com.example.gemgemgen.wildcard.android.AndroidWildcardDirectStorage
import com.example.gemgemgen.wildcard.android.WildcardFolderStore
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
        val initialUri = WildcardFolderStore.getFolderUri(context)
        wildcardFolderLauncher.launch(initialUri)
    }

    fun openWildcardStorageSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        try {
            context.startActivity(appSettingsIntent)
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun selectSafWildcardFolder() {
        if (wildcardViewModel.requestFolderSelection()) {
            launchWildcardFolderPicker()
        } else {
            selectedTab = MainTab.WILDCARD
        }
    }

    fun selectWildcardFolder() {
        if (AndroidWildcardDirectStorage.hasAllFilesAccess()) {
            if (!wildcardViewModel.requestFolderSelection()) return
            wildcardViewModel.onFolderChanged()
            selectedTab = MainTab.WILDCARD
            mainViewModel.refreshStatus()
            return
        }

        if (!mainUiState.environmentStatus.isWildcardDirectoryAccessible) {
            openWildcardStorageSettings()
            return
        }

        selectSafWildcardFolder()
    }

    fun selectMainTab(tab: MainTab) {
        if (tab != MainTab.AUTOMATION) {
            mainViewModel.cancelParagraphSelection()
        }
        if (selectedTab == MainTab.ANALYSIS && tab != MainTab.ANALYSIS) {
            // 결과·설정·타겟 구간 및 진행 중 AI 작업은 유지하고 일시적 다이얼로그 상태만 정리.
            analysisViewModel.trimForInactiveTab()
        }
        if (selectedTab == MainTab.WILDCARD && tab != MainTab.WILDCARD) {
            // 미저장 여부와 무관하게 ViewModel과 텍스트 본문은 보존하고, 무거운 Undo 버퍼만 정리하여 재진입 시 0ms 즉시 표시
            wildcardViewModel.trimForInactiveTab()
        }
        // 와일드카드 탭에서 파일 추가/이름변경 후 돌아와도 추천 목록이 갱신되게 한다.
        if (tab == MainTab.AUTOMATION) {
            mainViewModel.refreshWildcardTokenCandidates()
        }
        selectedTab = tab
    }

    fun handoffSavedAnalysisToAutomation(replacedSource: String) {
        mainViewModel.replacePromptTemplateEntirely(replacedSource)
        selectMainTab(MainTab.AUTOMATION)
    }

    fun bringMainActivityToFront() {
        val appContext = context.applicationContext
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: Intent(appContext, MainActivity::class.java)
        appContext.startActivity(
            launchIntent
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    fun runAutomation() {
        analysisViewModel.trimForInactiveTab()
        wildcardViewModel.trimForInactiveTab()
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
                activity?.moveTaskToBack(true)
            }
            AutomationStartDecision.RemoteStarted -> Unit
            AutomationStartDecision.PermissionRequired -> {
                Toast.makeText(
                    context,
                    "플로팅 바를 띄우려면 다른 앱 위에 표시 권한이 필요합니다.",
                    Toast.LENGTH_LONG
                ).show()
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            AutomationStartDecision.Rejected -> Unit
        }
    }

    fun selectAutomationMode(mode: AutomationMode) {
        if (mode == AutomationMode.RECEIVER &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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

    // 스플릿/멀티윈도우: 다른 창을 탭해 우리 창이 포커스를 잃으면 커서·키보드 즉시 해제.
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .distinctUntilChanged()
            .collect { focused ->
                if (!focused) {
                    clearInputFocus()
                }
            }
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
            automationActions = AutomationAppActions(
                onSelectTab = ::selectMainTab,
                onShowSettings = mainViewModel::showSettings,
                onSelectThemePalette = mainViewModel::onSelectThemePalette,
                onSelectThemeMode = mainViewModel::onSelectThemeMode,
                onClearFocus = clearInputFocus,
            onHideSettings = mainViewModel::hideSettings,
            onConfirmAccessibilityPrompt = {
                mainViewModel.confirmAccessibilityPrompt()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismissAccessibilityPromptToSettings =
                mainViewModel::dismissAccessibilityPromptToSettings,
            onRefreshStatus = mainViewModel::refreshStatus,
            onSelectWildcardFolder = ::selectWildcardFolder,
            onSelectSafWildcardFolder = ::selectSafWildcardFolder,
            onOpenWildcardStorageSettings = ::openWildcardStorageSettings,
            onOpenAccessibilitySettings = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onTargetAppSelected = mainViewModel::onTargetAppSelected,
            onPromptTemplateChange = mainViewModel::onPromptTemplateFromEditor,
            onWildcardTokenSuggestionClick = mainViewModel::applyWildcardTokenSuggestion,
            onUndoPromptEdit = mainViewModel::undoPromptEdit,
            onInsertSystemInstruction = mainViewModel::insertSystemInstruction,
            onParagraphOffsetSelected = mainViewModel::selectPromptParagraphAt,
            onDeleteSelectedParagraph = mainViewModel::deleteSelectedPromptParagraph,
            onReplaceSelectedParagraph = mainViewModel::replaceSelectedPromptParagraph,
            onImportFromClipboard = mainViewModel::importPromptFromClipboard,
            onCopyPromptToClipboard = mainViewModel::copyPromptToClipboard,
            onPasteFromClipboard = mainViewModel::pastePromptFromClipboard,
            onCloseGeminiApp = mainViewModel::closeGeminiApp,
            onTerminateGeminiApp = mainViewModel::terminateGeminiApp,
            onCleanDeviceMemory = mainViewModel::cleanDeviceMemory,
            onTerminateSelfApp = mainViewModel::terminateSelfApp,
            onRepeatCountChange = mainViewModel::onRepeatCountChange,
            onRunAutomation = ::runAutomation,
            onCancelAutomation = mainViewModel::cancelAutomation,
            onAutomationModeSelected = ::selectAutomationMode,
            onPairRemoteDevice = mainViewModel::pairRemoteDevice,
            onDisconnectRemoteDevice = mainViewModel::disconnectRemoteDevice,
            onOpenPromptHistory = mainViewModel::openPromptHistory,
            onClosePromptHistory = mainViewModel::closePromptHistory,
            onSelectPromptHistoryItem = mainViewModel::selectPromptHistoryItem,
            onClearPromptHistory = mainViewModel::clearPromptHistory
        ),
        analysisActions = AnalysisAppActions(
            onClearFocus = clearInputFocus,
            onSourcePromptChange = analysisViewModel::onSourcePromptChange,
            onImportFromAutomation = {
                analysisViewModel.importSourcePromptFromAutomation(
                    mainViewModel.currentPromptTemplateText()
                )
            },
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
            onApplyCandidate = { index ->
                analysisViewModel.applyCandidate(
                    index = index,
                    applyToAutomation = mainViewModel::replacePromptTemplateSegment
                )
            },
            onCopyCandidate = analysisViewModel::copyCandidate,
            onRestoreOriginalPrompt = {
                analysisViewModel.restoreOriginalPrompt(
                    restoreInAutomation = mainViewModel::replacePromptTemplateSegment
                )
            },
            onCopyResults = analysisViewModel::copyGeneratedResults,
            onSaveResults = {
                analysisViewModel.saveGeneratedResults(
                    onSuccess = ::handoffSavedAnalysisToAutomation
                )
            },
            onConfirmOverwrite = {
                analysisViewModel.confirmOverwrite(
                    onSuccess = ::handoffSavedAnalysisToAutomation
                )
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
            onOpenGrokLoginUrl = { url ->
                val opened = browserLauncher.openUrlPreferFirefox(url)
                if (!opened) {
                    Toast.makeText(
                        context,
                        "브라우저를 열 수 없습니다. Firefox 설치 여부를 확인해 주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onAddApiKey = analysisViewModel::addApiKey,
            onDeleteApiKey = analysisViewModel::deleteApiKey,
            onActivateApiKey = analysisViewModel::activateApiKey,
            onStartEditApiKey = analysisViewModel::startEditingApiKey,
            onEditKeyLabelChange = analysisViewModel::onEditingKeyLabelChange,
            onCancelEditApiKey = analysisViewModel::cancelEditingApiKey,
            onUpdateKeyLabel = analysisViewModel::updateApiKeyLabel
        ),
        wildcardActions = WildcardAppActions(
            onRefresh = { wildcardViewModel.refreshFiles(openFirstFile = true) },
            onSelectFolder = ::selectWildcardFolder,
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
    )
    }
}
