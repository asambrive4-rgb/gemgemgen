// 역할: 분석 화면의 API 키 관리, 인증, 알림 다이얼로그를 뉴모피즘 디자인과 단일 호스트로 통합 표시합니다.
package com.example.gemgemgen.analysis.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppConfirmDialogContent
import com.example.gemgemgen.ui.theme.AppDialogHostShell
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuConfigDialogContent
import com.example.gemgemgen.ui.theme.NeuInsetBed
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
                useFilledConfirmButton = true,
                onConfirm = actions::confirmResetSession,
                onDismiss = actions::dismissResetSession
            )
            is AnalysisDialogType.Overwrite -> AppConfirmDialogContent(
                title = "같은 파일명이 있습니다",
                message = "${targetDialog.fileName} 파일을 덮어쓸까요? 다른 이름을 입력하려면 취소하고 파일명을 바꿔주세요.",
                confirmLabel = "덮어쓰기",
                dismissLabel = "다른 파일명 입력",
                useFilledConfirmButton = true,
                onConfirm = actions::onConfirmOverwrite,
                onDismiss = actions::dismissOverwrite
            )
            is AnalysisDialogType.GrokLogin -> GrokLoginContent(targetDialog, actions)
        }
    }
}

private val PasswordMaskOutputTransformation = OutputTransformation {
    if (length > 0) {
        replace(0, length, "\u2022".repeat(length))
    }
}

@Composable
private fun KeyManagementContent(
    uiState: AnalysisUiState,
    actions: AnalysisScreenActions
) {
    val focusManager = LocalFocusManager.current
    val keyLabelState = rememberSyncedTextFieldState(
        value = uiState.keyLabelInput,
        onValueChange = actions::onKeyLabelChange
    )
    val keyValueState = rememberSyncedTextFieldState(
        value = uiState.keyValueInput,
        onValueChange = actions::onKeyValueChange
    )
    val canAddKey = keyValueState.text.isNotBlank()

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
            color = AppTheme.colors.textPrimary
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                state = keyLabelState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                onKeyboardAction = { focusManager.moveFocus(FocusDirection.Down) },
                label = { Text("키 이름") },
                placeholder = { Text("개인 키") }
            )
            OutlinedTextField(
                state = keyValueState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = {
                    if (canAddKey) {
                        actions.addApiKey()
                    }
                },
                label = { Text("API 키") },
                outputTransformation = PasswordMaskOutputTransformation
            )

            if (uiState.error.isNotBlank()) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (uiState.message.isNotBlank()) {
                Text(
                    text = uiState.message,
                    color = AppTheme.colors.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            NeuButton(
                onClick = actions::addApiKey,
                enabled = canAddKey,
                isPrimary = true,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "추가",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = AppTheme.colors.cardBorder)

            if (uiState.apiKeys.isEmpty()) {
                Text(
                    text = "저장된 키가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary
                )
            } else {
                uiState.apiKeys.forEach { key ->
                    val backgroundColor = if (key.isActive) {
                        AppTheme.colors.primary.copy(alpha = 0.12f)
                    } else {
                        AppTheme.colors.insetBed
                    }
                    val borderColor = if (key.isActive) {
                        AppTheme.colors.primary
                    } else {
                        AppTheme.colors.cardBorder
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = backgroundColor,
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${key.label} ${key.preview}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary
                                )
                                if (key.isActive) {
                                    Text(
                                        text = "사용 중",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.primary
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!key.isActive) {
                                    NeuButton(
                                        onClick = { actions.activateApiKey(key.id) },
                                        isPrimary = false,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "활성화",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                NeuButton(
                                    onClick = { actions.startEditingApiKey(key) },
                                    isPrimary = false,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "이름 수정",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                TextButton(
                                    onClick = { actions.deleteApiKey(key.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "삭제",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
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
            NeuButton(
                onClick = actions::dismissKeyDialog,
                isPrimary = false,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "닫기",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EditKeyLabelContent(
    dialog: AnalysisDialogType.EditKeyLabel,
    actions: AnalysisScreenActions
) {
    val editingLabelState = rememberSyncedTextFieldState(
        value = dialog.currentLabel,
        onValueChange = actions::onEditingKeyLabelChange,
        resetKey = dialog.originalLabel
    )
    val canSave = editingLabelState.text.isNotBlank()

    NeuConfigDialogContent(
        title = "API 키 이름 수정",
        subtitle = "API 키를 식별할 새 이름을 입력하세요.",
        confirmLabel = "저장",
        dismissLabel = "취소",
        confirmEnabled = canSave,
        useNeuButtons = true,
        onConfirm = actions::updateApiKeyLabel,
        onDismiss = actions::cancelEditingApiKey
    ) {
        OutlinedTextField(
            state = editingLabelState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = {
                if (canSave) {
                    actions.updateApiKeyLabel()
                }
            },
            label = { Text("새 키 이름") },
            placeholder = { Text(dialog.originalLabel) }
        )
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
            color = AppTheme.colors.textPrimary
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (dialog.userCode.isBlank()) {
                Text(
                    text = "로그인 코드를 준비하는 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary
                )
            } else {
                Text(
                    text = "Firefox 또는 브라우저에서 아래 코드를 승인하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary
                )
                NeuInsetBed(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = AppTheme.colors.insetBed,
                    borderColor = AppTheme.colors.insetBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dialog.userCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = AppTheme.colors.textPrimary
                        )
                        NeuButton(
                            onClick = actions::copyGrokUserCode,
                            isPrimary = true,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "코드 복사",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (dialog.verificationUri.isNotBlank()) {
                    NeuButton(
                        onClick = { actions.onOpenGrokLoginUrl(dialog.verificationUri) },
                        isPrimary = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentModifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "브라우저에서 승인 페이지 열기",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (dialog.isPolling) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AppTheme.colors.primary
                    )
                    Text(
                        text = "승인 대기 중...",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            NeuButton(
                onClick = actions::cancelGrokLogin,
                isPrimary = false,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
