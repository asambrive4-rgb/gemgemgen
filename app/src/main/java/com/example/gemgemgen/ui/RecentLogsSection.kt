package com.example.gemgemgen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunLog
import com.example.gemgemgen.automation.domain.AutomationTargetApp

@Composable
internal fun RecentLogsSection(
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
            if (log.targetApp.isNotBlank()) {
                Text(
                    text = "대상 앱: ${
                        AutomationTargetApp.fromStorageValue(log.targetApp).displayName
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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

