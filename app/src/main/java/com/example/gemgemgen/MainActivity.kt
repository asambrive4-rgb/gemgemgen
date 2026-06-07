package com.example.gemgemgen

import android.content.ClipboardManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var promptTemplate by rememberSaveable { mutableStateOf("") }
    var repeatCount by rememberSaveable {
        mutableStateOf(AppDefaults.DEFAULT_REPEAT_COUNT.toString())
    }
    var status by remember { mutableStateOf(EnvironmentStatus()) }
    var generatedPrompts by remember { mutableStateOf<List<GeneratedPrompt>>(emptyList()) }
    var previewMessage by rememberSaveable { mutableStateOf("") }
    var previewError by rememberSaveable { mutableStateOf("") }
    var automationState by remember { mutableStateOf<AutomationUiState>(AutomationUiState.Idle) }
    var showRecentLogs by rememberSaveable { mutableStateOf(false) }
    val previewReadiness = PreviewReadiness.check(
        promptTemplate = promptTemplate,
        isWildcardDirectoryAccessible = status.isWildcardDirectoryAccessible
    )
    val runLogger = remember(context) {
        RunLogger.android(context.applicationContext)
    }
    var recentLogs by remember { mutableStateOf(runLogger.loadRecent()) }
    val previewUseCase = remember(context) {
        PromptPreviewUseCase(
            loadWildcards = { WildcardRepository(context).load() }
        )
    }
    val mvpAutomation = remember(context, runLogger) {
        GeminiMvpAutomation(
            context = context.applicationContext,
            runLogger = runLogger
        )
    }

    fun refreshStatus() {
        status = EnvironmentStatusChecker.check(context, promptTemplate)
    }

    fun generatePreview() {
        val result = previewUseCase.generate(
            promptTemplate = promptTemplate,
            repeatCountText = repeatCount
        )
        generatedPrompts = result.generatedPrompts
        previewMessage = result.message
        previewError = result.error
    }

    fun refreshLogs() {
        recentLogs = runLogger.loadRecent()
    }

    fun handleAutomationState(state: AutomationUiState) {
        automationState = state
        if (state.isTerminal()) {
            refreshLogs()
        }
    }

    val wildcardFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            WildcardFolderStore.saveFolderUri(context, uri)
            previewMessage = "wildcard 폴더를 선택했습니다."
            previewError = ""
            refreshStatus()
        } catch (error: SecurityException) {
            previewMessage = ""
            previewError = "폴더 권한 저장 실패: ${error.message ?: "다시 선택해주세요."}"
        }
    }

    LaunchedEffect(promptTemplate) {
        refreshStatus()
    }

    DisposableEffect(lifecycleOwner, promptTemplate) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                            focusManager.clearFocus()
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
                Text(
                    text = "Gemini Auto Sender",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusSection(
                    status = status,
                    onRefresh = ::refreshStatus,
                    onSelectWildcardFolder = {
                        wildcardFolderLauncher.launch(null)
                    },
                    onOpenAccessibilitySettings = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                PromptSection(
                    promptTemplate = promptTemplate,
                    onPromptTemplateChange = { promptTemplate = it },
                    onImportFromClipboard = {
                        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
                        promptTemplate = clipboardManager.primaryClip
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                    }
                )

                OutlinedTextField(
                    value = repeatCount,
                    onValueChange = { value ->
                        repeatCount = value.filter { it.isDigit() }.take(3)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("반복 횟수") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                PreviewSection(
                    generatedPrompts = generatedPrompts,
                    message = previewMessage,
                    error = previewError,
                    onGeneratePreview = ::generatePreview,
                    readiness = previewReadiness
                )

                ActionSection(
                    status = status,
                    automationState = automationState,
                    onRunMvp = {
                        mvpAutomation.run(
                            promptTemplate = promptTemplate,
                            repeatCountText = repeatCount,
                            onStateChange = ::handleAutomationState
                        )
                    },
                    onCancelAutomation = {
                        mvpAutomation.cancel(::handleAutomationState)
                    },
                    recentLogs = recentLogs,
                    showRecentLogs = showRecentLogs,
                    onToggleRecentLogs = {
                        refreshLogs()
                        showRecentLogs = !showRecentLogs
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GeminiAutoSenderAppPreview() {
    GemgemgenTheme {
        GeminiAutoSenderApp()
    }
}

@Composable
private fun StatusSection(
    status: EnvironmentStatus,
    onRefresh: () -> Unit,
    onSelectWildcardFolder: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "상태",
                    style = MaterialTheme.typography.titleMedium,
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
            StatusRow("프롬프트", status.hasPromptTemplate)

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
        }
    }
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
            minLines = 6
        )
        OutlinedButton(onClick = onImportFromClipboard) {
            Text("클립보드에서 가져오기")
        }
    }
}

@Composable
private fun PreviewSection(
    generatedPrompts: List<GeneratedPrompt>,
    message: String,
    error: String,
    onGeneratePreview: () -> Unit,
    readiness: PreviewReadiness
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onGeneratePreview,
            modifier = Modifier.fillMaxWidth(),
            enabled = readiness.canPreview
        ) {
            Text("생성 미리보기")
        }

        Text(
            text = readiness.reason,
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

        generatedPrompts.forEach { generatedPrompt ->
            GeneratedPromptRow(generatedPrompt)
        }
    }
}

@Composable
private fun GeneratedPromptRow(generatedPrompt: GeneratedPrompt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${generatedPrompt.index}번째 생성 결과",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = generatedPrompt.finalPrompt,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ActionSection(
    status: EnvironmentStatus,
    automationState: AutomationUiState,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    recentLogs: List<AutomationRunLog>,
    showRecentLogs: Boolean,
    onToggleRecentLogs: () -> Unit
) {
    val isRunning = automationState is AutomationUiState.Running
    val canRunMvp = status.isReadyToStart && !isRunning

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onRunMvp,
            modifier = Modifier.fillMaxWidth(),
            enabled = canRunMvp
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
            text = if (status.isReadyToStart) {
                "실행 준비 상태가 충족되었습니다."
            } else {
                "실행 전 필요한 상태를 먼저 채워주세요."
            },
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

    if (automationState is AutomationUiState.Running &&
        automationState.lastPrompt.isNotBlank()
    ) {
        Text(
            text = "마지막 생성 프롬프트:\n${automationState.lastPrompt}",
            style = MaterialTheme.typography.bodySmall
        )
    }
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
                text = "${formatLogTime(log.finishedAtMillis)} · ${log.statusLabel()}",
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

private fun AutomationRunLog.statusLabel(): String {
    return when (status) {
        AutomationRunLogStatus.SUCCESS -> "성공"
        AutomationRunLogStatus.STOPPED -> "중지"
        AutomationRunLogStatus.FAILURE -> "실패"
        else -> status
    }
}

private fun formatLogTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(timeMillis))
}
