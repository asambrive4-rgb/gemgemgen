package com.example.gemgemgen

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun GeminiAutoSenderApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = remember(context) { MainViewModelFactory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val wildcardFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.saveWildcardFolder(uri.toString())
        } catch (error: SecurityException) {
            viewModel.showWildcardFolderSaveError(
                "폴더 권한 저장 실패: ${error.message ?: "다시 선택해주세요."}"
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    GeminiAutoSenderScreen(
        uiState = uiState,
        onClearFocus = { focusManager.clearFocus() },
        onShowSettings = viewModel::showSettings,
        onHideSettings = viewModel::hideSettings,
        onRefreshStatus = viewModel::refreshStatus,
        onSelectWildcardFolder = { wildcardFolderLauncher.launch(null) },
        onOpenAccessibilitySettings = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onPromptTemplateChange = viewModel::onPromptTemplateChange,
        onImportFromClipboard = viewModel::importPromptFromClipboard,
        onRepeatCountChange = viewModel::onRepeatCountChange,
        onRunMvp = viewModel::runAutomation,
        onCancelAutomation = viewModel::cancelAutomation,
        onToggleRecentLogs = viewModel::toggleRecentLogs
    )
}
