package com.example.gemgemgen

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemgemgen.ui.theme.GemgemgenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GemgemgenTheme {
                GeminiAutoSenderApp()
            }
        }
    }
}

@Composable
private fun GeminiAutoSenderApp() {
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

@Composable
private fun GeminiAutoSenderScreen(
    uiState: MainUiState,
    onClearFocus: () -> Unit,
    onShowSettings: () -> Unit,
    onHideSettings: () -> Unit,
    onRefreshStatus: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onPromptTemplateChange: (String) -> Unit,
    onImportFromClipboard: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    onToggleRecentLogs: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Final)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        if (up != null && !down.isConsumed && !up.isConsumed) {
                            onClearFocus()
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gemini Auto Sender",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = onShowSettings) {
                        Text("설정")
                    }
                }

                PromptSection(
                    promptTemplate = uiState.promptTemplate,
                    onPromptTemplateChange = onPromptTemplateChange,
                    onImportFromClipboard = onImportFromClipboard
                )

                OutlinedTextField(
                    value = uiState.repeatCountText,
                    onValueChange = onRepeatCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("반복 횟수") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                ActionSection(
                    automationState = uiState.automationState,
                    canRun = uiState.canRun,
                    isRunning = uiState.isRunning,
                    readinessMessage = uiState.readinessMessage,
                    onRunMvp = onRunMvp,
                    onCancelAutomation = onCancelAutomation,
                    recentLogs = uiState.recentLogs,
                    showRecentLogs = uiState.showRecentLogs,
                    onToggleRecentLogs = onToggleRecentLogs
                )
            }

            if (uiState.showSettings) {
                StatusSettingsDialog(
                    status = uiState.environmentStatus,
                    hasPromptTemplate = uiState.hasPromptTemplate,
                    message = uiState.settingsMessage,
                    error = uiState.settingsError,
                    onDismiss = onHideSettings,
                    onRefresh = onRefreshStatus,
                    onSelectWildcardFolder = onSelectWildcardFolder,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GeminiAutoSenderAppPreview() {
    GemgemgenTheme {
        GeminiAutoSenderScreen(
            uiState = MainUiState(),
            onClearFocus = {},
            onShowSettings = {},
            onHideSettings = {},
            onRefreshStatus = {},
            onSelectWildcardFolder = {},
            onOpenAccessibilitySettings = {},
            onPromptTemplateChange = {},
            onImportFromClipboard = {},
            onRepeatCountChange = {},
            onRunMvp = {},
            onCancelAutomation = {},
            onToggleRecentLogs = {}
        )
    }
}

@Composable
private fun StatusSettingsDialog(
    status: EnvironmentStatus,
    hasPromptTemplate: Boolean,
    message: String,
    error: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "상태",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onRefresh) {
                        Text("새로고침")
                    }
                }

                StatusRow("Gemini 앱", status.isGeminiInstalled)
                StatusRow("접근성 서비스", status.isAccessibilityServiceEnabled)
                StatusRow("WRITE_SECURE_SETTINGS", status.hasWriteSecureSettingsPermission)
                StatusRow("wildcard 폴더", status.isWildcardDirectoryAccessible)
                StatusRow("프롬프트", hasPromptTemplate)

                if (!status.isAccessibilityServiceEnabled) {
                    Button(onClick = onOpenAccessibilitySettings) {
                        Text("접근성 설정 열기")
                    }
                }

                OutlinedButton(onClick = onSelectWildcardFolder) {
                    Text("wildcard 폴더 선택")
                }

                if (!status.hasWriteSecureSettingsPermission && status.adbGrantCommand.isNotBlank()) {
                    Text(
                        text = "ADB 권한 명령어:\n${status.adbGrantCommand}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "wildcard 폴더: ${status.wildcardDirectoryPath.ifBlank { "선택 안 됨" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Null Keyboard 전환 대상: ${status.nullKeyboardTargetImeId}",
                    style = MaterialTheme.typography.bodySmall
                )

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun StatusRow(label: String, isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        StatusBadge(isReady = isReady)
    }
}

@Composable
private fun StatusBadge(isReady: Boolean) {
    val containerColor = if (isReady) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val textColor = if (isReady) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = textColor
    ) {
        Text(
            text = if (isReady) "정상" else "필요",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun PromptSection(
    promptTemplate: String,
    onPromptTemplateChange: (String) -> Unit,
    onImportFromClipboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = promptTemplate,
            onValueChange = onPromptTemplateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("프롬프트 템플릿") },
            minLines = 6,
            maxLines = 10
        )
        OutlinedButton(onClick = onImportFromClipboard) {
            Text("클립보드에서 가져오기")
        }
    }
}

@Composable
private fun ActionSection(
    automationState: AutomationUiState,
    canRun: Boolean,
    isRunning: Boolean,
    readinessMessage: String,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    recentLogs: List<AutomationRunLog>,
    showRecentLogs: Boolean,
    onToggleRecentLogs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onRunMvp,
            modifier = Modifier.fillMaxWidth(),
            enabled = canRun
        ) {
            Text("실행 시작")
        }

        AutomationStateText(automationState)

        if (isRunning) {
            OutlinedButton(
                onClick = onCancelAutomation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("중지")
            }
        }

        OutlinedButton(
            onClick = onToggleRecentLogs,
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Text(if (showRecentLogs) "최근 로그 닫기" else "최근 로그")
        }

        if (showRecentLogs) {
            RecentLogsSection(recentLogs)
        }

        Text(
            text = readinessMessage,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AutomationStateText(automationState: AutomationUiState) {
    val text = AutomationUiText.statusText(automationState)
    val color = when (automationState) {
        is AutomationUiState.Failure -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun RecentLogsSection(recentLogs: List<AutomationRunLog>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "최근 로그",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (recentLogs.isEmpty()) {
            Text(
                text = "저장된 로그가 없습니다.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            recentLogs.forEach { log ->
                RunLogRow(log)
            }
        }
    }
}

@Composable
private fun RunLogRow(log: AutomationRunLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = RunLogUiText.title(log),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "마지막 단계: ${log.lastStep.ifBlank { "기록 없음" }}",
                style = MaterialTheme.typography.bodySmall
            )
            if (log.message.isNotBlank()) {
                Text(
                    text = "메시지: ${log.message}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "입력기 복구: ${log.imeRestoreMessage.ifBlank { "기록 없음" }}",
                style = MaterialTheme.typography.bodySmall
            )
            if (log.repeatCount > 0) {
                Text(
                    text = "반복: ${log.completedCount}/${log.repeatCount}, 성공 ${log.successCount}, 실패 ${log.failureCount}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (log.markerStatus.isNotBlank()) {
                Text(
                    text = "세션 마커: ${log.markerStatus}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
