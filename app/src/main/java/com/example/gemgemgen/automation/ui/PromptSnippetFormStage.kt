// 역할: 메인 단축어 및 다중 추가 단축어를 포함한 프롬프트 상용구의 신규 등록 및 기존 상용구 수정을 위한 입력 폼과 본문 붙여넣기/가져오기 툴바 UI를 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors

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
    val shortcutState = rememberSyncedTextFieldState(
        value = shortcutInput,
        onValueChange = onShortcutChange,
        resetKey = editingSnippet?.id
    )
    val categoryState = rememberSyncedTextFieldState(
        value = categoryInput,
        onValueChange = onCategoryChange,
        resetKey = editingSnippet?.id
    )
    val contentState = rememberSyncedTextFieldState(
        value = contentInput,
        onValueChange = onContentChange,
        resetKey = editingSnippet?.id
    )
    val currentShortcut = shortcutState.text.toString().trim()
    val currentCategory = categoryState.text.toString().trim()
    val isShortcutDuplicate = currentShortcut.isNotBlank() && existingShortcuts.contains(currentShortcut.lowercase())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "카테고리 (선택)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                if (currentCategory.isNotEmpty()) {
                    Surface(
                        onClick = { onCategoryChange("") },
                        shape = RoundedCornerShape(6.dp),
                        color = AppTheme.colors.insetBed
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
            }

            OutlinedTextField(
                state = categoryState,
                label = { Text("카테고리명 (예: 스타일, 조명 · 비워두면 미분류)") },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation {
                    val noSpaces = asCharSequence().filterNot { it.isWhitespace() }.toString()
                    if (asCharSequence().toString() != noSpaces) {
                        replace(0, length, noSpaces)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
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
                                if (isSelected) {
                                    onCategoryChange("")
                                } else {
                                    onCategoryChange(cat)
                                }
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
                modifier = Modifier.fillMaxWidth(),
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
                label = { Text("호출 단축어 (예: ABC)") },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation {
                    val noSpaces = asCharSequence().filterNot { it.isWhitespace() }.toString()
                    if (asCharSequence().toString() != noSpaces) {
                        replace(0, length, noSpaces)
                    }
                },
                isError = isShortcutDuplicate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors()
            )
            if (isShortcutDuplicate) {
                Text(
                    text = "이미 등록된 단축어입니다. 다른 단축어를 입력해 주세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }

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
                        val isExistingDuplicate = trimmedAlias.isNotBlank() && existingShortcuts.contains(trimmedAlias.lowercase())
                        val hasError = isSelfDuplicate || isExistingDuplicate

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = alias,
                                onValueChange = { newValue ->
                                    val filtered = newValue.filterNot { it.isWhitespace() }
                                    val updated = additionalShortcutsInput.toMutableList()
                                    updated[index] = filtered
                                    onAdditionalShortcutsChange(updated)
                                },
                                label = { Text("추가 단축어 ${index + 1} (예: 가나다)") },
                                singleLine = true,
                                isError = hasError,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = appTextFieldColors()
                            )

                            IconButton(
                                onClick = {
                                    val updated = additionalShortcutsInput.toMutableList()
                                    updated.removeAt(index)
                                    onAdditionalShortcutsChange(updated)
                                },
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

                        if (hasError) {
                            val errorText = when {
                                trimmedAlias.equals(currentShortcut, ignoreCase = true) -> "메인 단축어와 중복됩니다."
                                isExistingDuplicate -> "'$trimmedAlias'은(는) 이미 등록된 단축어입니다."
                                else -> "추가 단축어 목록 내에 중복된 단어가 있습니다."
                            }
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. 치환될 프롬프트 문구 입력 & 툴바
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    onContentChange = onContentChange
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
                    .heightIn(min = 90.dp, max = 150.dp),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors(),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6)
            )
        }

        // 에러 메시지
        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. 하단 액션 버튼 (취소 / 저장)
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
