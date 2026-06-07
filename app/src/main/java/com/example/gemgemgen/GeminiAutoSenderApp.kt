package com.example.gemgemgen

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
    val floatingBarController = remember(activity) {
        activity?.let { FloatingAutomationBarController(it) }
    }
    var wasFloatingBarShown by remember { mutableStateOf(false) }

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

    DisposableEffect(floatingBarController) {
        onDispose {
            floatingBarController?.hide()
        }
    }

    SideEffect {
        if (uiState.isRunning) {
            floatingBarController?.showOrUpdate(
                uiState = uiState,
                onCancelAutomation = viewModel::cancelAutomation
            )
            wasFloatingBarShown = true
        } else {
            floatingBarController?.hide()
            if (wasFloatingBarShown && uiState.automationState.isTerminal()) {
                wasFloatingBarShown = false
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
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
        onRunMvp = {
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
                return@GeminiAutoSenderScreen
            }

            val accepted = viewModel.runAutomation()
            if (accepted && viewModel.uiState.value.isRunning) {
                floatingBarController?.showOrUpdate(
                    uiState = viewModel.uiState.value,
                    onCancelAutomation = viewModel::cancelAutomation
                )
                activity?.moveTaskToBack(true)
            }
        },
        onCancelAutomation = viewModel::cancelAutomation,
        onToggleRecentLogs = viewModel::toggleRecentLogs
    )
}
