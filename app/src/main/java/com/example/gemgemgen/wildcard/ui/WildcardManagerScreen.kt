package com.example.gemgemgen.wildcard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.environment.domain.EnvironmentSetupInfo
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.ui.clearFocusOnOutsideTap
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import com.example.gemgemgen.wildcard.domain.WildcardEditorSession
import com.example.gemgemgen.wildcard.domain.WildcardTextFile
import kotlinx.coroutines.delay

@Composable
internal fun WildcardManagerScreen(
    uiState: WildcardManagerUiState,
    environmentStatus: EnvironmentStatus,
    environmentSetupInfo: EnvironmentSetupInfo,
    onClearFocus: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFolder: () -> Unit,
    onFileClick: (WildcardTextFile) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestNewFile: () -> Unit,
    onNewFileNameChange: (String) -> Unit,
    onCreateNewFile: () -> Unit,
    onDismissNewFile: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onRequestRename: () -> Unit,
    onRenameFileNameChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onDismissRename: () -> Unit,
    onPaste: () -> Unit,
    onPasteBelow: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
    onEnterLineSelectionMode: () -> Unit,
    onExitLineSelectionMode: () -> Unit,
    onToggleLineSelection: (Int) -> Unit,
    onSelectAllLines: () -> Unit,
    onDeselectAllLines: () -> Unit,
    onComposeDynamicPrompt: () -> Unit,
    onRequestClassify: () -> Unit,
    onClassifyCriteriaChange: (String) -> Unit,
    onClassifyProviderSelected: (AnalysisProvider) -> Unit,
    onClassifyModelSelected: (String) -> Unit,
    onDismissClassifyCriteria: () -> Unit,
    onRunClassify: () -> Unit,
    onDismissClassifyPreview: () -> Unit,
    onClassifyFileNameChange: (Int, String) -> Unit,
    onToggleClassifyFileNameEdit: (Int) -> Unit,
    onSaveClassifyResult: () -> Unit,
    onConfirmClassifyOverwrite: () -> Unit,
    onDismissClassifyOverwrite: () -> Unit,
    onConfirmPendingSave: () -> Unit,
    onConfirmPendingDiscard: () -> Unit,
    onCancelPending: () -> Unit
) {
    var editingTextFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.editingText,
                selection = TextRange(uiState.editingText.length)
            )
        )
    }
    var lastCommittedEditingText by remember { mutableStateOf(uiState.editingText) }

    if (uiState.editingText != lastCommittedEditingText) {
        editingTextFieldValueState = editingTextFieldValueState.copy(
            text = uiState.editingText,
            selection = TextRange(uiState.editingText.length)
        )
        lastCommittedEditingText = uiState.editingText
    }

    LaunchedEffect(editingTextFieldValueState.text) {
        val text = editingTextFieldValueState.text
        if (text != lastCommittedEditingText) {
            delay(TEXT_COMMIT_DEBOUNCE_MS)
            if (text != lastCommittedEditingText) {
                onTextChange(text)
                lastCommittedEditingText = text
            }
        }
    }

    fun commitEditingText() {
        val text = editingTextFieldValueState.text
        if (text != lastCommittedEditingText) {
            onTextChange(text)
            lastCommittedEditingText = text
        }
    }

    fun runWithCommittedText(action: () -> Unit) {
        commitEditingText()
        action()
    }
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val isDirty = uiState.selectedFile != null &&
        editingTextFieldValueState.text != uiState.savedText
    val fileItems = remember(uiState.files, uiState.selectedFile?.id, isDirty) {
        uiState.files.map { file ->
            val isSelected = uiState.selectedFile?.id == file.id
            WildcardFileUiItem(
                file = file,
                displayName = if (isSelected && isDirty) {
                    "${file.fileName} *"
                } else {
                    file.fileName
                },
                isSelected = isSelected
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.canvas)
            .imePadding()
            .clearFocusOnOutsideTap {
                runWithCommittedText(onClearFocus)
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
                onFileClick = { file ->
                    runWithCommittedText {
                        onFileClick(file)
                    }
                },
                canCreateFile = uiState.canCreateFile,
                canDelete = uiState.canDelete,
                onRequestNewFile = {
                    runWithCommittedText(onRequestNewFile)
                },
                onRequestDelete = {
                    runWithCommittedText(onRequestDelete)
                }
            )

            // 2구역: 에디터 카드 (헤더 + 텍스트 입력창 일체화)
            val fileLabel = uiState.selectedFile?.fileName?.let {
                if (it.endsWith(".txt")) it.dropLast(4) else it
            } ?: "선택된 파일 없음"
            val statusText = if (isDirty) "$fileLabel *" else fileLabel
            val editorCardShape = RoundedCornerShape(20.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(
                        elevation = 4.dp,
                        shape = editorCardShape,
                        ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.35f),
                        spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f)
                    )
                    .border(1.dp, AppTheme.colors.cardBorder, editorCardShape)
                    .background(AppTheme.colors.card, editorCardShape)
            ) {
                // 에디터 일체형 헤더 (부드러운 톤온톤 배경 띠)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.colors.primary.copy(alpha = 0.15f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
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
                    if (uiState.selectedFile != null && !uiState.isLineSelectionMode) {
                        TextButton(
                            onClick = {
                                runWithCommittedText(onRequestClassify)
                            },
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
                            onClick = {
                                runWithCommittedText(onEnterLineSelectionMode)
                            },
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
                            onClick = {
                                runWithCommittedText(onRequestRename)
                            },
                            enabled = !uiState.isFileOperationInProgress && !uiState.isClassifying,
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
                            onClick = onExitLineSelectionMode,
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
                        onToggle = onToggleLineSelection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    // 클린 화이트 인셋 베드 텍스트 에디터
                    OutlinedTextField(
                        value = editingTextFieldValueState,
                        onValueChange = { newVal ->
                            editingTextFieldValueState = newVal
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        enabled = uiState.canEditText,
                        placeholder = {
                            Text(
                                text = "Select a file or create a new txt file.",
                                color = AppTheme.colors.textSecondary.copy(alpha = 0.6f)
                            )
                        },
                        minLines = 8,
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedContainerColor = AppTheme.colors.inputBackground,
                            unfocusedContainerColor = AppTheme.colors.inputBackground,
                            disabledContainerColor = AppTheme.colors.inputBackground.copy(alpha = 0.6f),
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary,
                            cursorColor = AppTheme.colors.primary
                        )
                    )
                }
            }

            // 3구역: 고정형 하단 액션 버튼 바
            if (uiState.isLineSelectionMode) {
                LineSelectionActionBar(
                    uiState = uiState,
                    onSelectAll = onSelectAllLines,
                    onDeselectAll = onDeselectAllLines,
                    onCompose = onComposeDynamicPrompt,
                    onExit = onExitLineSelectionMode
                )
            } else {
                ActionButtonsBar(
                    uiState = uiState,
                    onSave = {
                        runWithCommittedText(onSave)
                    },
                    onPaste = {
                        runWithCommittedText(onPaste)
                    },
                    onPasteBelow = {
                        runWithCommittedText(onPasteBelow)
                    },
                    onCopy = {
                        runWithCommittedText(onCopy)
                    },
                    onUndo = {
                        runWithCommittedText(onUndo)
                    }
                )
            }

            if (!isKeyboardVisible) {
                // 4구역: 폴더 정보 스트립 (화면 최하단)
                FolderInfoSection(
                    environmentStatus = environmentStatus,
                    setupInfo = environmentSetupInfo,
                    onRefresh = {
                        runWithCommittedText(onRefresh)
                    },
                    onSelectFolder = {
                        runWithCommittedText(onSelectFolder)
                    }
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
    val dialogActions = WildcardDialogActions(
        onNewFileNameChange = onNewFileNameChange,
        onCreateNewFile = { runWithCommittedText(onCreateNewFile) },
        onDismissNewFile = onDismissNewFile,
        onRenameFileNameChange = onRenameFileNameChange,
        onConfirmRename = { runWithCommittedText(onConfirmRename) },
        onDismissRename = onDismissRename,
        onConfirmDelete = { runWithCommittedText(onConfirmDelete) },
        onDismissDelete = onDismissDelete,
        onConfirmPendingSave = { runWithCommittedText(onConfirmPendingSave) },
        onConfirmPendingDiscard = onConfirmPendingDiscard,
        onCancelPending = onCancelPending,
        onClassifyCriteriaChange = onClassifyCriteriaChange,
        onClassifyProviderSelected = onClassifyProviderSelected,
        onClassifyModelSelected = onClassifyModelSelected,
        onRunClassify = { runWithCommittedText(onRunClassify) },
        onDismissClassifyCriteria = onDismissClassifyCriteria,
        onClassifyFileNameChange = onClassifyFileNameChange,
        onToggleClassifyFileNameEdit = onToggleClassifyFileNameEdit,
        onSaveClassifyResult = onSaveClassifyResult,
        onDismissClassifyPreview = onDismissClassifyPreview,
        onConfirmClassifyOverwrite = onConfirmClassifyOverwrite,
        onDismissClassifyOverwrite = onDismissClassifyOverwrite
    )
    WildcardDialogHost(
        activeDialog = activeDialog,
        actions = dialogActions
    )
}

private const val TEXT_COMMIT_DEBOUNCE_MS = 250L

@Composable
private fun FileTabsSection(
    fileItems: List<WildcardFileUiItem>,
    onFileClick: (WildcardTextFile) -> Unit,
    canCreateFile: Boolean,
    canDelete: Boolean,
    onRequestNewFile: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val containerShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = containerShape,
                ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.35f),
                spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f)
            )
            .border(1.dp, AppTheme.colors.cardBorder, containerShape)
            .background(AppTheme.colors.card, containerShape)
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
                    val isSelected = item.isSelected
                    val tabShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = if (isSelected) 3.dp else 1.dp,
                                shape = tabShape,
                                ambientColor = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.35f) else AppTheme.colors.shadowDark.copy(alpha = 0.25f),
                                spotColor = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.3f) else AppTheme.colors.shadowDark.copy(alpha = 0.2f)
                            )
                            .clip(tabShape)
                            .background(if (isSelected) AppTheme.colors.primary else AppTheme.colors.card)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.cardBorder,
                                shape = tabShape
                            )
                            .clickable { onFileClick(item.file) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.displayName.endsWith(".txt")) item.displayName.dropLast(4) else item.displayName,
                            color = if (isSelected) AppTheme.colors.onPrimary else AppTheme.colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 파일 추가 및 삭제 액션 버튼 영역 (우측 하단 정렬)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val actionBtnShape = RoundedCornerShape(10.dp)
            // 새 파일 (+) 버튼
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = if (canCreateFile) 2.dp else 0.dp,
                        shape = actionBtnShape,
                        ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                        spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.2f)
                    )
                    .clip(actionBtnShape)
                    .background(AppTheme.colors.card)
                    .border(1.dp, AppTheme.colors.cardBorder, actionBtnShape)
                    .clickable(enabled = canCreateFile) { onRequestNewFile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "새 파일",
                    tint = if (canCreateFile) AppTheme.colors.primary else AppTheme.colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 삭제 (휴지통) 버튼
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = if (canDelete) 2.dp else 0.dp,
                        shape = actionBtnShape,
                        ambientColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        spotColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    )
                    .clip(actionBtnShape)
                    .background(AppTheme.colors.card)
                    .border(
                        width = 1.dp,
                        color = if (canDelete) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else AppTheme.colors.cardBorder,
                        shape = actionBtnShape
                    )
                    .clickable(enabled = canDelete) { onRequestDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = if (canDelete) MaterialTheme.colorScheme.error else AppTheme.colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
            OutlinedButton(
                onClick = onSelectFolder,
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (setupInfo.wildcardDirectoryPath.isBlank()) "폴더 선택" else "폴더 변경",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onRefresh,
                border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = AppTheme.colors.card,
                    contentColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "새로고침",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
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
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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
    uiState: WildcardManagerUiState,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCompose: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onSelectAll,
            enabled = uiState.canSelectAllLines,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            Text(
                text = "전체",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        OutlinedButton(
            onClick = onDeselectAll,
            enabled = uiState.canDeselectAllLines,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            Text(
                text = "해제",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Button(
            onClick = onCompose,
            enabled = uiState.canComposeDynamicPrompt,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.primary,
                disabledContainerColor = AppTheme.colors.primary.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
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
        OutlinedButton(
            onClick = onExit,
            enabled = uiState.canExitLineSelectionMode,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
        ) {
            Text(
                text = "완료",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ActionButtonsBar(
    uiState: WildcardManagerUiState,
    onSave: () -> Unit,
    onPaste: () -> Unit,
    onPasteBelow: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Paste Below (weight 3.8)
        Button(
            onClick = onPasteBelow,
            enabled = uiState.canPaste,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .weight(3.8f)
                .height(52.dp)
                .shadow(
                    elevation = if (uiState.canPaste) 3.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.2f)
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "아래 붙여넣기",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Paste (weight 3.6)
        Button(
            onClick = onPaste,
            enabled = uiState.canPaste,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .weight(3.6f)
                .height(52.dp)
                .shadow(
                    elevation = if (uiState.canPaste) 3.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f),
                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.2f)
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "붙여넣기",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Save (weight 3.6)
        Button(
            onClick = onSave,
            enabled = uiState.canSave,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.primary,
                disabledContainerColor = AppTheme.colors.primary.copy(alpha = 0.4f),
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .weight(3.6f)
                .height(52.dp)
                .shadow(
                    elevation = if (uiState.canSave) 4.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = AppTheme.colors.primary.copy(alpha = 0.4f),
                    spotColor = AppTheme.colors.primary.copy(alpha = 0.3f)
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "저장",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Copy (weight 1.2)
        OutlinedButton(
            onClick = onCopy,
            enabled = uiState.canCopy,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
                .shadow(
                    elevation = if (uiState.canCopy) 2.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f),
                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.15f)
                )
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "복사",
                modifier = Modifier.size(16.dp)
            )
        }

        // Undo (weight 1.2)
        OutlinedButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = AppTheme.colors.card,
                disabledContainerColor = AppTheme.colors.card.copy(alpha = 0.4f),
                contentColor = AppTheme.colors.textPrimary,
                disabledContentColor = AppTheme.colors.textPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
                .shadow(
                    elevation = if (uiState.canUndo) 2.dp else 0.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f),
                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.15f)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "실행 취소",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun WildcardManagerScreenPreview() {
    GemgemgenTheme {
        WildcardManagerScreen(
            uiState = WildcardManagerUiState(
                files = listOf(
                    WildcardTextFile("1", "hair.txt"),
                    WildcardTextFile("2", "color.txt")
                ),
                editor = WildcardEditorSession(
                    selectedFile = WildcardTextFile("1", "hair.txt"),
                    savedText = "black hair",
                    editingText = "black hair\nsilver hair"
                ),
                canModifyFiles = true
            ),
            environmentStatus = EnvironmentStatus(
                isWildcardDirectoryAccessible = true,
                isWildcardDirectoryWritable = true
            ),
            environmentSetupInfo = EnvironmentSetupInfo(
                wildcardDirectoryPath = "content://wildcard"
            ),
            onClearFocus = {},
            onRefresh = {},
            onSelectFolder = {},
            onFileClick = {},
            onTextChange = {},
            onSave = {},
            onRequestNewFile = {},
            onNewFileNameChange = {},
            onCreateNewFile = {},
            onDismissNewFile = {},
            onRequestDelete = {},
            onConfirmDelete = {},
            onDismissDelete = {},
            onRequestRename = {},
            onRenameFileNameChange = {},
            onConfirmRename = {},
            onDismissRename = {},
            onPaste = {},
            onPasteBelow = {},
            onCopy = {},
            onUndo = {},
            onEnterLineSelectionMode = {},
            onExitLineSelectionMode = {},
            onToggleLineSelection = {},
            onSelectAllLines = {},
            onDeselectAllLines = {},
            onComposeDynamicPrompt = {},
            onRequestClassify = {},
            onClassifyCriteriaChange = {},
            onClassifyProviderSelected = {},
            onClassifyModelSelected = {},
            onDismissClassifyCriteria = {},
            onRunClassify = {},
            onDismissClassifyPreview = {},
            onClassifyFileNameChange = { _, _ -> },
            onToggleClassifyFileNameEdit = {},
            onSaveClassifyResult = {},
            onConfirmClassifyOverwrite = {},
            onDismissClassifyOverwrite = {},
            onConfirmPendingSave = {},
            onConfirmPendingDiscard = {},
            onCancelPending = {}
        )
    }
}

