// 역할: 에디터 헤더용 컴팩트 자동화 모드(일반/송신/수신) 3단 토글 및 수신 모드 하단 고정 상태 바를 표시합니다.
package com.example.gemgemgen.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed

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
        backgroundColor = AppTheme.colors.insetBed
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
 * 수신 모드일 때 시작 바 대신 화면 하단에 고정 표시되는 수신 대기/연결 상태 카드.
 */
@Composable
fun AutomationReceiverBottomBar(
    status: RemoteAutomationStatus,
    modifier: Modifier = Modifier
) {
    val message = resolveConnectionText(AutomationMode.RECEIVER, status)

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
