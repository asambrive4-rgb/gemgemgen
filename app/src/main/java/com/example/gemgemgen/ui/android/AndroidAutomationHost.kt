package com.example.gemgemgen.ui.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemgemgen.automation.android.FloatingAutomationBarController
import com.example.gemgemgen.automation.usecase.AutomationStartDecision
import com.example.gemgemgen.automation.ui.MainViewModel
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
    val mainViewModel: MainViewModel = viewModel(factory = container.mainViewModelFactory)
    val mainUiState by mainViewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.AUTOMATION) }
    var shouldLoadWildcard by rememberSaveable { mutableStateOf(false) }
    val wildcardViewModel: WildcardManagerViewModel? = if (shouldLoadWildcard) {
        viewModel(factory = container.wildcardViewModelFactory)
    } else {
        null
    }
    val wildcardUiState = wildcardViewModel?.uiState?.collectAsState()?.value
    val floatingBarController = remember(activity) {
        activity?.let(::FloatingAutomationBarController)
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
        when (mainViewModel.runAutomation()) {
            AutomationStartDecision.Started -> {
                if (!mainViewModel.uiState.value.isRunning) return
                floatingBarController?.showOrUpdate(
                    uiStateFlow = mainViewModel.automationBarUiState,
                    onCancelAutomation = mainViewModel::cancelAutomation,
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
                focusManager.clearFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(floatingBarController) {
        onDispose { floatingBarController?.hide() }
    }

    SideEffect {
        wildcardViewModel?.onFolderAccessChanged(
            mainUiState.environmentStatus.canEditWildcardFiles
        )
    }

    AutomationApp(
        selectedTab = selectedTab,
        mainUiState = mainUiState,
        promptTemplateState = mainViewModel.promptTemplateTextFieldState,
        wildcardUiState = wildcardUiState,
        automationActions = AutomationAppActions(
            onSelectTab = {
                if (it != MainTab.AUTOMATION) {
                    mainViewModel.cancelParagraphSelection()
                }
                if (it == MainTab.WILDCARD) shouldLoadWildcard = true
                selectedTab = it
            },
            onShowSettings = mainViewModel::showSettings,
            onClearFocus = { focusManager.clearFocus() },
            onHideSettings = mainViewModel::hideSettings,
            onRefreshStatus = mainViewModel::refreshStatus,
            onSelectWildcardFolder = ::selectWildcardFolder,
            onOpenAccessibilitySettings = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onTargetAppSelected = mainViewModel::onTargetAppSelected,
            onPromptTemplateChange = mainViewModel::onPromptTemplateChange,
            onUndoPromptEdit = mainViewModel::undoPromptEdit,
            onToggleParagraphSelectionMode =
                mainViewModel::toggleParagraphSelectionMode,
            onParagraphOffsetSelected = mainViewModel::selectPromptParagraphAt,
            onDeleteSelectedParagraph = mainViewModel::deleteSelectedPromptParagraph,
            onReplaceSelectedParagraph = mainViewModel::replaceSelectedPromptParagraph,
            onImportFromClipboard = mainViewModel::importPromptFromClipboard,
            onCopyPromptToClipboard = mainViewModel::copyPromptToClipboard,
            onCloseGeminiApp = mainViewModel::closeGeminiApp,
            onTerminateGeminiApp = mainViewModel::terminateGeminiApp,
            onRepeatCountChange = mainViewModel::onRepeatCountChange,
            onRunAutomation = ::runAutomation,
            onCancelAutomation = mainViewModel::cancelAutomation,
            onToggleRecentLogs = mainViewModel::toggleRecentLogs
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
