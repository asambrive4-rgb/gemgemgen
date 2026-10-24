// 역할: 다중 계정 모달의 수직 목록에 표시되는 컴팩트 구글 계정 사용량 카드(계정 전환 버튼, 사용불가 내림/복구 외부 원터치 토글, 실시간 초기화 만료 보정, 소진 Dim UI, 소진 뱃지, 별칭, 게이지, 당분간 사용 금지 메뉴)를 제공합니다.
package com.example.gemgemgen.usage.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.usage.domain.GeminiAccountGroup
import com.example.gemgemgen.usage.domain.GeminiAccountUsageEntry
import com.example.gemgemgen.usage.domain.GeminiQuotaExhaustionReason
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.domain.GeminiUsageWarningLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 다중 계정 수직 목록 모달의 개별 계정 카드 컴포넌트.
 *
 * 구성 요소:
 * 1. 계정 라벨/별칭 (예: "작업용 1", "개인 본계정") 및 갱신 시각 (긴 이메일은 숨김 처리)
 * 2. 5시간 한도 프로그레스 바 + 리셋 시각 및 남은 시간 ("14:00 (4시간 후)")
 * 3. 주간 한도 프로그레스 바 + 리셋 일시 및 남은 시간 ("10/20/14:00 (4일 후)")
 * 4. 70%/90% 위험 뱃지 (주의/경고)
 * 5. 개별 계정 [전환] 버튼, [새로고침] 아이콘, [계정 삭제] 메뉴 (더보기 메뉴 및 2단계 확인)
 */
@Composable
fun GeminiAccountRowCard(
    entry: GeminiAccountUsageEntry,
    isRepresentative: Boolean,
    onSelectRepresentative: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit,
    onEditAccount: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSwitchAccount: (() -> Unit)? = null,
    onMoveAccountGroup: ((GeminiAccountGroup) -> Unit)? = null,
    onToggleManualExhaustion: ((Boolean) -> Unit)? = null,
    onToggleAccountSuspension: ((Boolean) -> Unit)? = null,
    nowMillis: Long = remember { System.currentTimeMillis() },
    modifier: Modifier = Modifier
) {
    val displayedSuccess = entry.displayedSuccess
    val isLoading = entry.usageState is GeminiUsageState.Loading || entry.isRefreshing
    val fiveHourWarning = entry.fiveHourWarningLevel(nowMillis)
    val brandHighlightColor = resolveGeminiUsageNormalHighlightColor()
    val exhaustionReason = entry.quotaExhaustionReason(nowMillis)
    val isExhausted = exhaustionReason != null

    val cardBorderColor = when {
        isExhausted -> AppTheme.colors.cardBorder.copy(alpha = 0.25f)
        fiveHourWarning == GeminiUsageWarningLevel.WARNING -> MultiUsageWarningRed.copy(alpha = 0.65f)
        fiveHourWarning == GeminiUsageWarningLevel.CAUTION -> MultiUsageCautionAmber.copy(alpha = 0.65f)
        isRepresentative -> brandHighlightColor.copy(alpha = 0.7f)
        else -> AppTheme.colors.cardBorder
    }

    var showMenu by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }

    NeuCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelectRepresentative),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = if (isExhausted) AppTheme.colors.card.copy(alpha = 0.65f) else AppTheme.colors.card,
        borderColor = cardBorderColor,
        elevation = if (isExhausted) 0.dp else if (isRepresentative) 5.dp else 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isExhausted) 0.38f else 1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. 상단 1줄 컴팩트 헤더: 별칭, 대표 뱃지, 위험/소진 뱃지, 갱신 시각 + 우측 [전환], 새로고침/메뉴 아이콘
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.profile.displayAlias,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isRepresentative) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = brandHighlightColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, brandHighlightColor.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "대표",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = brandHighlightColor,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (exhaustionReason != null) {
                        GeminiQuotaExhaustionBadge(reason = exhaustionReason)
                    } else if (fiveHourWarning != GeminiUsageWarningLevel.NORMAL) {
                        GeminiUsageWarningBadge(
                            warningLevel = fiveHourWarning,
                            color = resolveGeminiUsageWarningColor(fiveHourWarning)
                        )
                    }

                    if (displayedSuccess != null && displayedSuccess.updatedAt > 0L) {
                        Text(
                            text = "· ${formatAccountUpdatedAtTime(displayedSuccess.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }
                }

                // 우측 액션: 개별 계정 [전환] 버튼, [새로고침] 아이콘 및 [계정 삭제] 메뉴
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onSwitchAccount != null) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = brandHighlightColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, brandHighlightColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable(onClick = onSwitchAccount)
                                .semantics {
                                    contentDescription = "${entry.profile.displayAlias} 계정 전환"
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = brandHighlightColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "전환",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = brandHighlightColor,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (onToggleManualExhaustion != null) {
                        val isSuspended = entry.profile.isSuspended
                        val isManuallyExhausted = entry.profile.isManuallyExhausted(nowMillis)
                        val buttonEnabled = !isSuspended
                        val arrowIcon = if (isManuallyExhausted) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                        val buttonDesc = when {
                            isSuspended -> "${entry.profile.displayAlias} 당분간 사용 금지 상태"
                            isManuallyExhausted -> "${entry.profile.displayAlias} 사용 가능으로 복구"
                            else -> "${entry.profile.displayAlias} 사용불가로 내리기"
                        }

                        Surface(
                            shape = CircleShape,
                            color = AppTheme.colors.insetBed,
                            border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .alpha(if (buttonEnabled) 1f else 0.38f)
                                .clickable(enabled = buttonEnabled) {
                                    onToggleManualExhaustion(!isManuallyExhausted)
                                }
                                .semantics {
                                    contentDescription = buttonDesc
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = arrowIcon,
                                    contentDescription = null,
                                    tint = if (isManuallyExhausted) brandHighlightColor else AppTheme.colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = AppTheme.colors.insetBed,
                        border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isLoading, onClick = onRefresh)
                            .semantics {
                                contentDescription = "${entry.profile.displayAlias} 사용량 새로고침"
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = brandHighlightColor
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = AppTheme.colors.textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Box {
                        Surface(
                            shape = CircleShape,
                            color = AppTheme.colors.insetBed,
                            border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { showMenu = true }
                                .semantics {
                                    contentDescription = "${entry.profile.displayAlias} 계정 메뉴"
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = AppTheme.colors.card,
                            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                            shadowElevation = 8.dp
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "별칭 수정 / 재로그인",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = AppTheme.colors.textSecondary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onEditAccount()
                                }
                            )
                            if (onMoveAccountGroup != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = AppTheme.colors.insetBorder.copy(alpha = 0.65f),
                                    thickness = 0.8.dp
                                )
                                val targetGroup = if (entry.profile.group == GeminiAccountGroup.MAIN) {
                                    GeminiAccountGroup.SUB
                                } else {
                                    GeminiAccountGroup.MAIN
                                }
                                val moveLabel = if (targetGroup == GeminiAccountGroup.SUB) "서브로 이동" else "메인으로 이동"
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = moveLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            tint = brandHighlightColor
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onMoveAccountGroup(targetGroup)
                                    }
                                )
                            }
                            if (onToggleAccountSuspension != null) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = AppTheme.colors.insetBorder.copy(alpha = 0.65f),
                                    thickness = 0.8.dp
                                )
                                val isSuspended = entry.profile.isSuspended
                                val suspendText = if (isSuspended) "사용 금지 해제" else "당분간 사용 금지"
                                val suspendIcon = if (isSuspended) Icons.Default.CheckCircle else Icons.Default.Block
                                val suspendColor = if (isSuspended) brandHighlightColor else MultiUsageWarningRed
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = suspendText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSuspended) AppTheme.colors.textPrimary else MultiUsageWarningRed
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = suspendIcon,
                                            contentDescription = null,
                                            tint = suspendColor
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onToggleAccountSuspension(!isSuspended)
                                    }
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = AppTheme.colors.insetBorder,
                                thickness = 1.dp
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "계정 삭제",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    isConfirmingDelete = true
                                }
                            )
                        }
                    }
                }
            }

            // 2. 게이지 및 상태 본문
            when (val currentState = entry.usageState) {
                is GeminiUsageState.Success -> {
                    AccountUsageGaugesSection(entry = entry, success = currentState, nowMillis = nowMillis)
                }
                is GeminiUsageState.AuthRequired -> {
                    if (displayedSuccess != null) {
                        AccountUsageGaugesSection(entry = entry, success = displayedSuccess, nowMillis = nowMillis)
                    }
                    AccountAuthRequiredBanner(onOpenLogin = onOpenLogin)
                }
                is GeminiUsageState.Error -> {
                    if (displayedSuccess != null) {
                        AccountUsageGaugesSection(entry = entry, success = displayedSuccess, nowMillis = nowMillis)
                    }
                    AccountErrorBanner(
                        message = currentState.message,
                        onRefresh = onRefresh,
                        onOpenLogin = onOpenLogin
                    )
                }
                GeminiUsageState.Loading -> {
                    if (displayedSuccess != null) {
                        AccountUsageGaugesSection(entry = entry, success = displayedSuccess, nowMillis = nowMillis)
                    } else {
                        AccountLoadingRow()
                    }
                }
            }

            // 3. 삭제 확인 바 (실수 방지 2단계 확인)
            AnimatedVisibility(
                visible = isConfirmingDelete,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "'${entry.profile.displayAlias}' 계정을 삭제할까요?",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { isConfirmingDelete = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("취소", fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                            }
                            TextButton(
                                onClick = {
                                    isConfirmingDelete = false
                                    onDeleteAccount()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("계정 삭제", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 할당량 소진 사유 뱃지 ([5시간 소진], [주간 소진]).
 */
@Composable
fun GeminiQuotaExhaustionBadge(
    reason: GeminiQuotaExhaustionReason,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MultiUsageWarningRed.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MultiUsageWarningRed.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = reason.badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MultiUsageWarningRed,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun AccountUsageGaugesSection(
    entry: GeminiAccountUsageEntry,
    success: GeminiUsageState.Success,
    nowMillis: Long
) {
    val fiveHourPercent = entry.effectiveFiveHourPercent(nowMillis)
    val weeklyPercent = entry.effectiveWeeklyPercent(nowMillis)

    val fiveHourResetText = GeminiUsageParser.formatCompactResetWithRemaining(
        rawText = success.fiveHourResetText,
        nowMillis = nowMillis,
        updatedAt = success.updatedAt,
        isFiveHourWindow = true
    )

    val weeklyResetText = GeminiUsageParser.formatCompactResetWithRemaining(
        rawText = success.weeklyResetText,
        nowMillis = nowMillis,
        updatedAt = success.updatedAt,
        isFiveHourWindow = false
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AccountUsageGaugeRow(
            title = "5시간",
            percent = fiveHourPercent,
            resetText = fiveHourResetText,
            warningLevel = GeminiUsageWarningLevel.fromPercent(fiveHourPercent),
            exhaustionBadgeText = if (fiveHourPercent >= 100) "5시간 소진" else null
        )

        AccountUsageGaugeRow(
            title = "주간",
            percent = weeklyPercent,
            resetText = weeklyResetText,
            warningLevel = GeminiUsageWarningLevel.fromPercent(weeklyPercent),
            exhaustionBadgeText = if (weeklyPercent >= 100) "주간 소진" else null
        )
    }
}

@Composable
private fun AccountUsageGaugeRow(
    title: String,
    percent: Int,
    resetText: String,
    warningLevel: GeminiUsageWarningLevel,
    exhaustionBadgeText: String? = null
) {
    val clampedPercent = percent.coerceIn(0, 100)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedPercent / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "AccountUsageGaugeProgress"
    )
    val gaugeColor = resolveGeminiUsageWarningColor(warningLevel)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = gaugeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, gaugeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "${clampedPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = gaugeColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (exhaustionBadgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MultiUsageWarningRed.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MultiUsageWarningRed.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = exhaustionBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MultiUsageWarningRed,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                } else if (warningLevel != GeminiUsageWarningLevel.NORMAL) {
                    GeminiUsageWarningBadge(
                        warningLevel = warningLevel,
                        color = gaugeColor
                    )
                }
            }

            Text(
                text = resetText,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        NeuInsetBed(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            shape = RoundedCornerShape(999.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(gaugeColor)
            )
        }
    }
}

@Composable
private fun AccountAuthRequiredBanner(
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.insetBed)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "세션이 만료되었거나 Google 로그인이 필요합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        NeuButton(
            onClick = onOpenLogin,
            isPrimary = true,
            shape = RoundedCornerShape(10.dp),
            contentModifier = Modifier.padding(PaddingValues(0.dp)),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Login,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "로그인",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AccountErrorBanner(
    message: String,
    onRefresh: () -> Unit,
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppTheme.colors.insetBed)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountCompactActionButton(
                label = "로그인",
                icon = Icons.Default.Login,
                onClick = onOpenLogin
            )
            AccountCompactActionButton(
                label = "재시도",
                icon = Icons.Default.Refresh,
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun AccountCompactActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val brandHighlightColor = resolveGeminiUsageNormalHighlightColor()
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.1.dp, AppTheme.colors.cardBorder),
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = brandHighlightColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AccountLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = resolveGeminiUsageNormalHighlightColor()
        )
        Text(
            text = "해당 계정의 사용량 한도를 조회하는 중…",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            fontSize = 11.sp
        )
    }
}

private val accountUpdatedAtFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm 갱신", Locale.KOREA)
}

private fun formatAccountUpdatedAtTime(updatedAt: Long): String {
    return runCatching {
        val formatter = accountUpdatedAtFormatter.get() ?: SimpleDateFormat("HH:mm 갱신", Locale.KOREA)
        formatter.format(Date(updatedAt))
    }.getOrDefault("")
}
