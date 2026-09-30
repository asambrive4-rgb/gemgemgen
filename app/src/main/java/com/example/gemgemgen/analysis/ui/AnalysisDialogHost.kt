// 역할: 분석 화면의 API 키 관리, 인증, 알림 다이얼로그를 단일 호스트와 화면 액션 인터페이스로 표시합니다.
package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.AppConfirmDialogContent
import com.example.gemgemgen.ui.theme.AppDialogHostShell
import com.example.gemgemgen.ui.theme.appTextFieldColors

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

@Composable
internal fun AnalysisDialogHost(
    activeDialog: AnalysisDialogType,
    uiState: AnalysisUiState,
    actions: AnalysisScreenActions
) {
    if (activeDialog is AnalysisDialogType.None) return

    val onDismissRequest: () -> Unit = {
        when (activeDialog) {
            AnalysisDialogType.None -> Unit
            AnalysisDialogType.KeyManagement -> actions.dismissKeyDialog()
            is AnalysisDialogType.EditKeyLabel -> actions.cancelEditingApiKey()
            AnalysisDialogType.ResetSession -> actions.dismissResetSession()
            is AnalysisDialogType.Overwrite -> actions.dismissOverwrite()
            is AnalysisDialogType.GrokLogin -> actions.cancelGrokLogin()
        }
    }

    AppDialogHostShell(
        activeDialog = activeDialog,
        onDismissRequest = onDismissRequest,
        label = "AnalysisDialogHostCrossfade"
    ) { targetDialog ->
        when (targetDialog) {
            AnalysisDialogType.None -> Unit
            AnalysisDialogType.KeyManagement -> KeyManagementContent(uiState, actions)
            is AnalysisDialogType.EditKeyLabel -> EditKeyLabelContent(targetDialog, actions)
            AnalysisDialogType.ResetSession -> AppConfirmDialogContent(
                title = "분석 세션 비우기",
                message = "원문, 카테고리, 마스킹, 생성 결과와 변주 조건이 모두 지워집니다. " +
                    "자동화 프롬프트와 계정 설정은 유지됩니다.",
                confirmLabel = "비우기",
                onConfirm = actions::confirmResetSession,
                onDismiss = actions::dismissResetSession
            )
            is AnalysisDialogType.Overwrite -> AppConfirmDialogContent(
                title = "같은 파일명이 있습니다",
                message = "${targetDialog.fileName} 파일을 덮어쓸까요? 다른 이름을 입력하려면 취소하고 파일명을 바꿔주세요.",
                confirmLabel = "덮어쓰기",
                dismissLabel = "다른 파일명 입력",
                onConfirm = actions::onConfirmOverwrite,
                onDismiss = actions::dismissOverwrite
            )
            is AnalysisDialogType.GrokLogin -> GrokLoginContent(targetDialog, actions)
        }
    }
}

@Composable
private fun KeyManagementContent(
    uiState: AnalysisUiState,
    actions: AnalysisScreenActions
) {
    val focusManager = LocalFocusManager.current
    val canAddKey = uiState.keyValueInput.isNotBlank()

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
                onValueChange = actions::onKeyLabelChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                label = { Text("키 이름") },
                placeholder = { Text("개인 키") }
            )
            OutlinedTextField(
                value = uiState.keyValueInput,
                onValueChange = actions::onKeyValueChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canAddKey) {
                            actions.addApiKey()
                        }
                    }
                ),
                label = { Text("API 키") },
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = actions::addApiKey,
                enabled = canAddKey,
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
                                    onClick = { actions.activateApiKey(key.id) },
                                    enabled = !key.isActive
                                ) {
                                    Text(if (key.isActive) "활성" else "활성화")
                                }
                                OutlinedButton(
                                    onClick = { actions.startEditingApiKey(key) }
                                ) {
                                    Text("이름 수정")
                                }
                                TextButton(onClick = { actions.deleteApiKey(key.id) }) {
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
            TextButton(onClick = actions::dismissKeyDialog) {
                Text("닫기")
            }
        }
    }
}

@Composable
private fun EditKeyLabelContent(
    dialog: AnalysisDialogType.EditKeyLabel,
    actions: AnalysisScreenActions
) {
    val canSave = dialog.currentLabel.isNotBlank()
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
            onValueChange = actions::onEditingKeyLabelChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (canSave) {
                        actions.updateApiKeyLabel()
                    }
                }
            ),
            label = { Text("새 키 이름") },
            placeholder = { Text(dialog.originalLabel) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = actions::cancelEditingApiKey) {
                Text("취소")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = actions::updateApiKeyLabel,
                enabled = canSave
            ) {
                Text("저장")
            }
        }
    }
}

@Composable
private fun GrokLoginContent(
    dialog: AnalysisDialogType.GrokLogin,
    actions: AnalysisScreenActions
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
            TextButton(onClick = actions::cancelGrokLogin) {
                Text("취소")
            }
        }
    }
}
