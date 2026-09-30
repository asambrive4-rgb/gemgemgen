// 역할: 와일드카드 세트 목록과 단어 목록을 관리하고 분류 작업을 수행하는 메인 탭 화면 UI를 제공합니다.
package com.example.gemgemgen.wildcard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors
import com.example.gemgemgen.wildcard.domain.WildcardTextFile

@Composable
internal fun WildcardScreen(
    uiState: WildcardUiState,
    environmentStatus: EnvironmentStatus,
    environmentSetupInfo: EnvironmentSetupInfo,
    actions: WildcardScreenActions,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val editingText = uiState.editor.editingText
    val selectedFile = uiState.editor.selectedFile
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val isDirty = uiState.editor.hasUnsavedChanges
    val fileItems = remember(uiState.files, selectedFile?.id, isDirty) {
        uiState.files.map { file ->
            val isSelected = selectedFile?.id == file.id
            WildcardFileUiItem(
                file = file,
                displayName = if (isSelected && isDirty) "${file.fileName} *" else file.fileName,
                isSelected = isSelected
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.canvas)
            .imePadding()
            .clearFocusOnOutsideTap {
                focusManager.clearFocus(force = true)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1구역: Top Strip (파일 탭 목록 + 새 파일/삭제 버튼)
            FileTabsSection(
                fileItems = fileItems,
                onFileClick = actions::selectFile,
                canCreateFile = uiState.canCreateFile,
                canDelete = uiState.canDelete,
                onRequestNewFile = actions::requestNewFile,
                onRequestDelete = actions::requestDeleteSelectedFile
            )

            // 2구역: 에디터 카드 (헤더 + 텍스트 입력창 일체화)
            val fileLabel = selectedFile?.fileName?.removeSuffix(".txt") ?: "선택된 파일 없음"
            val statusText = if (isDirty) "$fileLabel *" else fileLabel
            val editorCardShape = RoundedCornerShape(20.dp)

            NeuCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = editorCardShape,
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 에디터 일체형 헤더 (부드러운 톤온톤 배경 띠)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                AppTheme.colors.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.isLineSelectionMode) {
                                "선택 ${uiState.selectedLineIndices.size}/${uiState.selectableLines.size}"
                            } else {
                                statusText
                            },
                            color = AppTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedFile != null && !uiState.isLineSelectionMode) {
                            TextButton(
                                onClick = actions::requestClassify,
                                enabled = uiState.canRequestClassify
                            ) {
                                Text(
                                    text = "분류",
                                    color = if (uiState.canRequestClassify) {
                                        AppTheme.colors.textPrimary
                                    } else {
                                        AppTheme.colors.textPrimary.copy(alpha = 0.4f)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = actions::enterLineSelectionMode,
                                enabled = uiState.canEnterLineSelectionMode
                            ) {
                                Text(
                                    text = "선택",
                                    color = if (uiState.canEnterLineSelectionMode) {
                                        AppTheme.colors.textPrimary
                                    } else {
                                        AppTheme.colors.textPrimary.copy(alpha = 0.4f)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = actions::requestRenameSelectedFile,
                                enabled = !uiState.isFileOperationInProgress && !uiState.classify.isClassifying,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "이름 수정",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (uiState.isLineSelectionMode) {
                            TextButton(
                                onClick = actions::exitLineSelectionMode,
                                enabled = uiState.canExitLineSelectionMode
                            ) {
                                Text("편집으로", color = AppTheme.colors.textPrimary, fontSize = 12.sp)
                            }
                        }
                    }

                    if (uiState.isLineSelectionMode) {
                        LineSelectionList(
                            lines = uiState.selectableLines,
                            selectedIndices = uiState.selectedLineIndices,
                            onToggle = actions::toggleLineSelection,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        OutlinedTextField(
                            value = editingText,
                            onValueChange = actions::onTextChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            enabled = uiState.canEditText,
                            placeholder = { Text(text = "Select a file or create a new txt file.") },
                            minLines = 8,
                            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = AppTheme.colors.textPrimary,
                                fontSize = 15.sp
                            ),
                            colors = appTextFieldColors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            // 3구역: 고정형 하단 액션 버튼 바
            if (uiState.isLineSelectionMode) {
                LineSelectionActionBar(
                    uiState = uiState,
                    onSelectAll = actions::selectAllLines,
                    onDeselectAll = actions::deselectAllLines,
                    onCompose = actions::composeDynamicPromptToClipboard,
                    onExit = actions::exitLineSelectionMode
                )
            } else {
                ActionButtonsBar(
                    uiState = uiState,
                    onSave = actions::onSaveFile,
                    onPaste = actions::pasteFromClipboard,
                    onPasteBelow = actions::pasteBelowFromClipboard,
                    onCopy = actions::copyToClipboard,
                    onUndo = actions::undoClipboardEdit
                )
            }

            if (!isKeyboardVisible) {
                // 4구역: 폴더 정보 스트립 (화면 최하단)
                FolderInfoSection(
                    environmentStatus = environmentStatus,
                    setupInfo = environmentSetupInfo,
                    onRefresh = actions::onRefresh,
                    onSelectFolder = actions::onSelectFolder
                )
            }

            if (uiState.message.isNotBlank()) {
                Text(
                    text = uiState.message,
                    color = AppTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.error.isNotBlank()) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    val activeDialog = deriveActiveWildcardDialog(uiState)
    if (activeDialog !is WildcardDialogType.None) {
        WildcardDialogHost(
            activeDialog = activeDialog,
            actions = actions
        )
    }
}

@Composable
private fun FileTabsSection(
    fileItems: List<WildcardFileUiItem>,
    onFileClick: (WildcardTextFile) -> Unit,
    canCreateFile: Boolean,
    canDelete: Boolean,
    onRequestNewFile: () -> Unit,
    onRequestDelete: () -> Unit
) {
    NeuCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (fileItems.isEmpty()) {
                Text(
                    text = "표시할 txt 파일이 없습니다.",
                    color = AppTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = fileItems,
                        key = { it.file.id }
                    ) { item ->
                        NeuPillChip(
                            text = item.displayName.removeSuffix(".txt"),
                            selected = item.isSelected,
                            onClick = { onFileClick(item.file) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            // 파일 추가 및 삭제 액션 버튼 영역 (우측 하단 정렬)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FileTabIconActionButton(
                    icon = Icons.Default.Add,
                    contentDescription = "새 파일",
                    enabled = canCreateFile,
                    activeTint = AppTheme.colors.primary,
                    activeBorderColor = AppTheme.colors.cardBorder,
                    onClick = onRequestNewFile
                )
                Spacer(modifier = Modifier.width(6.dp))
                FileTabIconActionButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "삭제",
                    enabled = canDelete,
                    activeTint = MaterialTheme.colorScheme.error,
                    activeBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    onClick = onRequestDelete
                )
            }
        }
    }
}

@Composable
private fun FileTabIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    activeTint: Color,
    activeBorderColor: Color,
    onClick: () -> Unit
) {
    val actionBtnShape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(
                elevation = if (enabled) 2.dp else 0.dp,
                shape = actionBtnShape,
                ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.2f)
            )
            .clip(actionBtnShape)
            .background(AppTheme.colors.card)
            .border(
                width = 1.dp,
                color = if (enabled) activeBorderColor else AppTheme.colors.cardBorder,
                shape = actionBtnShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) activeTint else AppTheme.colors.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FolderInfoSection(
    environmentStatus: EnvironmentStatus,
    setupInfo: EnvironmentSetupInfo,
    onRefresh: () -> Unit,
    onSelectFolder: () -> Unit
) {
    val folderPathDesc = WildcardFolderPathFormatter.summaryPath(
        setupInfo.wildcardDirectoryPath
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppTheme.colors.cardBorder, RoundedCornerShape(12.dp))
            .background(AppTheme.colors.card, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "와일드카드 저장소",
                color = AppTheme.colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = folderPathDesc,
                color = AppTheme.colors.textPrimary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!environmentStatus.isWildcardDirectoryAccessible) {
                Text(
                    text = "와일드카드 폴더를 선택해주세요.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            } else if (!environmentStatus.isWildcardDirectoryWritable) {
                Text(
                    text = "파일 편집을 위해 폴더를 다시 선택해주세요.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            FolderActionButton(
                label = if (setupInfo.wildcardDirectoryPath.isBlank()) "폴더 선택" else "폴더 변경",
                onClick = onSelectFolder
            )
            FolderActionButton(
                label = "새로고침",
                onClick = onRefresh
            )
        }
    }
}

@Composable
private fun FolderActionButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppTheme.colors.card,
            contentColor = AppTheme.colors.textPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LineSelectionList(
    lines: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lines.isEmpty()) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "선택할 줄이 없습니다.",
                color = AppTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "$index|$line" }
        ) { index, line ->
            val checked = index in selectedIndices
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle(index) }
                )
                Text(
                    text = line,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LineSelectionActionBar(
    uiState: WildcardUiState,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCompose: () -> Unit,
    onExit: () -> Unit
) {
    val barShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuButton(
            onClick = onSelectAll,
            enabled = uiState.canSelectAllLines,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            Text(text = "전체", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NeuButton(
            onClick = onDeselectAll,
            enabled = uiState.canDeselectAllLines,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            Text(text = "해제", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NeuButton(
            onClick = onCompose,
            enabled = uiState.canComposeDynamicPrompt,
            isPrimary = true,
            shape = barShape,
            modifier = Modifier
                .weight(2.2f)
                .height(52.dp)
        ) {
            Text(
                text = "다이나믹 구성",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NeuButton(
            onClick = onExit,
            enabled = uiState.canExitLineSelectionMode,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
        ) {
            Text(text = "완료", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ActionButtonsBar(
    uiState: WildcardUiState,
    onSave: () -> Unit,
    onPaste: () -> Unit,
    onPasteBelow: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit
) {
    val barShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuButton(
            onClick = onPasteBelow,
            enabled = uiState.canPaste,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(3.8f)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "아래 붙여넣기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NeuButton(
            onClick = onPaste,
            enabled = uiState.canPaste,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(3.6f)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "붙여넣기",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NeuButton(
            onClick = onSave,
            enabled = uiState.canSave,
            isPrimary = true,
            shape = barShape,
            modifier = Modifier
                .weight(3.6f)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "저장",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        NeuButton(
            onClick = onCopy,
            enabled = uiState.canCopy,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "복사", modifier = Modifier.size(16.dp))
        }

        NeuButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            isPrimary = false,
            shape = barShape,
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Undo, contentDescription = "실행 취소", modifier = Modifier.size(16.dp))
        }
    }
}
