package com.example.gemgemgen.ui.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.gemgemgen.wildcard.ui.WildcardManagerViewModel

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
    val mainUiState by mainViewModel.uiState.collectAsState()
    val automationBarUiState by mainViewModel.automationBarUiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.AUTOMATION) }
    var shouldLoadWildcard by rememberSaveable { mutableStateOf(false) }
    var shouldLoadAnalysis by rememberSaveable { mutableStateOf(false) }
    val wildcardStoreOwner = remember { TabViewModelStoreOwner() }
    val analysisStoreOwner = remember { TabViewModelStoreOwner() }
    val analysisViewModel: AnalysisViewModel? = if (shouldLoadAnalysis) {
        viewModel(
            viewModelStoreOwner = analysisStoreOwner,
            factory = container.analysisViewModelFactory
        )
    } else {
        null
    }
    val analysisUiState = analysisViewModel?.uiState?.collectAsState()?.value
        ?: AnalysisUiState()
    val unusedAnalysisPromptState = remember { TextFieldState() }
    val analysisPromptState = analysisViewModel?.sourcePromptTextFieldState
        ?: unusedAnalysisPromptState
    val wildcardViewModel: WildcardManagerViewModel? = if (shouldLoadWildcard) {
        viewModel(
            viewModelStoreOwner = wildcardStoreOwner,
            factory = container.wildcardViewModelFactory
        )
    } else {
        null
    }
    val wildcardUiState = wildcardViewModel?.uiState?.collectAsState()?.value
    val floatingBarController = remember(activity) {
        activity?.let(::FloatingAutomationBarController)
    }

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
            wildcardViewModel?.onFolderChanged()
        }
    }

    fun selectWildcardFolder() {
        if (wildcardViewModel == null || wildcardViewModel.requestFolderSelection()) {
            wildcardFolderLauncher.launch(null)
        } else {
            shouldLoadWildcard = true
            selectedTab = MainTab.WILDCARD
        }
    }

    fun selectMainTab(tab: MainTab) {
        if (tab != MainTab.AUTOMATION) {
            mainViewModel.cancelParagraphSelection()
        }
        if (selectedTab == MainTab.ANALYSIS && tab != MainTab.ANALYSIS) {
            // 결과·설정·타겟 구간은 유지. 진행 중 AI 작업만 취소.
            analysisViewModel?.trimForInactiveTab()
        }
        if (selectedTab == MainTab.WILDCARD && tab != MainTab.WILDCARD) {
            val dirty = wildcardViewModel?.uiState?.value?.hasUnsavedChanges == true
            if (dirty) {
                // Policy B: keep dirty editor; only drop undo buffers.
                wildcardViewModel?.trimForInactiveTab()
            } else {
                wildcardViewModel?.trimForInactiveTab()
                shouldLoadWildcard = false
                wildcardStoreOwner.clear()
            }
        }
        if (tab == MainTab.ANALYSIS) shouldLoadAnalysis = true
        if (tab == MainTab.WILDCARD) shouldLoadWildcard = true
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
        analysisViewModel?.trimForInactiveTab()
        wildcardViewModel?.trimForInactiveTab()
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
        wildcardViewModel?.onFolderAccessChanged(
            mainUiState.environmentStatus.canEditWildcardFiles
        )
    }

    LaunchedEffect(selectedTab, wildcardViewModel) {
        if (selectedTab == MainTab.WILDCARD) {
            wildcardViewModel?.onTabEntered()
        }
    }

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
            onOpenAccessibilitySettings = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onTargetAppSelected = mainViewModel::onTargetAppSelected,
            onPromptTemplateChange = mainViewModel::onPromptTemplateFromEditor,
            onWildcardTokenSuggestionClick = mainViewModel::applyWildcardTokenSuggestion,
            onUndoPromptEdit = mainViewModel::undoPromptEdit,
            onToggleParagraphSelectionMode =
                mainViewModel::toggleParagraphSelectionMode,
            onParagraphOffsetSelected = mainViewModel::selectPromptParagraphAt,
            onDeleteSelectedParagraph = mainViewModel::deleteSelectedPromptParagraph,
            onReplaceSelectedParagraph = mainViewModel::replaceSelectedPromptParagraph,
            onImportFromClipboard = mainViewModel::importPromptFromClipboard,
            onCopyPromptToClipboard = mainViewModel::copyPromptToClipboard,
            onPasteFromClipboard = mainViewModel::pastePromptFromClipboard,
            onCloseGeminiApp = mainViewModel::closeGeminiApp,
            onTerminateGeminiApp = mainViewModel::terminateGeminiApp,
            onTerminateSelfApp = mainViewModel::terminateSelfApp,
            onRepeatCountChange = mainViewModel::onRepeatCountChange,
            onRunAutomation = ::runAutomation,
            onCancelAutomation = mainViewModel::cancelAutomation
        ),
        analysisActions = AnalysisAppActions(
            onClearFocus = clearInputFocus,
            onSourcePromptChange = { analysisViewModel?.onSourcePromptChange(it) },
            onImportFromAutomation = {
                analysisViewModel?.importSourcePromptFromAutomation(
                    mainViewModel.currentPromptTemplateText()
                )
            },
            onCategorySelected = { analysisViewModel?.onCategorySelected(it) },
            onClearTargetSegment = { analysisViewModel?.clearTargetSegment() },
            onGenerate = { analysisViewModel?.generate() },
            onGenerateTxt = { analysisViewModel?.generateTxt() },
            onCancelWork = { analysisViewModel?.cancelActiveWork() },
            onRequestResetSession = { analysisViewModel?.requestResetSession() },
            onConfirmResetSession = { analysisViewModel?.confirmResetSession() },
            onDismissResetSession = { analysisViewModel?.dismissResetSession() },
            onTxtCountChange = { analysisViewModel?.onTxtCountChange(it) },
            onToggleDirection = { analysisViewModel?.toggleDirection(it) },
            onCustomHintChange = { analysisViewModel?.onCustomHintChange(it) },
            onResultFileNameChange = { analysisViewModel?.onResultFileNameChange(it) },
            onApplyCandidate = { index ->
                analysisViewModel?.applyCandidate(
                    index = index,
                    applyToAutomation = mainViewModel::replacePromptTemplateSegment
                )
            },
            onCopyCandidate = { index -> analysisViewModel?.copyCandidate(index) },
            onRestoreOriginalPrompt = {
                analysisViewModel?.restoreOriginalPrompt(
                    restoreInAutomation = mainViewModel::replacePromptTemplateSegment
                )
            },
            onCopyResults = { analysisViewModel?.copyGeneratedResults() },
            onSaveResults = {
                analysisViewModel?.saveGeneratedResults(
                    onSuccess = ::handoffSavedAnalysisToAutomation
                )
            },
            onConfirmOverwrite = {
                analysisViewModel?.confirmOverwrite(
                    onSuccess = ::handoffSavedAnalysisToAutomation
                )
            },
            onDismissOverwrite = { analysisViewModel?.dismissOverwrite() },
            onShowKeyDialog = { analysisViewModel?.showKeyDialog() },
            onDismissKeyDialog = { analysisViewModel?.dismissKeyDialog() },
            onKeyLabelChange = { analysisViewModel?.onKeyLabelChange(it) },
            onKeyValueChange = { analysisViewModel?.onKeyValueChange(it) },
            onRoleProviderSelected = { role, provider ->
                analysisViewModel?.onRoleProviderSelected(role, provider)
            },
            onRoleModelSelected = { role, modelId ->
                analysisViewModel?.onRoleModelSelected(role, modelId)
            },
            onStartGrokLogin = { analysisViewModel?.startGrokLogin() },
            onCancelGrokLogin = { analysisViewModel?.cancelGrokLogin() },
            onLogoutGrok = { analysisViewModel?.logoutGrok() },
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
            onAddApiKey = { analysisViewModel?.addApiKey() },
            onDeleteApiKey = { analysisViewModel?.deleteApiKey(it) },
            onActivateApiKey = { analysisViewModel?.activateApiKey(it) },
            onStartEditApiKey = { analysisViewModel?.startEditingApiKey(it) },
            onEditKeyLabelChange = { analysisViewModel?.onEditingKeyLabelChange(it) },
            onCancelEditApiKey = { analysisViewModel?.cancelEditingApiKey() },
            onUpdateKeyLabel = { analysisViewModel?.updateApiKeyLabel() }
        ),
        wildcardActions = wildcardViewModel?.let { viewModel ->
            WildcardAppActions(
                onRefresh = { viewModel.refreshFiles(openFirstFile = true) },
                onSelectFolder = ::selectWildcardFolder,
                onFileClick = viewModel::selectFile,
                onTextChange = viewModel::onTextChange,
                onSave = { viewModel.saveCurrent() },
                onRequestNewFile = viewModel::requestNewFile,
                onNewFileNameChange = viewModel::onNewFileNameChange,
                onCreateNewFile = viewModel::createNewFile,
                onDismissNewFile = viewModel::dismissNewFileDialog,
                onRequestDelete = viewModel::requestDeleteSelectedFile,
                onConfirmDelete = viewModel::confirmDeleteSelectedFile,
                onDismissDelete = viewModel::dismissDeleteConfirm,
                onRequestRename = viewModel::requestRenameSelectedFile,
                onRenameFileNameChange = viewModel::onRenameFileNameChange,
                onConfirmRename = viewModel::renameSelectedFile,
                onDismissRename = viewModel::dismissRenameDialog,
                onPaste = viewModel::pasteFromClipboard,
                onPasteBelow = viewModel::pasteBelowFromClipboard,
                onCopy = viewModel::copyToClipboard,
                onUndo = viewModel::undoClipboardEdit,
                onConfirmPendingSave = {
                    viewModel.confirmPendingWithSave {
                        wildcardFolderLauncher.launch(null)
                    }
                },
                onConfirmPendingDiscard = {
                    if (viewModel.confirmPendingWithDiscard()) {
                        wildcardFolderLauncher.launch(null)
                    }
                },
                onCancelPending = viewModel::cancelPendingAction
            )
        }
    )
}
