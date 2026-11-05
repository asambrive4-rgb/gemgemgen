// 역할: 프롬프트 실험실 모달 내부의 서브 단계 화면(개별 항목 수정, 세트 초기화 확인, 다중 줄 붙여넣기 선택, 실험 세트 관리)을 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.gemgemgen.automation.domain.PromptExperimentItem
import com.example.gemgemgen.automation.domain.PromptExperimentPolicy
import com.example.gemgemgen.automation.domain.PromptExperimentPreset
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ExperimentEditItemContent(
    number: Int,
    item: PromptExperimentItem,
    clipboardGateway: ClipboardGateway,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(item.id) { mutableStateOf(item.content) }
    var isCopied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✏️ ${number}번 실험 프롬프트 수정",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DialogTopActionButton(
                    text = if (isCopied) "복사됨" else "복사",
                    icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    enabled = text.isNotBlank(),
                    isSuccess = isCopied,
                    onClick = {
                        if (text.isNotBlank()) {
                            clipboardGateway.writeText(text)
                            isCopied = true
                            coroutineScope.launch {
                                delay(1500)
                                isCopied = false
                            }
                        }
                    },
                    modifier = Modifier.width(82.dp)
                )
                DialogTopActionButton(
                    text = "붙여넣기",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        val clip = clipboardGateway.readText()
                        if (clip.isNotBlank()) {
                            text = clip.trim()
                        }
                    },
                    modifier = Modifier.width(88.dp)
                )
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NeuButton(
                onClick = onCancel,
                isPrimary = false,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text("취소", fontWeight = FontWeight.SemiBold)
            }
            NeuButton(
                onClick = { onSave(text) },
                isPrimary = true,
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentModifier = Modifier.fillMaxWidth()
            ) {
                Text("저장", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun ExperimentResetConfirmContent(
    presetName: String,
    itemCount: Int,
    onCancel: () -> Unit,
    onConfirmReset: () -> Unit
) {
    NeuInsetBed(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "'$presetName' 세트를 초기화할까요?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "현재 세트에 등록된 실험 프롬프트 ${itemCount}개가 모두 삭제됩니다.\n다른 실험 세트(프리셋)의 데이터는 그대로 안전하게 유지됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeuButton(
                    onClick = onCancel,
                    isPrimary = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    Text("취소", fontWeight = FontWeight.SemiBold)
                }
                NeuButton(
                    onClick = onConfirmReset,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    Text("초기화 실행", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ExperimentMultilinePasteChoiceContent(
    lineCount: Int,
    previewLines: List<String>,
    onSplitRegister: () -> Unit,
    onSingleRegister: () -> Unit,
    onCancel: () -> Unit
) {
    NeuInsetBed(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "여러 줄 텍스트가 감지되었습니다 (${lineCount}줄)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "줄바꿈 기준으로 각각 1번, 2번... 별도 번호로 나누어 등록할까요, 아니면 줄바꿈을 유지한 단일 항목 1개로 등록할까요?",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTheme.colors.card)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                previewLines.forEachIndexed { idx, line ->
                    Text(
                        text = "${idx + 1}. $line",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (lineCount > previewLines.size) {
                    Text(
                        text = "...외 ${lineCount - previewLines.size}줄",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeuButton(
                    onClick = onSplitRegister,
                    isPrimary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentModifier = Modifier.fillMaxWidth()
                ) {
                    Text("줄바꿈 단위로 나누어 등록 (${lineCount}개)", fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeuButton(
                        onClick = onSingleRegister,
                        isPrimary = false,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentModifier = Modifier.fillMaxWidth()
                    ) {
                        Text("단일 항목 1개로 등록", fontWeight = FontWeight.SemiBold)
                    }
                    NeuButton(
                        onClick = onCancel,
                        isPrimary = false,
                        modifier = Modifier
                            .weight(0.6f)
                            .height(44.dp),
                        contentModifier = Modifier.fillMaxWidth()
                    ) {
                        Text("취소", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExperimentManagePresetsContent(
    presets: List<PromptExperimentPreset>,
    activePreset: PromptExperimentPreset,
    initialEditingPreset: PromptExperimentPreset?,
    onSelectPreset: (String) -> Unit,
    onAddPreset: (String) -> Unit,
    onUpdatePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMovePreset: (String, Int) -> Unit,
    onReorderPresets: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var newPresetName by remember { mutableStateOf("") }
    var editingPreset by remember { mutableStateOf(initialEditingPreset) }
    var editingName by remember { mutableStateOf(initialEditingPreset?.name.orEmpty()) }

    val density = LocalDensity.current
    val presetRowHeightPx = with(density) { 56.dp.toPx() }
    val presetDragState = rememberReorderableListDragState(
        items = presets,
        rowHeightPx = presetRowHeightPx,
        keyMatches = { preset, key -> preset.id == key },
        onReorderCommitted = onReorderPresets
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🗂️ 실험 세트 추가 · 이름 · 순서 관리",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            DialogTopActionButton(
                text = "돌아가기",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                modifier = Modifier.width(108.dp)
            )
        }

        // 새 프리셋 추가 또는 이름 수정 폼
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = if (editingPreset != null) editingName else newPresetName,
                onValueChange = {
                    if (editingPreset != null) editingName = it else newPresetName = it
                },
                placeholder = {
                    Text(
                        text = if (editingPreset != null) "세트 이름 수정..." else "새 세트 이름 (예: 장소, 조명, 구도)",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(10.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.weight(1f)
            )

            if (editingPreset != null) {
                NeuButton(
                    onClick = {
                        val target = editingPreset ?: return@NeuButton
                        onUpdatePreset(target.id, editingName)
                        editingPreset = null
                        editingName = ""
                    },
                    isPrimary = true,
                    enabled = editingName.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("변경", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else {
                NeuButton(
                    onClick = {
                        onAddPreset(newPresetName)
                        newPresetName = ""
                    },
                    isPrimary = true,
                    enabled = newPresetName.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("+ 세트 추가", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = !presetDragState.isDragging
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presetDragState.items.forEachIndexed { index, preset ->
                key(preset.id) {
                    val isSelected = preset.id == activePreset.id
                    val isDragging = presetDragState.isDraggingItem(preset.id)
                    val number = index + 1
                    val currentOnPresetDragStart by rememberUpdatedState { presetDragState.startDrag(preset.id) }
                    val currentOnPresetDrag by rememberUpdatedState { deltaY: Float -> presetDragState.onDrag(deltaY) }
                    val currentOnPresetDragEnd by rememberUpdatedState { presetDragState.endDrag() }
                    val currentOnPresetDragCancel by rememberUpdatedState { presetDragState.cancelDrag(presets) }

                    NeuCard(
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = when {
                            isDragging -> AppTheme.colors.accent.copy(alpha = 0.14f)
                            isSelected -> AppTheme.colors.primary.copy(alpha = 0.1f)
                            else -> AppTheme.colors.card
                        },
                        elevation = if (isDragging) 8.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) presetDragState.dragAccumulatedY else 0f
                                if (isDragging) {
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPreset(preset.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 좌측: 3단 가로줄 드래그 핸들 (롱프레스)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .pointerInput(preset.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { currentOnPresetDragStart() },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    currentOnPresetDrag(dragAmount.y)
                                                },
                                                onDragEnd = { currentOnPresetDragEnd() },
                                                onDragCancel = { currentOnPresetDragCancel() }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "${preset.name} 순서 변경 (길게 눌러 드래그)",
                                        tint = if (isDragging) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "$number. ${preset.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "실험 항목 ${preset.items.size}개",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        editingPreset = preset
                                        editingName = preset.name
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "세트 이름 수정",
                                        tint = AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                if (presetDragState.items.size > 1) {
                                    IconButton(
                                        onClick = { onDeletePreset(preset.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "세트 삭제",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
