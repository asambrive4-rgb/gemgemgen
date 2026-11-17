// 역할: 프롬프트 실험(연금술) 다이얼로그의 메인 호스트 화면으로, 세트 선택·실험 항목 원터치 복사 및 목록 관리·서브 단계 화면들과의 단일 모달 호스트(Single Dialog Host) 전환을 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.gemgemgen.core.android.rememberClipboardGateway
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuModalDialogFrame
import com.example.gemgemgen.ui.theme.appTextFieldColors

private sealed interface ExperimentDialogSubView {
    data object MainList : ExperimentDialogSubView
    data class EditItem(val item: PromptExperimentItem, val number: Int) : ExperimentDialogSubView
    data object ConfirmResetPreset : ExperimentDialogSubView
    data class MultilinePasteChoice(
        val rawText: String,
        val splitLines: List<String>
    ) : ExperimentDialogSubView
    data class ManagePresets(
        val editingPreset: PromptExperimentPreset? = null
    ) : ExperimentDialogSubView
}

@Composable
internal fun PromptExperimentDialog(
    presets: List<PromptExperimentPreset>,
    activePresetId: String,
    selectedItemId: String?,
    onSelectPreset: (String) -> Unit,
    onSelectItem: (String?) -> Unit,
    onAddPreset: (String) -> Unit,
    onUpdatePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onMovePreset: (String, Int) -> Unit = { _, _ -> },
    onReorderPresets: (Int, Int) -> Unit = { _, _ -> },
    onResetPresetItems: (String) -> Unit,
    onAddItem: (String, String) -> Unit,
    onAddItemsBatch: (String, List<String>) -> Unit,
    onUpdateItem: (String, String, String) -> Unit,
    onDeleteItem: (String, String) -> Unit,
    onMoveItem: (String, String, Int) -> Unit = { _, _, _ -> },
    onReorderItems: (String, Int, Int) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit
) {
    val clipboardGateway = rememberClipboardGateway()
    val activePreset = presets.firstOrNull { it.id == activePresetId }
        ?: presets.firstOrNull()
        ?: PromptExperimentPolicy.defaultPreset()

    var subView by remember { mutableStateOf<ExperimentDialogSubView>(ExperimentDialogSubView.MainList) }
    var newItemInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    NeuModalDialogFrame(onDismiss = onDismiss) {
        // 1. 공통 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppTheme.colors.accent.copy(alpha = 0.16f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = AppTheme.colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "프롬프트 실험 (연금술)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "세트·순서를 자유롭게 바꾸고 번호만 골라 실험합니다",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Single Dialog Host 내부 AnimatedContent 전환 (깜빡임 제로)
        AnimatedContent(
            targetState = subView,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
            },
            contentKey = { it::class },
            label = "ExperimentDialogSubViewCrossfade"
        ) { currentSubView ->
            when (currentSubView) {
                ExperimentDialogSubView.MainList -> {
                    ExperimentMainListContent(
                        presets = presets,
                        activePreset = activePreset,
                        selectedItemId = selectedItemId,
                        newItemInput = newItemInput,
                        statusMessage = statusMessage,
                        clipboardGateway = clipboardGateway,
                        onNewItemInputChange = {
                            newItemInput = it
                            statusMessage = ""
                        },
                        onSelectPreset = { presetId ->
                            onSelectPreset(presetId)
                            statusMessage = ""
                        },
                        onSelectItem = onSelectItem,
                        onOpenManagePresets = {
                            statusMessage = ""
                            subView = ExperimentDialogSubView.ManagePresets()
                        },
                        onRequestResetPreset = {
                            statusMessage = ""
                            subView = ExperimentDialogSubView.ConfirmResetPreset
                        },
                        onAddSingleItem = { content ->
                            val trimmed = content.trim()
                            if (trimmed.isEmpty()) {
                                statusMessage = "추가할 실험 문구를 입력해주세요."
                            } else {
                                onAddItem(activePreset.id, trimmed)
                                newItemInput = ""
                                statusMessage = ""
                            }
                        },
                        onRequestMultilinePasteChoice = { raw, lines ->
                            subView = ExperimentDialogSubView.MultilinePasteChoice(
                                rawText = raw,
                                splitLines = lines
                            )
                        },
                        onEmptyClipboardWarning = {
                            statusMessage = "클립보드가 비어 있습니다."
                        },
                        onReorderItems = { from, to ->
                            onReorderItems(activePreset.id, from, to)
                        },
                        onEditItemClick = { item, number ->
                            statusMessage = ""
                            subView = ExperimentDialogSubView.EditItem(item = item, number = number)
                        },
                        onDeleteItemClick = { itemId ->
                            onDeleteItem(activePreset.id, itemId)
                        }
                    )
                }

                is ExperimentDialogSubView.EditItem -> {
                    ExperimentEditItemContent(
                        number = currentSubView.number,
                        item = currentSubView.item,
                        clipboardGateway = clipboardGateway,
                        onCancel = { subView = ExperimentDialogSubView.MainList },
                        onSave = { updatedText ->
                            onUpdateItem(activePreset.id, currentSubView.item.id, updatedText)
                            subView = ExperimentDialogSubView.MainList
                        }
                    )
                }

                ExperimentDialogSubView.ConfirmResetPreset -> {
                    ExperimentResetConfirmContent(
                        presetName = activePreset.name,
                        itemCount = activePreset.items.size,
                        onCancel = { subView = ExperimentDialogSubView.MainList },
                        onConfirmReset = {
                            onResetPresetItems(activePreset.id)
                            subView = ExperimentDialogSubView.MainList
                            statusMessage = "'${activePreset.name}' 세트의 항목을 초기화했습니다."
                        }
                    )
                }

                is ExperimentDialogSubView.MultilinePasteChoice -> {
                    ExperimentMultilinePasteChoiceContent(
                        lineCount = currentSubView.splitLines.size,
                        previewLines = currentSubView.splitLines.take(4),
                        onSplitRegister = {
                            onAddItemsBatch(activePreset.id, currentSubView.splitLines)
                            newItemInput = ""
                            statusMessage = "${currentSubView.splitLines.size}개 항목으로 나누어 등록했습니다."
                            subView = ExperimentDialogSubView.MainList
                        },
                        onSingleRegister = {
                            onAddItem(activePreset.id, currentSubView.rawText.trim())
                            newItemInput = ""
                            statusMessage = "여러 줄을 단일 항목 1개로 등록했습니다."
                            subView = ExperimentDialogSubView.MainList
                        },
                        onCancel = {
                            subView = ExperimentDialogSubView.MainList
                        }
                    )
                }

                is ExperimentDialogSubView.ManagePresets -> {
                    ExperimentManagePresetsContent(
                        presets = presets,
                        activePreset = activePreset,
                        initialEditingPreset = currentSubView.editingPreset,
                        onSelectPreset = onSelectPreset,
                        onAddPreset = onAddPreset,
                        onUpdatePreset = onUpdatePreset,
                        onDeletePreset = onDeletePreset,
                        onMovePreset = onMovePreset,
                        onReorderPresets = onReorderPresets,
                        onBack = { subView = ExperimentDialogSubView.MainList }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentMainListContent(
    presets: List<PromptExperimentPreset>,
    activePreset: PromptExperimentPreset,
    selectedItemId: String?,
    newItemInput: String,
    statusMessage: String,
    clipboardGateway: ClipboardGateway,
    onNewItemInputChange: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSelectItem: (String?) -> Unit,
    onOpenManagePresets: () -> Unit,
    onRequestResetPreset: () -> Unit,
    onAddSingleItem: (String) -> Unit,
    onRequestMultilinePasteChoice: (rawText: String, splitLines: List<String>) -> Unit,
    onEmptyClipboardWarning: () -> Unit,
    onReorderItems: (fromIndex: Int, toIndex: Int) -> Unit,
    onEditItemClick: (PromptExperimentItem, Int) -> Unit,
    onDeleteItemClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }
    val dragState = rememberReorderableListDragState(
        items = activePreset.items,
        rowHeightPx = rowHeightPx,
        keyMatches = { item, key -> item.id == key },
        onReorderCommitted = onReorderItems
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1) 상단 큼직한 컨트롤 바 (높이 42dp): [현재 세트 드롭다운] | [세트 관리/순서] | [초기화]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DialogPresetDropdownButton(
                presets = presets,
                activePreset = activePreset,
                onSelectPreset = onSelectPreset,
                onOpenManagePresets = onOpenManagePresets,
                modifier = Modifier.weight(1.15f)
            )

            DialogTopActionButton(
                text = "세트 편집",
                icon = Icons.Default.FolderSpecial,
                onClick = onOpenManagePresets,
                modifier = Modifier.weight(0.95f)
            )

            DialogTopActionButton(
                text = "초기화",
                icon = Icons.Default.RestartAlt,
                enabled = activePreset.items.isNotEmpty(),
                isDanger = true,
                onClick = onRequestResetPreset,
                modifier = Modifier.weight(0.85f)
            )
        }

        // 2) 실험 문구 추가 & 큼직한 붙여넣기 추가 패널
        NeuInsetBed(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newItemInput,
                    onValueChange = onNewItemInputChange,
                    placeholder = {
                        Text(
                            text = "'${activePreset.name}'에 넣을 실험 문구 입력 (예: 야구장)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // 입력창 아래 큼직한 2분할 버튼 ([📋 클립보드 붙여넣기 추가] | [+ 직접 추가])
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuButton(
                        onClick = {
                            val clip = clipboardGateway.readText().trim()
                            if (clip.isEmpty()) {
                                onEmptyClipboardWarning()
                            } else if (PromptExperimentPolicy.isMultilinePasteCandidate(clip)) {
                                val lines = PromptExperimentPolicy.extractNonBlankLines(clip)
                                onRequestMultilinePasteChoice(clip, lines)
                            } else {
                                onAddSingleItem(clip)
                            }
                        },
                        isPrimary = false,
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(42.dp),
                        contentModifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "붙여넣기 추가",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    NeuButton(
                        onClick = { onAddSingleItem(newItemInput) },
                        isPrimary = true,
                        enabled = newItemInput.isNotBlank(),
                        elevation = 3.dp,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(42.dp),
                        contentModifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "문구 추가",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = AppTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3) 현재 세트의 번호별 실험 항목 리스트 (순서 위/아래 변경 + 수정 + 삭제 + 터치 선택)
        if (activePreset.items.isEmpty()) {
            NeuInsetBed(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "등록된 실험 프롬프트가 없습니다.\n위 입력창에 입력하거나 [붙여넣기 추가]를 눌러보세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = !dragState.isDragging
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dragState.items.forEachIndexed { index, item ->
                    key(item.id) {
                        val number = index + 1
                        val isSelected = item.id == selectedItemId
                        val isDragging = dragState.isDraggingItem(item.id)
                        ExperimentItemRowCard(
                            number = number,
                            item = item,
                            selected = isSelected,
                            isDragging = isDragging,
                            dragOffset = if (isDragging) dragState.dragAccumulatedY else 0f,
                            clipboardGateway = clipboardGateway,
                            onSelectClick = { onSelectItem(item.id) },
                            onDragStart = { dragState.startDrag(item.id) },
                            onDrag = { deltaY -> dragState.onDrag(deltaY) },
                            onDragEnd = { dragState.endDrag() },
                            onDragCancel = { dragState.cancelDrag(activePreset.items) },
                            onEditClick = { onEditItemClick(item, number) },
                            onDeleteClick = { onDeleteItemClick(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogPresetDropdownButton(
    presets: List<PromptExperimentPreset>,
    activePreset: PromptExperimentPreset,
    onSelectPreset: (String) -> Unit,
    onOpenManagePresets: () -> Unit,
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
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${activePreset.name} (${activePreset.items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "세트 선택",
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppTheme.colors.card)
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
                    onOpenManagePresets()
                }
            )
        }
    }
}

@Composable
internal fun DialogTopActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false,
    isSuccess: Boolean = false
) {
    val contentColor = when {
        !enabled -> AppTheme.colors.textSecondary.copy(alpha = 0.4f)
        isDanger -> MaterialTheme.colorScheme.error
        isSuccess -> AppTheme.colors.primary
        else -> AppTheme.colors.textPrimary
    }
    val iconColor = when {
        !enabled -> AppTheme.colors.textSecondary.copy(alpha = 0.4f)
        isDanger -> MaterialTheme.colorScheme.error
        isSuccess -> AppTheme.colors.primary
        else -> AppTheme.colors.primary
    }

    NeuButton(
        onClick = onClick,
        enabled = enabled,
        isPrimary = false,
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = modifier.height(42.dp),
        contentModifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExperimentItemRowCard(
    number: Int,
    item: PromptExperimentItem,
    selected: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    clipboardGateway: ClipboardGateway,
    onSelectClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val bgColor = when {
        isDragging -> AppTheme.colors.accent.copy(alpha = 0.14f)
        selected -> AppTheme.colors.primary.copy(alpha = 0.1f)
        else -> AppTheme.colors.card
    }
    val elevation = when {
        isDragging -> 8.dp
        selected -> 4.dp
        else -> 2.dp
    }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    NeuCard(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = bgColor,
        elevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                if (isDragging) {
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelectClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { currentOnDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDrag(dragAmount.y)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragCancel() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "${number}번 순서 변경 (길게 눌러 드래그)",
                        tint = if (isDragging) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isDragging -> AppTheme.colors.accent
                        selected -> AppTheme.colors.primary
                        else -> AppTheme.colors.insetBed
                    },
                    contentColor = when {
                        isDragging -> AppTheme.colors.onPrimary
                        selected -> AppTheme.colors.onPrimary
                        else -> AppTheme.colors.textPrimary
                    }
                ) {
                    Text(
                        text = "$number",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = if (selected || isDragging) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExperimentCopyIconButton(
                    textToCopy = item.content,
                    clipboardGateway = clipboardGateway,
                    contentDescriptionLabel = "${number}번 항목 복사",
                    iconSize = 17.dp
                )
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "${number}번 항목 수정",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "${number}번 항목 삭제",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

