// 역할: 상용구 카테고리의 추가, 이름 일괄 변경, 롱프레스 드래그 순서 변경 및 안전 삭제(미분류 보존) 화면을 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuPillButton
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
internal fun SnippetManageCategoriesContent(
    categories: List<String>,
    snippets: List<PromptSnippet>,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (oldCategory: String, newCategory: String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onMoveCategory: (String, Int) -> Unit,
    onReorderCategories: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }
    var editingTarget by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var confirmingDeleteCategory by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }

    val dragState = rememberReorderableListDragState(
        items = categories,
        rowHeightPx = rowHeightPx,
        keyMatches = { item, key -> item.equals(key, ignoreCase = true) },
        onReorderCommitted = onReorderCategories
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 상단 헤더 & 목록으로 돌아가기 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "🏷️ 카테고리 관리",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "순서 변경 · 이름 일괄 변경 · 삭제 시 상용구는 미분류로 보존",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = AppTheme.colors.textSecondary
                )
            }

            NeuButton(
                onClick = onBack,
                isPrimary = false,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "보관함",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 새 카테고리 추가 또는 이름 수정 폼
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val inputValue = if (editingTarget != null) editingName else newCategoryName
            OutlinedTextField(
                value = inputValue,
                onValueChange = { raw ->
                    val filtered = raw.filterNot { it.isWhitespace() }
                    if (editingTarget != null) {
                        editingName = filtered
                    } else {
                        newCategoryName = filtered
                    }
                },
                placeholder = {
                    Text(
                        text = if (editingTarget != null) {
                            "'${editingTarget}'의 새 이름 입력..."
                        } else {
                            "새 카테고리 이름 (예: 스타일, 카메라, 조명)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(10.dp),
                colors = appTextFieldColors(),
                modifier = Modifier.weight(1f)
            )

            if (editingTarget != null) {
                NeuButton(
                    onClick = {
                        val target = editingTarget ?: return@NeuButton
                        val trimmed = editingName.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameCategory(target, trimmed)
                        }
                        editingTarget = null
                        editingName = ""
                    },
                    isPrimary = true,
                    enabled = editingName.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("변경", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                NeuButton(
                    onClick = {
                        editingTarget = null
                        editingName = ""
                    },
                    isPrimary = false,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("취소", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                NeuButton(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddCategory(trimmed)
                            newCategoryName = ""
                        }
                    },
                    isPrimary = true,
                    enabled = newCategoryName.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("+ 추가", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 카테고리 목록
        if (dragState.items.isEmpty()) {
            NeuInsetBed(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = AppTheme.colors.insetBed
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "등록된 카테고리가 없습니다",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "위 입력창에서 카테고리를 미리 만들어 두거나 상용구 등록 시 추가할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        } else {
            NeuInsetBed(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = AppTheme.colors.insetBed
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            state = rememberScrollState(),
                            enabled = !dragState.isDragging
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dragState.items.forEachIndexed { index, category ->
                        key(category.lowercase()) {
                            val isDragging = dragState.isDraggingItem(category)
                            val isEditingThis = editingTarget?.equals(category, ignoreCase = true) == true
                            val isConfirmingDelete = confirmingDeleteCategory?.equals(category, ignoreCase = true) == true
                            val count = snippets.count { it.category.trim().equals(category, ignoreCase = true) }
                            val number = index + 1

                            val currentOnDragStart by rememberUpdatedState { dragState.startDrag(category) }
                            val currentOnDrag by rememberUpdatedState { deltaY: Float -> dragState.onDrag(deltaY) }
                            val currentOnDragEnd by rememberUpdatedState { dragState.endDrag() }
                            val currentOnDragCancel by rememberUpdatedState { dragState.cancelDrag() }

                            NeuCard(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = when {
                                    isDragging -> AppTheme.colors.accent.copy(alpha = 0.14f)
                                    isEditingThis -> AppTheme.colors.primary.copy(alpha = 0.1f)
                                    else -> AppTheme.colors.card
                                },
                                elevation = if (isDragging) 8.dp else 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragState.dragAccumulatedY else 0f
                                        if (isDragging) {
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                        }
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .pointerInput(category.lowercase()) {
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
                                                    contentDescription = "$category 순서 드래그 변경",
                                                    tint = if (isDragging) AppTheme.colors.accent else AppTheme.colors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "$number. $category",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = "소속 상용구 ${count}개",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = AppTheme.colors.textSecondary
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                                        ) {
                                            IconButton(
                                                onClick = { onMoveCategory(category, -1) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "위로 이동",
                                                    tint = if (index > 0) AppTheme.colors.textSecondary else AppTheme.colors.textSecondary.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onMoveCategory(category, 1) },
                                                enabled = index < dragState.items.lastIndex,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "아래로 이동",
                                                    tint = if (index < dragState.items.lastIndex) AppTheme.colors.textSecondary else AppTheme.colors.textSecondary.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingTarget = category
                                                    editingName = category
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "카테고리 이름 수정",
                                                    tint = AppTheme.colors.accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    confirmingDeleteCategory = if (isConfirmingDelete) null else category
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "카테고리 삭제",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = isConfirmingDelete,
                                        enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                                        exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (count > 0) {
                                                        "삭제할까요? (상용구 ${count}개는 '미분류'로 유지됩니다)"
                                                    } else {
                                                        "'$category' 카테고리를 삭제할까요?"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    NeuPillButton(
                                                        onClick = { confirmingDeleteCategory = null },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "취소",
                                                            fontSize = 11.sp,
                                                            color = AppTheme.colors.textSecondary
                                                        )
                                                    }
                                                    NeuPillButton(
                                                        onClick = {
                                                            confirmingDeleteCategory = null
                                                            if (editingTarget?.equals(category, ignoreCase = true) == true) {
                                                                editingTarget = null
                                                                editingName = ""
                                                            }
                                                            onDeleteCategory(category)
                                                        },
                                                        contentColor = MaterialTheme.colorScheme.error,
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "삭제",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
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
            }
        }
    }
}
