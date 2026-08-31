package com.example.gemgemgen.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.ui.theme.RemoteModePurple
import com.example.gemgemgen.ui.theme.RemoteModePurpleDark

@Composable
fun AutomationModePanel(
    selectedMode: AutomationMode,
    status: RemoteAutomationStatus,
    enabled: Boolean,
    onModeSelected: (AutomationMode) -> Unit,
    onRequestPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modeColor = if (isSystemInDarkTheme()) RemoteModePurpleDark else RemoteModePurple
    val selectedContentColor = if (isSystemInDarkTheme()) Color(0xFF241638) else Color.White

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AutomationMode.entries.forEach { mode ->
                    val label = when (mode) {
                        AutomationMode.NORMAL -> "일반"
                        AutomationMode.SENDER -> "송신"
                        AutomationMode.RECEIVER -> "수신"
                    }
                    if (selectedMode == mode) {
                        Button(
                            onClick = { onModeSelected(mode) },
                            enabled = enabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = modeColor,
                                contentColor = selectedContentColor
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onModeSelected(mode) },
                            enabled = enabled,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = modeColor
                            ),
                            border = BorderStroke(1.dp, modeColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            if (selectedMode != AutomationMode.NORMAL &&
                !(selectedMode == AutomationMode.SENDER &&
                    status.automationState is AutomationRunState.Running)
            ) {
                Text(
                    text = when (selectedMode) {
                        AutomationMode.SENDER -> status.connectionMessage
                        AutomationMode.RECEIVER -> status.message
                        AutomationMode.NORMAL -> ""
                    }.ifBlank {
                        if (selectedMode == AutomationMode.SENDER) {
                            "S25 FE를 찾는 중입니다."
                        } else {
                            "수신 대기를 시작하는 중입니다."
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedMode == AutomationMode.SENDER &&
                status.discoveredDeviceName.isNotBlank() &&
                !status.isPaired
            ) {
                OutlinedButton(
                    onClick = onRequestPair,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${status.discoveredDeviceName} 연결")
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
                    color = MaterialTheme.colorScheme.primary
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
    val isConfirmEnabled = pairingCode.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${targetDeviceName} 연결") },
        text = {
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { value ->
                    pairingCode = value.filter(Char::isDigit).take(4)
                },
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
