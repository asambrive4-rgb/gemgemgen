// 역할: 에디터 헤더용 컴팩트 자동화 모드(일반/송신/수신) 3단 토글, 헤더 하단 원격 연결 서브 바, 수신 모드 하단 고정 상태 바 및 3D 조약돌 스타일 기기 연결 다이얼로그를 표시합니다.
package com.example.gemgemgen.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuModalDialogFrame
import com.example.gemgemgen.ui.theme.appTextFieldColors

/**
 * 프롬프트 에디터 헤더의 대상 앱 선택 칩 우측에 작게 배치되는 3단 세그먼트 캡슐 토글 ([일반 | 송신 | 수신]).
 */
@Composable
fun CompactAutomationModeToggle(
    selectedMode: AutomationMode,
    enabled: Boolean,
    onModeSelected: (AutomationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(10.dp)
    val pillShape = RoundedCornerShape(8.dp)

    NeuInsetBed(
        modifier = modifier
            .height(30.dp)
            .semantics {
                contentDescription = "자동화 동작 모드 선택: 현재 ${modeDisplayLabel(selectedMode)}"
            },
        shape = containerShape,
        backgroundColor = AppTheme.colors.insetBed,
        borderColor = AppTheme.colors.insetBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AutomationMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                val label = modeDisplayLabel(mode)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(
                            if (isSelected) {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = pillShape,
                                    ambientColor = AppTheme.colors.primary.copy(alpha = 0.35f),
                                    spotColor = AppTheme.colors.primary.copy(alpha = 0.25f)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(pillShape)
                        .background(
                            if (isSelected) AppTheme.colors.primary else Color.Transparent
                        )
                        .clickable(enabled = enabled) {
                            if (mode != selectedMode) {
                                onModeSelected(mode)
                            }
                        }
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = when {
                            isSelected -> AppTheme.colors.onPrimary
                            enabled -> AppTheme.colors.textSecondary
                            else -> AppTheme.colors.textSecondary.copy(alpha = 0.5f)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun modeDisplayLabel(mode: AutomationMode): String = when (mode) {
    AutomationMode.NORMAL -> "일반"
    AutomationMode.SENDER -> "송신"
    AutomationMode.RECEIVER -> "수신"
}

/**
 * 헤더 바로 아래에서 송신 기기 연결 버튼, 수신 연결 번호, 상태 안내 문구를 슬림하게 노출하는 서브 바.
 */
@Composable
fun AutomationModeSubBar(
    selectedMode: AutomationMode,
    status: RemoteAutomationStatus,
    statusText: String?,
    isStatusError: Boolean,
    onRequestPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showSenderPairButton = selectedMode == AutomationMode.SENDER &&
        status.discoveredDeviceName.isNotBlank() &&
        !status.isPaired
    val showReceiverPairingCode = selectedMode == AutomationMode.RECEIVER &&
        status.isReceiverRunning &&
        !status.isPaired &&
        status.receiverPairingCode.isNotBlank()
    val hasStatusText = !statusText.isNullOrBlank()

    if (!showSenderPairButton && !showReceiverPairingCode && !hasStatusText) {
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (hasStatusText) {
            Text(
                text = statusText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isStatusError) {
                    MaterialTheme.colorScheme.error
                } else {
                    AppTheme.colors.textSecondary
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }

        if (showSenderPairButton) {
            NeuButton(
                onClick = onRequestPair,
                shape = RoundedCornerShape(10.dp),
                isPrimary = true,
                elevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "${status.discoveredDeviceName} 연결",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showReceiverPairingCode) {
            Text(
                text = "연결 번호 ${status.receiverPairingCode}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.primary
            )
        }
    }
}

/**
 * 수신 모드일 때 시작 바 대신 화면 하단에 고정 표시되는 수신 대기/연결 상태 카드.
 */
@Composable
fun AutomationReceiverBottomBar(
    status: RemoteAutomationStatus,
    modifier: Modifier = Modifier
) {
    val message = resolveConnectionText(AutomationMode.RECEIVER, status)
    val showPairingCode = status.isReceiverRunning &&
        !status.isPaired &&
        status.receiverPairingCode.isNotBlank()

    NeuCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(),
        shape = RoundedCornerShape(18.dp),
        elevation = 5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(50),
                    color = if (status.isPaired || status.isReceiverRunning) {
                        AppTheme.colors.primary
                    } else {
                        AppTheme.colors.cardBorder
                    }
                ) {}

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = AppTheme.colors.textSecondary
                )
            }

            if (showPairingCode) {
                Text(
                    text = "연결 번호 ${status.receiverPairingCode}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AutomationModePairDialog(
    targetDeviceName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pairingCode by remember { mutableStateOf("") }
    val pairingCodeState = rememberSyncedTextFieldState(
        value = pairingCode,
        onValueChange = { value ->
            pairingCode = value.filter(Char::isDigit).take(4)
        }
    )
    val isConfirmEnabled = pairingCode.length == 4

    NeuModalDialogFrame(
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${targetDeviceName} 연결",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "${targetDeviceName}에 표시된 4자리 번호를 입력하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )
        }

        OutlinedTextField(
            state = pairingCodeState,
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                color = AppTheme.colors.textPrimary
            ),
            placeholder = {
                Text(
                    text = "0000",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = AppTheme.colors.textSecondary.copy(alpha = 0.35f)
                    )
                )
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation {
                val digitsOnly = asCharSequence().filter(Char::isDigit).take(4).toString()
                if (asCharSequence().toString() != digitsOnly) {
                    replace(0, length, digitsOnly)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (isConfirmEnabled) ImeAction.Done else ImeAction.Default
            ),
            onKeyboardAction = {
                if (isConfirmEnabled) {
                    onConfirm(pairingCode)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NeuButton(
                onClick = onDismiss,
                isPrimary = false,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            NeuButton(
                onClick = { onConfirm(pairingCode) },
                enabled = isConfirmEnabled,
                isPrimary = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "연결",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

internal fun resolveConnectionText(
    selectedMode: AutomationMode,
    status: RemoteAutomationStatus
): String {
    if (selectedMode == AutomationMode.NORMAL) return ""
    if (selectedMode == AutomationMode.SENDER && status.automationState is AutomationRunState.Running) return ""

    return when (selectedMode) {
        AutomationMode.SENDER -> status.connectionMessage
        AutomationMode.RECEIVER -> status.message
        AutomationMode.NORMAL -> ""
    }.ifBlank {
        if (selectedMode == AutomationMode.SENDER) {
            "S25 FE를 찾는 중입니다."
        } else {
            "수신 대기를 시작하는 중입니다."
        }
    }
}
