// 역할: 자동화 화면 하단에 고정되어 시작·중지·원격 실행을 제어하는 액션 바 UI를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard

@Composable
internal fun AutomationBottomBar(
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    canRun: Boolean,
    isRunning: Boolean,
    automationState: AutomationRunState,
    isRemoteSendMode: Boolean = false,
    hasPreStartMemoryCleanup: Boolean = false,
    modifier: Modifier = Modifier
) {
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AutomationRunButton(
                canRun = canRun,
                isRunning = isRunning,
                onRunMvp = onRunMvp,
                onCancelAutomation = onCancelAutomation,
                isRemoteSendMode = isRemoteSendMode,
                hasPreStartMemoryCleanup = hasPreStartMemoryCleanup
            )

            Spacer(modifier = Modifier.width(6.dp))

            RepeatCountStepper(
                repeatCountText = repeatCountText,
                onRepeatCountChange = onRepeatCountChange
            )

            Spacer(modifier = Modifier.width(8.dp))

            AutomationStatusSection(
                automationState = automationState,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun AutomationStatusSection(
    automationState: AutomationRunState,
    modifier: Modifier = Modifier
) {
    val statusText = AutomationUiText.statusText(automationState)
    val isError = automationState is AutomationRunState.Failure

    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        automationState is AutomationRunState.Running -> AppTheme.colors.primary
        automationState is AutomationRunState.Success -> AppTheme.colors.primary
        else -> AppTheme.colors.cardBorder
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.graphicsLayer()
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = RoundedCornerShape(50),
            color = dotColor
        ) {}

        if (automationState is AutomationRunState.Running &&
            automationState.currentIndex != null &&
            automationState.totalCount != null
        ) {
            ProgressBadge(
                currentIndex = automationState.currentIndex,
                totalCount = automationState.totalCount
            )
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                AppTheme.colors.textSecondary
            }
        )
    }
}

@Composable
internal fun AutomationProgressBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AppTheme.colors.primary.copy(alpha = 0.15f),
        contentColor = AppTheme.colors.primary,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ProgressBadge(
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    AutomationProgressBadge(
        text = "[$currentIndex/$totalCount]",
        modifier = modifier
    )
}

@Composable
internal fun AutomationRunButton(
    canRun: Boolean,
    isRunning: Boolean,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    isRemoteSendMode: Boolean = false,
    hasPreStartMemoryCleanup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonShape = RoundedCornerShape(13.dp)
    val buttonModifier = modifier
        .width(88.dp)
        .height(42.dp)

    if (!isRunning) {
        NeuButton(
            onClick = onRunMvp,
            enabled = canRun,
            isPrimary = true,
            elevation = 3.dp,
            shape = buttonShape,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            modifier = buttonModifier
        ) {
            if (isRemoteSendMode) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wi-Fi 송신",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "시작",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = "시작",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (hasPreStartMemoryCleanup) {
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = "시작 전 메모리 정리 예정",
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    } else {
        NeuButton(
            onClick = onCancelAutomation,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            elevation = 3.dp,
            shape = buttonShape,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            modifier = buttonModifier
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "중지",
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "중지",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
