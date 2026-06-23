package com.example.gemgemgen.automation.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState

@Composable
internal fun AutomationActionBar(
    repeatCountText: String,
    onRepeatCountChange: (String) -> Unit,
    onRunMvp: () -> Unit,
    onCancelAutomation: () -> Unit,
    canRun: Boolean,
    isRunning: Boolean,
    automationState: AutomationRunState,
    modifier: Modifier = Modifier
) {
    val statusText = AutomationUiText.statusText(automationState)
    val isError = automationState is AutomationRunState.Failure

    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        automationState is AutomationRunState.Running -> MaterialTheme.colorScheme.primary
        automationState is AutomationRunState.Success -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.width(68.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isRunning) {
                    Button(
                        onClick = onRunMvp,
                        enabled = canRun,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text("시작", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onCancelAutomation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text("중지", style = MaterialTheme.typography.labelMedium)
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
                        MaterialTheme.colorScheme.onSurfaceVariant
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
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(6.dp),
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

