package com.example.gemgemgen.analysis.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemgemgen.analysis.usecase.GeminiApiKeySummary

sealed interface AnalysisDialogType {
    data object None : AnalysisDialogType
    data object KeyManagement : AnalysisDialogType
    data class EditKeyLabel(val originalLabel: String, val currentLabel: String) : AnalysisDialogType
    data object ResetSession : AnalysisDialogType
    data class Overwrite(val fileName: String) : AnalysisDialogType
    data class GrokLogin(
        val userCode: String,
        val verificationUri: String,
        val isPolling: Boolean
    ) : AnalysisDialogType
}

fun deriveActiveAnalysisDialog(uiState: AnalysisUiState): AnalysisDialogType {
    return when {
        uiState.editingApiKey != null ->
            AnalysisDialogType.EditKeyLabel(
                originalLabel = uiState.editingApiKey.label,
                currentLabel = uiState.editingKeyLabelInput
            )
        uiState.showKeyDialog ->
            AnalysisDialogType.KeyManagement
        uiState.showResetConfirmation ->
            AnalysisDialogType.ResetSession
        uiState.pendingOverwriteFileName != null ->
            AnalysisDialogType.Overwrite(uiState.pendingOverwriteFileName)
        uiState.showGrokLoginDialog ->
            AnalysisDialogType.GrokLogin(
                userCode = uiState.grokLoginUserCode,
                verificationUri = uiState.grokLoginVerificationUri,
                isPolling = uiState.isGrokLoginPolling
            )
        else ->
            AnalysisDialogType.None
    }
}

internal data class AnalysisDialogActions(
    val onDismissKeyDialog: () -> Unit,
    val onKeyLabelChange: (String) -> Unit,
    val onKeyValueChange: (String) -> Unit,
    val onAddApiKey: () -> Unit,
    val onDeleteApiKey: (String) -> Unit,
    val onActivateApiKey: (String) -> Unit,
    val onStartEditApiKey: (GeminiApiKeySummary) -> Unit,
    val onEditKeyLabelChange: (String) -> Unit,
    val onCancelEditApiKey: () -> Unit,
    val onUpdateKeyLabel: () -> Unit,
    val onConfirmResetSession: () -> Unit,
    val onDismissResetSession: () -> Unit,
    val onConfirmOverwrite: () -> Unit,
    val onDismissOverwrite: () -> Unit,
    val onOpenGrokLoginUrl: (String) -> Unit,
    val onCancelGrokLogin: () -> Unit
)

enum class AnalysisDialogStage {
    KEY_MANAGEMENT,
    EDIT_KEY_LABEL,
    RESET_SESSION,
    OVERWRITE,
    GROK_LOGIN
}

fun deriveAnalysisDialogStage(dialog: AnalysisDialogType): AnalysisDialogStage? {
    return when (dialog) {
        AnalysisDialogType.None -> null
        AnalysisDialogType.KeyManagement -> AnalysisDialogStage.KEY_MANAGEMENT
        is AnalysisDialogType.EditKeyLabel -> AnalysisDialogStage.EDIT_KEY_LABEL
        AnalysisDialogType.ResetSession -> AnalysisDialogStage.RESET_SESSION
        is AnalysisDialogType.Overwrite -> AnalysisDialogStage.OVERWRITE
        is AnalysisDialogType.GrokLogin -> AnalysisDialogStage.GROK_LOGIN
    }
}

@Composable
internal fun AnalysisDialogHost(
    activeDialog: AnalysisDialogType,
    uiState: AnalysisUiState,
    actions: AnalysisDialogActions
) {
    val activeStage = deriveAnalysisDialogStage(activeDialog) ?: return

    val onDismissRequest: () -> Unit = {
        when (activeDialog) {
            AnalysisDialogType.None -> Unit
            AnalysisDialogType.KeyManagement -> actions.onDismissKeyDialog()
            is AnalysisDialogType.EditKeyLabel -> actions.onCancelEditApiKey()
            AnalysisDialogType.ResetSession -> actions.onDismissResetSession()
            is AnalysisDialogType.Overwrite -> actions.onDismissOverwrite()
            is AnalysisDialogType.GrokLogin -> actions.onCancelGrokLogin()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .imePadding()
                .widthIn(min = 280.dp, max = 560.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            AnimatedContent(
                targetState = activeStage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                contentAlignment = Alignment.Center,
                label = "AnalysisDialogHostCrossfade"
            ) { stage ->
                when (stage) {
                    AnalysisDialogStage.KEY_MANAGEMENT -> KeyManagementContent(uiState, actions)
                    AnalysisDialogStage.EDIT_KEY_LABEL -> (activeDialog as? AnalysisDialogType.EditKeyLabel)?.let { EditKeyLabelContent(it, actions) }
                    AnalysisDialogStage.RESET_SESSION -> ResetSessionContent(actions)
                    AnalysisDialogStage.OVERWRITE -> (activeDialog as? AnalysisDialogType.Overwrite)?.let { OverwriteContent(it, actions) }
                    AnalysisDialogStage.GROK_LOGIN -> (activeDialog as? AnalysisDialogType.GrokLogin)?.let { GrokLoginContent(it, actions) }
                }
            }
        }
    }
}

@Composable
private fun KeyManagementContent(
    uiState: AnalysisUiState,
    actions: AnalysisDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gemini API 키 관리",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = uiState.keyLabelInput,
                onValueChange = actions.onKeyLabelChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                label = { Text("키 이름") },
                placeholder = { Text("개인 키") }
            )
            OutlinedTextField(
                value = uiState.keyValueInput,
                onValueChange = actions.onKeyValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                label = { Text("API 키") },
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = actions.onAddApiKey,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("추가")
            }

            HorizontalDivider()

            if (uiState.apiKeys.isEmpty()) {
                Text(
                    text = "저장된 키가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.apiKeys.forEach { key ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = if (key.isActive) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${key.label} ${key.preview}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { actions.onActivateApiKey(key.id) },
                                    enabled = !key.isActive
                                ) {
                                    Text(if (key.isActive) "활성" else "활성화")
                                }
                                OutlinedButton(
                                    onClick = { actions.onStartEditApiKey(key) }
                                ) {
                                    Text("이름 수정")
                                }
                                TextButton(onClick = { actions.onDeleteApiKey(key.id) }) {
                                    Text("삭제")
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissKeyDialog) {
                Text("닫기")
            }
        }
    }
}

@Composable
private fun EditKeyLabelContent(
    dialog: AnalysisDialogType.EditKeyLabel,
    actions: AnalysisDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "API 키 이름 수정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = dialog.currentLabel,
            onValueChange = actions.onEditKeyLabelChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            label = { Text("새 키 이름") },
            placeholder = { Text(dialog.originalLabel) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onCancelEditApiKey) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = actions.onUpdateKeyLabel) {
                Text("저장")
            }
        }
    }
}

@Composable
private fun ResetSessionContent(
    actions: AnalysisDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "분석 세션 비우기",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "원문, 카테고리, 마스킹, 생성 결과와 변주 조건이 모두 지워집니다. " +
                "자동화 프롬프트와 계정 설정은 유지됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissResetSession) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = actions.onConfirmResetSession) {
                Text("비우기")
            }
        }
    }
}

@Composable
private fun OverwriteContent(
    dialog: AnalysisDialogType.Overwrite,
    actions: AnalysisDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "같은 파일명이 있습니다",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${dialog.fileName} 파일을 덮어쓸까요? 다른 이름을 입력하려면 취소하고 파일명을 바꿔주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onDismissOverwrite) {
                Text("다른 파일명 입력")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = actions.onConfirmOverwrite) {
                Text("덮어쓰기")
            }
        }
    }
}

@Composable
private fun GrokLoginContent(
    dialog: AnalysisDialogType.GrokLogin,
    actions: AnalysisDialogActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Grok 로그인",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dialog.userCode.isBlank()) {
                Text("로그인 코드를 준비하는 중...")
            } else {
                Text("Firefox에서 아래 코드를 승인하세요.")
                Text(
                    text = dialog.userCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (dialog.verificationUri.isNotBlank()) {
                    TextButton(onClick = { actions.onOpenGrokLoginUrl(dialog.verificationUri) }) {
                        Text(dialog.verificationUri)
                    }
                }
            }
            if (dialog.isPolling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "승인 대기 중...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions.onCancelGrokLogin) {
                Text("취소")
            }
        }
    }
}
