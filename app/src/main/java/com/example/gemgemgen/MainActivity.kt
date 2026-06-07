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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val previewReadiness = PreviewReadiness.check(
        promptTemplate = promptTemplate,
        isWildcardDirectoryAccessible = status.isWildcardDirectoryAccessible
    )
    val previewUseCase = remember(context) {
        PromptPreviewUseCase(
            loadWildcards = { WildcardRepository(context).load() }
        )
    }
    val oneShotAutomation = remember(context) {
        GeminiOneShotAutomation(context.applicationContext)
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
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
                onRunOneShot = {
                    oneShotAutomation.run { state ->
                        automationState = state
                    }
                }
            )
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
    onRunOneShot: () -> Unit
) {
    val isRunning = automationState is AutomationUiState.Running
    val canRunOneShot = status.isGeminiInstalled &&
        status.isAccessibilityServiceEnabled &&
        !isRunning

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onRunOneShot,
            modifier = Modifier.fillMaxWidth(),
            enabled = canRunOneShot
        ) {
            Text("M3 테스트 전송")
        }

        if (!canRunOneShot && !isRunning) {
            Text(
                text = oneShotDisabledReason(status),
                style = MaterialTheme.typography.bodySmall
            )
        }

        AutomationStateText(automationState)

        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Text("실행 시작")
        }
        OutlinedButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Text("최근 로그")
        }
        Text(
            text = if (status.isReadyToStart) {
                "M2 미리보기 준비 상태가 충족되었습니다."
            } else {
                "실행 전 필요한 상태를 먼저 채워주세요."
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun oneShotDisabledReason(status: EnvironmentStatus): String {
    return when {
        !status.isGeminiInstalled -> "Gemini 앱 설치 상태를 확인하지 못했습니다."
        !status.isAccessibilityServiceEnabled -> "접근성 서비스를 켠 뒤 앱으로 돌아와주세요."
        else -> "M3 테스트 전송을 시작할 수 없습니다."
    }
}

@Composable
private fun AutomationStateText(automationState: AutomationUiState) {
    val text = when (automationState) {
        AutomationUiState.Idle -> "M3 테스트 전송은 고정 프롬프트를 Gemini에 1회 보냅니다."
        is AutomationUiState.Running -> "진행 중: ${automationState.step}"
        AutomationUiState.Success -> "M3 테스트 전송 성공"
        is AutomationUiState.Failure -> "M3 테스트 전송 실패: ${automationState.message}"
    }
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
