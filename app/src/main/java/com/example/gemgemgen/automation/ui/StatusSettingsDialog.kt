package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus

@Composable
internal fun StatusSettingsDialog(
    status: EnvironmentStatus,
    setupInfo: EnvironmentSetupInfo,
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
                StatusRow("ChatGPT 앱", status.isChatGptInstalled)
                StatusRow("접근성 서비스", status.isAccessibilityServiceEnabled)
                StatusRow("WRITE_SECURE_SETTINGS", status.hasWriteSecureSettingsPermission)
                StatusRow("wildcard 폴더", status.isWildcardDirectoryAccessible)
                StatusRow("wildcard 편집 권한", status.isWildcardDirectoryWritable)
                StatusRow("프롬프트", hasPromptTemplate)

                if (!status.isAccessibilityServiceEnabled) {
                    Button(onClick = onOpenAccessibilitySettings) {
                        Text("접근성 설정 열기")
                    }
                }

                OutlinedButton(onClick = onSelectWildcardFolder) {
                    Text("wildcard 폴더 선택")
                }

                if (!status.hasWriteSecureSettingsPermission && setupInfo.adbGrantCommand.isNotBlank()) {
                    Text(
                        text = "ADB 권한 명령어:\n${setupInfo.adbGrantCommand}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "wildcard 폴더: ${setupInfo.wildcardDirectoryPath.ifBlank { "선택 안 됨" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Null Keyboard 전환 대상: ${setupInfo.nullKeyboardTargetImeId}",
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

