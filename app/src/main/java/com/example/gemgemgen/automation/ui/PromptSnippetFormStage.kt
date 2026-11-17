// 역할: 메인 단축어 및 다중 추가 단축어를 포함한 프롬프트 상용구의 신규 등록·수정 입력 폼과 고정 높이 레이아웃 기반의 흔들림 없는(Jitter-Free) 본문 편집·붙여넣기 UI를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors

/** 상용구 본문 입력창의 줄바꿈 시 레이아웃 덜컹거림을 원천 차단하기 위한 고정 높이 및 줄 수 */
private val SnippetContentInputFieldHeight = 132.dp
private const val SnippetContentFixedLines = 5

/**
 * 전체 버퍼(0..length)를 덮어쓰지 않고 공백 문자 위치만 국소 제거하여
 * 중간 위치 타이핑 및 한글 조합(IME) 중 커서가 맨 끝으로 튀지 않게 유지하는 변환기.
 */
private val NoWhitespaceTransformation = InputTransformation {
    for (i in length - 1 downTo 0) {
        if (asCharSequence()[i].isWhitespace()) {
            replace(i, i + 1, "")
        }
    }
}

@Composable
internal fun SnippetFormHeaderBanner(
    isEditMode: Boolean,
    editingShortcut: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isEditMode) AppTheme.colors.accent.copy(alpha = 0.12f) else AppTheme.colors.primary.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Add,
                contentDescription = null,
                tint = if (isEditMode) AppTheme.colors.accent else AppTheme.colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (isEditMode) {
                    "기존 상용구 '${editingShortcut.orEmpty()}'의 내용을 수정합니다."
                } else {
                    "단축어나 카테고리를 입력창에 치면 아래 프롬프트로 자동 대치됩니다."
                },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.colors.textPrimary
            )
        }
    }
}

@Composable
internal fun SnippetFormContentToolbar(
    currentPromptText: String,
    isSelectionPrompt: Boolean,
    clipboardGateway: ClipboardGateway,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentPromptText.isNotBlank()) {
            Surface(
                onClick = { onContentChange(currentPromptText) },
                shape = RoundedCornerShape(6.dp),
                color = AppTheme.colors.primary.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Input,
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isSelectionPrompt) "선택 문구" else "현재 프롬프트",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.primary
                    )
                }
            }
        }

        Surface(
            onClick = {
                val clip = clipboardGateway.readText()
                if (clip.isNotBlank()) {
                    onContentChange(clip)
                }
            },
            shape = RoundedCornerShape(6.dp),
            color = AppTheme.colors.accent.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = AppTheme.colors.accent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "붙여넣기",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.accent
                )
            }
        }
    }
}

/**
 * 상용구 등록 / 수정 폼 컨텐츠
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SnippetFormContent(
    editingSnippet: PromptSnippet?,
    shortcutInput: String,
    additionalShortcutsInput: List<String> = emptyList(),
    categoryInput: String,
    contentInput: String,
    availableCategories: List<String>,
    errorMessage: String,
    currentPromptText: String,
    isSelectionPrompt: Boolean = false,
    existingShortcuts: List<String>,
    clipboardGateway: ClipboardGateway,
    onShortcutChange: (String) -> Unit,
    onAdditionalShortcutsChange: (List<String>) -> Unit,
    onCategoryChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val isEditMode = editingSnippet != null
    val resetKey = editingSnippet?.id ?: "new"
    val formScrollState = rememberScrollState()
    val contentScrollState = rememberScrollState()
    var isContentFocused by remember { mutableStateOf(false) }

    val shortcutState = rememberSyncedTextFieldState(
        value = shortcutInput,
        onValueChange = onShortcutChange,
        resetKey = resetKey
    )
    val categoryState = rememberSyncedTextFieldState(
        value = categoryInput,
        onValueChange = onCategoryChange,
        resetKey = resetKey
    )
    val contentState = rememberSyncedTextFieldState(
        value = contentInput,
        onValueChange = onContentChange,
        resetKey = resetKey
    )

    val existingShortcutsSet = remember(existingShortcuts) {
        existingShortcuts.map { it.lowercase() }.toSet()
    }
    val currentShortcut by remember(shortcutState) {
        derivedStateOf { shortcutState.text.toString().trim() }
    }
    val currentCategory by remember(categoryState) {
        derivedStateOf { categoryState.text.toString().trim() }
    }
    val hasCategory by remember(categoryState) {
        derivedStateOf { categoryState.text.isNotEmpty() }
    }
    val isShortcutDuplicate by remember(shortcutState, existingShortcutsSet) {
        derivedStateOf {
            val trimmed = shortcutState.text.toString().trim()
            trimmed.isNotEmpty() && existingShortcutsSet.contains(trimmed.lowercase())
        }
    }

    // 가상 키보드가 올라오며 폼 스크롤 영역의 maxValue가 커질 때, 본문 입력칸이 포커스된 상태라면
    // 같은 프레임에서 스크롤 위치를 동기화하여 2단계 덜컹거림 없이 한 번에 자리 잡도록 한다.
    LaunchedEffect(isContentFocused) {
        if (!isContentFocused) return@LaunchedEffect
        if (formScrollState.maxValue > 0) {
            formScrollState.scrollTo(formScrollState.maxValue)
        }
        var previousMax = formScrollState.maxValue
        snapshotFlow { formScrollState.maxValue }
            .collect { currentMax ->
                if (currentMax > previousMax) {
                    formScrollState.scrollTo(currentMax)
                }
                previousMax = currentMax
            }
    }

    var firstAdditionalDuplicateError: String? = null

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 상단·중단 입력 영역 (키보드 노출 시 내부 스크롤)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(formScrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 모드 안내 배너
            SnippetFormHeaderBanner(
                isEditMode = isEditMode,
                editingShortcut = editingSnippet?.shortcut
            )

            // 1. 카테고리 입력 (선택 사항) + 기존 카테고리 원터치 재사용 칩
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "카테고리 (선택)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Surface(
                        onClick = {
                            categoryState.setTextAndPlaceCursorAtEnd("")
                            onCategoryChange("")
                        },
                        enabled = hasCategory,
                        shape = RoundedCornerShape(6.dp),
                        color = AppTheme.colors.insetBed,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (hasCategory) 1f else 0f
                        }
                    ) {
                        Text(
                            text = "미분류로 비우기",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                OutlinedTextField(
                    state = categoryState,
                    placeholder = {
                        Text(
                            text = "카테고리명 (예: 스타일, 조명 · 비워두면 미분류)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = NoWhitespaceTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(),
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors()
                )

                if (availableCategories.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableCategories.forEach { cat ->
                            val isSelected = currentCategory.equals(cat, ignoreCase = true)
                            NeuPillChip(
                                text = cat,
                                selected = isSelected,
                                onClick = {
                                    val nextCategory = if (isSelected) "" else cat
                                    categoryState.setTextAndPlaceCursorAtEnd(nextCategory)
                                    onCategoryChange(nextCategory)
                                },
                                height = 28.dp,
                                textStyle = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // 2. 단축어 입력 (메인 단축어 + 우측 상단 '+ 단축어 추가' 버튼)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "단축어",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        if (additionalShortcutsInput.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppTheme.colors.insetBed
                            ) {
                                Text(
                                    text = "+${additionalShortcutsInput.size}개",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            onAdditionalShortcutsChange(additionalShortcutsInput + "")
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = AppTheme.colors.primary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "단축어 추가",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    state = shortcutState,
                    placeholder = {
                        Text(
                            text = "호출 단축어 (예: ABC)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = NoWhitespaceTransformation,
                    isError = isShortcutDuplicate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(),
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors()
                )

                // 추가 단축어 버튼을 눌렀을 때만 아래에 생성됨
                if (additionalShortcutsInput.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        additionalShortcutsInput.forEachIndexed { index, alias ->
                            val trimmedAlias = alias.trim()
                            val isSelfDuplicate = trimmedAlias.isNotBlank() &&
                                (trimmedAlias.equals(currentShortcut, ignoreCase = true) ||
                                    additionalShortcutsInput.indices.any { otherIdx ->
                                        otherIdx != index && additionalShortcutsInput[otherIdx].trim().equals(trimmedAlias, ignoreCase = true)
                                    })
                            val isExistingDuplicate = trimmedAlias.isNotBlank() && existingShortcutsSet.contains(trimmedAlias.lowercase())
                            val hasError = isSelfDuplicate || isExistingDuplicate

                            if (hasError && firstAdditionalDuplicateError == null) {
                                firstAdditionalDuplicateError = when {
                                    trimmedAlias.equals(currentShortcut, ignoreCase = true) -> "메인 단축어와 중복됩니다."
                                    isExistingDuplicate -> "'$trimmedAlias'은(는) 이미 등록된 단축어입니다."
                                    else -> "추가 단축어 목록 내에 중복된 단어가 있습니다."
                                }
                            }

                            key("$resetKey:alias:$index") {
                                AdditionalShortcutInputRow(
                                    index = index,
                                    alias = alias,
                                    hasError = hasError,
                                    resetKey = "$resetKey:alias:$index:${additionalShortcutsInput.size}",
                                    onAliasChange = { filtered ->
                                        if (additionalShortcutsInput.getOrNull(index) != filtered) {
                                            val updated = additionalShortcutsInput.toMutableList()
                                            if (index in updated.indices) {
                                                updated[index] = filtered
                                                onAdditionalShortcutsChange(updated)
                                            }
                                        }
                                    },
                                    onRemove = {
                                        val updated = additionalShortcutsInput.toMutableList()
                                        if (index in updated.indices) {
                                            updated.removeAt(index)
                                            onAdditionalShortcutsChange(updated)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. 치환될 프롬프트 문구 입력 & 툴바 (고정 높이 + 내부 스크롤로 줄바꿈 시 레이아웃 흔들림 원천 차단)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "치환될 프롬프트 본문",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )

                    SnippetFormContentToolbar(
                        currentPromptText = currentPromptText,
                        isSelectionPrompt = isSelectionPrompt,
                        clipboardGateway = clipboardGateway,
                        onContentChange = { newText ->
                            contentState.setTextAndPlaceCursorAtEnd(newText)
                            onContentChange(newText)
                        }
                    )
                }

                OutlinedTextField(
                    state = contentState,
                    placeholder = {
                        Text(
                            text = "단축어를 치면 입력창에 들어갈 실제 문구를 입력하세요.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SnippetContentInputFieldHeight)
                        .graphicsLayer()
                        .onFocusChanged { focusState ->
                            isContentFocused = focusState.isFocused
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors(),
                    lineLimits = TextFieldLineLimits.MultiLine(
                        minHeightInLines = SnippetContentFixedLines,
                        maxHeightInLines = SnippetContentFixedLines
                    ),
                    scrollState = contentScrollState
                )
            }
        }

        // 고정 높이 검증/에러 메시지 슬롯 (타이핑 중 경고 문구가 나타나고 사라져도 레이아웃 높이가 변하지 않음)
        val activeFeedbackMessage = when {
            errorMessage.isNotBlank() -> errorMessage
            isShortcutDuplicate -> "이미 등록된 단축어입니다. 다른 단축어를 입력해 주세요."
            firstAdditionalDuplicateError != null -> firstAdditionalDuplicateError.orEmpty()
            else -> ""
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (activeFeedbackMessage.isNotBlank()) {
                Text(
                    text = activeFeedbackMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. 하단 고정 액션 버튼 (취소 / 저장)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                isPrimary = false
            ) {
                Text(if (isEditMode) "수정 취소" else "목록으로")
            }

            NeuButton(
                onClick = onSave,
                modifier = Modifier.weight(1.3f),
                isPrimary = true
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(if (isEditMode) "수정사항 저장" else "상용구 등록")
                }
            }
        }
    }
}

@Composable
private fun AdditionalShortcutInputRow(
    index: Int,
    alias: String,
    hasError: Boolean,
    resetKey: Any,
    onAliasChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val aliasState = rememberSyncedTextFieldState(
        value = alias,
        onValueChange = { updated ->
            onAliasChange(updated.filterNot { it.isWhitespace() })
        },
        resetKey = resetKey
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            state = aliasState,
            placeholder = {
                Text(
                    text = "추가 단축어 ${index + 1} (예: 가나다)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp
                )
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = NoWhitespaceTransformation,
            isError = hasError,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer(),
            shape = RoundedCornerShape(12.dp),
            colors = appTextFieldColors()
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "추가 단축어 삭제",
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
