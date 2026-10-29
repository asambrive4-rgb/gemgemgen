// 역할: 메인 화면 프롬프트 에디터 하단에 대표 계정의 제미나이 사용률(%·만료 자동 보정)과 70%/90% 주의·경고 컬러를 공통 NeuCard·NeuButton 기반 토스 스타일로 표시하고, 모달 진입 없이 즉시 새로고침하거나 다중 계정 모달을 여는 요약 바입니다.
package com.example.gemgemgen.usage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.ui.GeminiGradientLogo
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.usage.domain.GeminiUsageWarningLevel
import com.example.gemgemgen.usage.domain.MultiGeminiUsageState
import kotlinx.coroutines.delay

internal val MultiUsageCautionAmber = Color(0xFFE67E22)
internal val MultiUsageWarningRed = Color(0xFFD93025)

@Composable
internal fun resolveGeminiUsageNormalHighlightColor(): Color {
    return AppTheme.colors.primary
}

@Composable
internal fun resolveGeminiUsageWarningColor(level: GeminiUsageWarningLevel): Color {
    return when (level) {
        GeminiUsageWarningLevel.NORMAL -> resolveGeminiUsageNormalHighlightColor()
        GeminiUsageWarningLevel.CAUTION -> MultiUsageCautionAmber
        GeminiUsageWarningLevel.WARNING -> MultiUsageWarningRed
    }
}

/**
 * 프롬프트 에디터 하단에 붙는 1줄 요약 버튼.
 * 잔여 한도(0~100%), 만료(리셋)까지 남은 시간, 70%/90% 주의 배지를 토스 카드 스타일로 보여줍니다.
 */
@Composable
fun GeminiUsageSummaryButton(
    multiUsageState: MultiGeminiUsageState,
    onClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val warningLevel = multiUsageState.summaryWarningLevel(nowMillis)
    val isWarningOrCaution = warningLevel != GeminiUsageWarningLevel.NORMAL
    val accentColor = resolveGeminiUsageWarningColor(warningLevel)

    val badgeText = multiUsageState.summaryBadgeText(nowMillis)
    val resetTimeText = multiUsageState.summaryResetTimeText(nowMillis)
    val summaryButtonText = multiUsageState.summaryButtonText(nowMillis)

    val containerColor = when (warningLevel) {
        GeminiUsageWarningLevel.WARNING -> MultiUsageWarningRed.copy(alpha = 0.14f)
        GeminiUsageWarningLevel.CAUTION -> MultiUsageCautionAmber.copy(alpha = 0.13f)
        GeminiUsageWarningLevel.NORMAL -> AppTheme.colors.card
    }

    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuCard(
            shape = shape,
            backgroundColor = containerColor,
            elevation = 2.dp,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(shape)
                .clickable(onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = "Gemini 계정별 사용량 모달 열기: $summaryButtonText"
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        imageVector = GeminiGradientLogo,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )

                    Text(
                        text = multiUsageState.summaryAccountLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    badgeText?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    resetTimeText?.let { resetTime ->
                        Text(
                            text = resetTime,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary.copy(alpha = 0.88f),
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isWarningOrCaution) {
                        GeminiUsageWarningBadge(
                            warningLevel = warningLevel,
                            color = accentColor
                        )
                    }
                }

                if (multiUsageState.accountCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppTheme.colors.insetBed)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${multiUsageState.accountCount}계정",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        NeuButton(
            onClick = onRefreshClick,
            enabled = !multiUsageState.isAnyRefreshing,
            isPrimary = false,
            shape = shape,
            elevation = 2.dp,
            containerColor = AppTheme.colors.card,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(42.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Gemini 사용량 즉시 새로고침"
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (multiUsageState.isAnyRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun GeminiUsageWarningBadge(
    warningLevel: GeminiUsageWarningLevel,
    color: Color,
    modifier: Modifier = Modifier
) {
    val label = when (warningLevel) {
        GeminiUsageWarningLevel.CAUTION -> "주의"
        GeminiUsageWarningLevel.WARNING -> "경고"
        GeminiUsageWarningLevel.NORMAL -> return
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
