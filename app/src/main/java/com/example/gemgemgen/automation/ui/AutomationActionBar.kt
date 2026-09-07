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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.OnRemoteStartGreenDark
import com.example.gemgemgen.ui.theme.RemoteStartGreen

@Composable
internal fun AutomationActionBar(
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    canRun: Boolean,
    isRunning: Boolean,
    automationState: AutomationRunState,
    isRemoteSendMode: Boolean = false,
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

    NeuCard(
        modifier = modifier.fillMaxWidth(),
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
            Box(
                modifier = Modifier.width(72.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isRunning) {
                    val startBtnColor = if (isRemoteSendMode) RemoteStartGreen else AppTheme.colors.primary
                    Button(
                        onClick = onRunMvp,
                        enabled = canRun,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = startBtnColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .shadow(
                                elevation = if (canRun) 4.dp else 0.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = startBtnColor.copy(alpha = 0.4f),
                                spotColor = startBtnColor.copy(alpha = 0.3f)
                            )
                    ) {
                        if (isRemoteSendMode) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi 송신",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = "시작",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onCancelAutomation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                                spotColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            )
                    ) {
                        Text(
                            text = "중지",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            RepeatCountStepper(
                repeatCountText = repeatCountText,
                onRepeatCountChange = onRepeatCountChange
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
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
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        AppTheme.colors.textSecondary
                    }
                )
            }
        }
    }
}

@Composable
private fun ProgressBadge(
    currentIndex: Int,
    totalCount: Int,
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
            text = "[$currentIndex/$totalCount]",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
