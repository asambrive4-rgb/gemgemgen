// 역할: 에디터 상단에 펼쳐져 프리셋(세트) 전환, 위치 태그(`{{실험}}`) 원터치 삽입, 2열 무테두리 그리드 기반 실험 프롬프트 번호 선택(기본 4개 상시 노출, 초과 시 세로 스크롤) 및 롱탭 관리를 지원하는 실험(연금술) 바 UI입니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.PromptExperimentItem
import com.example.gemgemgen.automation.domain.PromptExperimentPreset
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuDropdownMenu
import com.example.gemgemgen.ui.theme.NeuDropdownMenuDivider
import com.example.gemgemgen.ui.theme.NeuInsetBed

@Composable
internal fun PromptExperimentBar(
    presets: List<PromptExperimentPreset>,
    activePreset: PromptExperimentPreset,
    selectedItemId: String?,
    hasExperimentTagInPrompt: Boolean,
    experimentBlockReason: String?,
    onSelectPreset: (String) -> Unit,
    onSelectItem: (String?) -> Unit,
    onInsertTag: () -> Unit,
    onOpenManageDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeuInsetBed(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1행: 상단 큼직한 컨트롤 버튼 바 (세트 선택 드롭다운 | 위치 태그 삽입 | 관리)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExperimentPresetDropdownButton(
                    presets = presets,
                    activePreset = activePreset,
                    onSelectPreset = onSelectPreset,
                    onOpenManageDialog = onOpenManageDialog,
                    modifier = Modifier.weight(1.1f)
                )

                ExperimentTopPillButton(
                    text = if (hasExperimentTagInPrompt) "위치 태그됨" else "위치 넣기",
                    icon = if (hasExperimentTagInPrompt) Icons.Default.CheckCircle else Icons.Default.MyLocation,
                    isPrimary = !hasExperimentTagInPrompt,
                    onClick = onInsertTag,
                    contentDescription = "실험 위치 태그 넣기",
                    modifier = Modifier.weight(1f)
                )

                ExperimentTopPillButton(
                    text = "목록 관리",
                    icon = Icons.Default.Settings,
                    isPrimary = false,
                    onClick = onOpenManageDialog,
                    contentDescription = "실험 목록 관리",
                    modifier = Modifier.weight(0.95f)
                )
            }

            // 2행: 2열 조약돌 그리드 (기본 4개 프롬프트가 한눈에 항상 보이고, 5개 이상이면 부드럽게 스크롤)
            if (activePreset.items.isEmpty()) {
                Surface(
                    onClick = onOpenManageDialog,
                    shape = RoundedCornerShape(12.dp),
                    color = AppTheme.colors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "여기를 눌러 실험할 프롬프트 목록을 추가하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                val rows = remember(activePreset.items) {
                    activePreset.items.mapIndexed { idx, item -> (idx + 1) to item }.chunked(2)
                }
                // 2행(4개 항목)이 넉넉히 들어가는 높이(약 96dp), 5개 이상일 때 내부 스크롤
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 104.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowItems.forEach { (number, item) ->
                                val isSelected = item.id == selectedItemId
                                ExperimentGridChip(
                                    number = number,
                                    item = item,
                                    selected = isSelected,
                                    onClick = { onSelectItem(item.id) },
                                    onLongClick = onOpenManageDialog,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                // 홀수 개일 때 빈 칸에 [+ 항목 추가] 버튼 배치
                                ExperimentQuickAddGridChip(
                                    onClick = onOpenManageDialog,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3행: 실행 차단 안내 배지 (위치 태그 미삽입 또는 번호 미선택 시 노출, 선택 완료 시 자동 숨김)
            if (experimentBlockReason != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 $experimentBlockReason",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentPresetDropdownButton(
    presets: List<PromptExperimentPreset>,
    activePreset: PromptExperimentPreset,
    onSelectPreset: (String) -> Unit,
    onOpenManageDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = shape,
            color = AppTheme.colors.card,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = AppTheme.colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${activePreset.name} (${activePreset.items.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "실험 세트 선택",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        NeuDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEach { preset ->
                val isSelected = preset.id == activePreset.id
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${preset.name} (${preset.items.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectPreset(preset.id)
                    }
                )
            }
            NeuDropdownMenuDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        text = "+ 세트 추가 / 순서 변경...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.accent
                    )
                },
                onClick = {
                    expanded = false
                    onOpenManageDialog()
                }
            )
        }
    }
}

@Composable
private fun ExperimentTopPillButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    NeuButton(
        onClick = onClick,
        isPrimary = isPrimary,
        elevation = if (isPrimary) 4.dp else 2.dp,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier
            .height(40.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExperimentGridChip(
    number: Int,
    item: PromptExperimentItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val containerColor = if (selected) AppTheme.colors.primary else AppTheme.colors.card
    val contentColor = if (selected) AppTheme.colors.onPrimary else AppTheme.colors.textPrimary
    val badgeBgColor = if (selected) AppTheme.colors.onPrimary.copy(alpha = 0.22f) else AppTheme.colors.insetBed
    val badgeTextColor = if (selected) AppTheme.colors.onPrimary else AppTheme.colors.primary

    val singleLinePreview = remember(item.content) {
        item.content.replace("\n", " ").trim()
    }

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = badgeBgColor,
            contentColor = badgeTextColor
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }

        Text(
            text = singleLinePreview,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExperimentQuickAddGridChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(AppTheme.colors.card.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "실험 항목 추가",
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "항목 추가",
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
