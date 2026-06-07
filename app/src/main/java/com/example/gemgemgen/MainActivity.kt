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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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

                AutomationControlStrip(
                    repeatCountText = uiState.repeatCountText,
                    onRepeatCountChange = onRepeatCountChange,
                    onRunMvp = onRunMvp,
                    onCancelAutomation = onCancelAutomation,
                    canRun = uiState.canRun,
                    isRunning = uiState.isRunning,
                    automationState = uiState.automationState
                )

                RecentLogsSectionWrapper(
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "프롬프트 템플릿",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onImportFromClipboard,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "가져오기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        OutlinedTextField(
            value = promptTemplate,
            onValueChange = onPromptTemplateChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            maxLines = 10
        )
    }
}

@Composable
private fun RepeatCountStepper(
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVal = RepeatCountParser.parse(repeatCountText)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (currentVal > 1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable(enabled = currentVal > 1) {
                        onRepeatCountChange((currentVal - 1).toString())
                    }
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = "—",
                    color = if (currentVal > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }

            BasicTextField(
                value = repeatCountText,
                onValueChange = { newValue ->
                    val filtered = RepeatCountParser.normalizeInput(newValue)
                    if (filtered.length <= 3) {
                        onRepeatCountChange(filtered)
                    }
                },
                modifier = Modifier
                    .width(28.dp)
                    .padding(vertical = 2.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (currentVal < 999) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable(enabled = currentVal < 999) {
                        onRepeatCountChange((currentVal + 1).toString())
                    }
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    text = "＋",
                    color = if (currentVal < 999) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AutomationControlStrip(
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    canRun: Boolean,
    isRunning: Boolean,
    automationState: AutomationUiState,
    modifier: Modifier = Modifier
) {
    val statusText = AutomationUiText.statusText(automationState)
    val isError = automationState is AutomationUiState.Failure

    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        automationState is AutomationUiState.Running -> MaterialTheme.colorScheme.primary
        automationState is AutomationUiState.Success -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(50),
                    color = dotColor
                ) {}

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RepeatCountStepper(
                repeatCountText = repeatCountText,
                onRepeatCountChange = onRepeatCountChange
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.width(68.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isRunning) {
                    Button(
                        onClick = onRunMvp,
                        enabled = canRun,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text("시작", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onCancelAutomation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text("중지", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentLogsSectionWrapper(
    recentLogs: List<AutomationRunLog>,
    showRecentLogs: Boolean,
    onToggleRecentLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "최근 로그",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            TextButton(
                onClick = onToggleRecentLogs,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (showRecentLogs) "닫기" else "보기",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (showRecentLogs) {
            if (recentLogs.isEmpty()) {
                Text(
                    text = "저장된 로그가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recentLogs.forEach { log ->
                    RunLogRow(log)
                }
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
