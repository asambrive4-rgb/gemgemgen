// 역할: 메인 화면 하단에서 로컬 모드와 원격 수신/송신 모드를 전환하는 뉴모피즘 토글 패널을 화면에 표시합니다.
package com.example.gemgemgen.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuSegmentedTabBar
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun AutomationModePanel(
    selectedMode: AutomationMode,
    status: RemoteAutomationStatus,
    enabled: Boolean,
    onModeSelected: (AutomationMode) -> Unit,
    onRequestPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        NeuSegmentedTabBar(
            items = AutomationMode.entries,
            selectedItem = selectedMode,
            onItemSelected = onModeSelected,
            enabled = enabled,
            itemLabel = { mode ->
                when (mode) {
                    AutomationMode.NORMAL -> "일반"
                    AutomationMode.SENDER -> "송신"
                    AutomationMode.RECEIVER -> "수신"
                }
            },
            height = 38.dp,
            shape = RoundedCornerShape(14.dp),
            pillShape = RoundedCornerShape(11.dp),
            contentPadding = PaddingValues(3.dp)
        )


        if (selectedMode == AutomationMode.SENDER &&
            status.discoveredDeviceName.isNotBlank() &&
            !status.isPaired
        ) {
            NeuButton(
                onClick = onRequestPair,
                shape = RoundedCornerShape(10.dp),
                isPrimary = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${status.discoveredDeviceName} 연결",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (selectedMode == AutomationMode.RECEIVER &&
            status.isReceiverRunning &&
            !status.isPaired &&
            status.receiverPairingCode.isNotBlank()
        ) {
            Text(
                text = "연결 번호  ${status.receiverPairingCode}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
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
    val isConfirmEnabled = pairingCode.length == 4

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text("${targetDeviceName} 연결") },
        text = {
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { value ->
                    pairingCode = value.filter(Char::isDigit).take(4)
                },
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                label = { Text("${targetDeviceName}에 표시된 4자리 번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (isConfirmEnabled) ImeAction.Done else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isConfirmEnabled) {
                            onConfirm(pairingCode)
                        }
                    }
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(pairingCode)
                },
                enabled = isConfirmEnabled
            ) {
                Text("연결")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("취소")
            }
        }
    )
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
