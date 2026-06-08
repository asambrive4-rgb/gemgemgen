package com.example.gemgemgen

import android.content.Context
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

@Composable
internal fun GeminiAutoSenderApp() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val viewModel: MainViewModel = viewModel(
        factory = remember(context) { MainViewModelFactory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.AUTOMATION) }
    var shouldLoadWildcard by rememberSaveable { mutableStateOf(false) }
    val wildcardViewModel: WildcardManagerViewModel? = if (shouldLoadWildcard) {
        viewModel(
            factory = remember(context) {
                val appContext = context.applicationContext
                WildcardManagerViewModelFactory(
                    fileManager = AndroidWildcardFileManager(appContext),
                    clipboardTextProvider = AndroidClipboardTextProvider(appContext),
                    clipboardTextWriter = AndroidClipboardTextWriter(appContext)
                )
            }
        )
    } else {
        null
    }
    val wildcardUiState = wildcardViewModel?.uiState?.collectAsState()?.value
    val floatingBarController = remember(activity) {
        activity?.let { FloatingAutomationBarController(it) }
    }
    val bringMainActivityToFront = {
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
    val cancelFromFloatingBar = viewModel::cancelAutomation
    val finishFromFloatingBar = {
        floatingBarController?.hide()
        bringMainActivityToFront()
    }

    val wildcardFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        handleWildcardFolderSelected(
            context = context,
            uri = uri,
            viewModel = viewModel,
            wildcardViewModel = wildcardViewModel
        )
    }
    val selectWildcardFolder = {
        val currentWildcardViewModel = wildcardViewModel
        if (currentWildcardViewModel == null || currentWildcardViewModel.requestFolderSelection()) {
            wildcardFolderLauncher.launch(null)
        } else {
            shouldLoadWildcard = true
            selectedTab = MainTab.WILDCARD
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
                focusManager.clearFocus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(floatingBarController) {
        onDispose {
            floatingBarController?.hide()
        }
    }

    SideEffect {
        wildcardViewModel?.onFolderAccessChanged(uiState.environmentStatus.canEditWildcardFiles)
    }

    MainTabbedScreen(
        selectedTab = selectedTab,
        onSelectTab = {
            if (it == MainTab.WILDCARD) shouldLoadWildcard = true
            selectedTab = it
        },
        onShowSettings = viewModel::showSettings,
        tabs = listOf(
            MainTabPage(MainTab.AUTOMATION) {
                GeminiAutoSenderScreen(
                    uiState = uiState,
                    onClearFocus = { focusManager.clearFocus() },
                    onHideSettings = viewModel::hideSettings,
                    onRefreshStatus = viewModel::refreshStatus,
                    onSelectWildcardFolder = selectWildcardFolder,
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onPromptTemplateChange = viewModel::onPromptTemplateChange,
                    onImportFromClipboard = viewModel::importPromptFromClipboard,
                    onRepeatCountChange = viewModel::onRepeatCountChange,
                    onRunMvp = {
                        runAutomationWithOverlayCheck(
                            context = context,
                            activity = activity,
                            viewModel = viewModel,
                            floatingBarController = floatingBarController,
                            onCancelAutomation = cancelFromFloatingBar,
                            onAutomationFinished = finishFromFloatingBar
                        )
                    },
                    onCancelAutomation = viewModel::cancelAutomation,
                    onToggleRecentLogs = viewModel::toggleRecentLogs
                )
            },
            MainTabPage(MainTab.WILDCARD) {
                val currentWildcardViewModel = wildcardViewModel
                val currentWildcardUiState = wildcardUiState
                if (currentWildcardViewModel != null && currentWildcardUiState != null) {
                    WildcardManagerScreen(
                        uiState = currentWildcardUiState,
                        environmentStatus = uiState.environmentStatus,
                        onClearFocus = { focusManager.clearFocus() },
                        onRefresh = { currentWildcardViewModel.refreshFiles(openFirstFile = true) },
                        onSelectFolder = selectWildcardFolder,
                        onFileClick = currentWildcardViewModel::selectFile,
                        onTextChange = currentWildcardViewModel::onTextChange,
                        onSave = { currentWildcardViewModel.saveCurrent() },
                        onRequestNewFile = currentWildcardViewModel::requestNewFile,
                        onNewFileNameChange = currentWildcardViewModel::onNewFileNameChange,
                        onCreateNewFile = currentWildcardViewModel::createNewFile,
                        onDismissNewFile = currentWildcardViewModel::dismissNewFileDialog,
                        onRequestDelete = currentWildcardViewModel::requestDeleteSelectedFile,
                        onConfirmDelete = currentWildcardViewModel::confirmDeleteSelectedFile,
                        onDismissDelete = currentWildcardViewModel::dismissDeleteConfirm,
                        onRequestRename = currentWildcardViewModel::requestRenameSelectedFile,
                        onRenameFileNameChange = currentWildcardViewModel::onRenameFileNameChange,
                        onConfirmRename = currentWildcardViewModel::renameSelectedFile,
                        onDismissRename = currentWildcardViewModel::dismissRenameDialog,
                        onPaste = currentWildcardViewModel::pasteFromClipboard,
                        onPasteBelow = currentWildcardViewModel::pasteBelowFromClipboard,
                        onCopy = currentWildcardViewModel::copyToClipboard,
                        onUndo = currentWildcardViewModel::undoClipboardEdit,
                        onConfirmPendingSave = {
                            if (currentWildcardViewModel.confirmPendingWithSave()) {
                                wildcardFolderLauncher.launch(null)
                            }
                        },
                        onConfirmPendingDiscard = {
                            if (currentWildcardViewModel.confirmPendingWithDiscard()) {
                                wildcardFolderLauncher.launch(null)
                            }
                        },
                        onCancelPending = currentWildcardViewModel::cancelPendingAction
                    )
                }
            }
        )
    )
}

private fun runAutomationWithOverlayCheck(
    context: Context,
    activity: ComponentActivity?,
    viewModel: MainViewModel,
    floatingBarController: FloatingAutomationBarController?,
    onCancelAutomation: () -> Unit,
    onAutomationFinished: () -> Unit
) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(
            context,
            "플로팅 바를 쓰려면 다른 앱 위에 표시 권한이 필요합니다.",
            Toast.LENGTH_LONG
        ).show()
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
        return
    }

    val accepted = viewModel.runAutomation()
    if (accepted && viewModel.uiState.value.isRunning) {
        floatingBarController?.showOrUpdate(
            uiStateFlow = viewModel.uiState,
            onCancelAutomation = onCancelAutomation,
            onAutomationFinished = onAutomationFinished
        )
        activity?.moveTaskToBack(true)
    }
}

private fun handleWildcardFolderSelected(
    context: Context,
    uri: Uri,
    viewModel: MainViewModel,
    wildcardViewModel: WildcardManagerViewModel?
) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        viewModel.saveWildcardFolder(uri.toString())
        wildcardViewModel?.onFolderChanged()
    } catch (error: SecurityException) {
        viewModel.showWildcardFolderSaveError(
            "폴더 권한 저장 실패: ${error.message ?: "다시 선택해주세요."}"
        )
    }
}
