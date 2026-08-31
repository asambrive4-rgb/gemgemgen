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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import com.example.gemgemgen.ui.blockMainTabSwipe
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_1_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_5_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_6_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_7_FLASH
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
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
import com.example.gemgemgen.ui.theme.*
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
            .background(MemoBackground)
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
            } ?: "No file selected"
            val statusText = if (isDirty) "$fileLabel *" else fileLabel

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MemoEditorBorder, RoundedCornerShape(12.dp))
                    .background(MemoSurface, RoundedCornerShape(12.dp))
            ) {
                // 에디터 일체형 헤더 (부드러운 연보라색 배경 띠)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MemoPrimary.copy(alpha = 0.3f), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isLineSelectionMode) {
                            "선택 ${uiState.selectedLineIndices.size}/${uiState.selectableLines.size}"
                        } else {
                            statusText
                        },
                        color = MemoText,
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
                                    MemoText
                                } else {
                                    MemoText.copy(alpha = 0.4f)
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
                                    MemoText
                                } else {
                                    MemoText.copy(alpha = 0.4f)
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
                                tint = MemoSubtle,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (uiState.isLineSelectionMode) {
                        TextButton(
                            onClick = onExitLineSelectionMode,
                            enabled = uiState.canExitLineSelectionMode
                        ) {
                            Text("편집으로", color = MemoText, fontSize = 12.sp)
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
                            .blockMainTabSwipe()
                    )
                } else {
                    // 테두리 없는 텍스트 에디터
                    OutlinedTextField(
                        value = editingTextFieldValueState,
                        onValueChange = { newVal ->
                            editingTextFieldValueState = newVal
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .blockMainTabSwipe(),
                        enabled = uiState.canEditText,
                        placeholder = {
                            Text(
                                text = "Select a file or create a new txt file.",
                                color = MemoSubtle
                            )
                        },
                        minLines = 8,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MemoText,
                            fontSize = 15.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
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
                    color = MemoSubtle,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.error.isNotBlank()) {
                Text(
                    text = uiState.error,
                    color = MemoDanger,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (uiState.showNewFileDialog) {
        NewFileDialog(
            fileName = uiState.newFileName,
            error = uiState.error,
            onFileNameChange = onNewFileNameChange,
            onCreate = {
                runWithCommittedText(onCreateNewFile)
            },
            onDismiss = onDismissNewFile
        )
    }

    if (uiState.showRenameDialog) {
        RenameFileDialog(
            fileName = uiState.renameFileName,
            error = uiState.error,
            onFileNameChange = onRenameFileNameChange,
            onConfirm = {
                runWithCommittedText(onConfirmRename)
            },
            onDismiss = onDismissRename
        )
    }

    if (uiState.showDeleteConfirm) {
        DeleteConfirmDialog(
            fileName = uiState.selectedFile?.fileName.orEmpty(),
            onConfirm = {
                runWithCommittedText(onConfirmDelete)
            },
            onDismiss = onDismissDelete
        )
    }

    if (uiState.pendingAction != null) {
        UnsavedChangesDialog(
            onSave = {
                runWithCommittedText(onConfirmPendingSave)
            },
            onDiscard = onConfirmPendingDiscard,
            onCancel = onCancelPending
        )
    }

    if (uiState.isClassifying) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("분류 중") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("분석 설정(Gemini/Grok)으로 분류하고 있습니다…")
                }
            },
            confirmButton = {}
        )
    }

    if (uiState.showClassifyCriteriaDialog && !uiState.isClassifying) {
        ClassifyCriteriaDialog(
            criteria = uiState.classifyCriteria,
            provider = uiState.classifyProvider,
            modelId = uiState.classifyModelId,
            error = uiState.error,
            canRun = uiState.canRunClassify,
            onCriteriaChange = onClassifyCriteriaChange,
            onProviderSelected = onClassifyProviderSelected,
            onModelSelected = onClassifyModelSelected,
            onRun = {
                runWithCommittedText(onRunClassify)
            },
            onDismiss = onDismissClassifyCriteria
        )
    }

    val classifyPreview = uiState.classifyPreview
    if (classifyPreview != null && !uiState.isClassifying && uiState.classifyOverwriteConflicts.isEmpty()) {
        ClassifyPreviewDialog(
            result = classifyPreview,
            criteria = uiState.classifyCriteria,
            saveEntries = uiState.classifySaveEntries,
            canSave = uiState.canSaveClassifyResult,
            canRerun = uiState.canRerunClassifyFromPreview,
            error = uiState.error,
            onCriteriaChange = onClassifyCriteriaChange,
            onFileNameChange = onClassifyFileNameChange,
            onToggleFileNameEdit = onToggleClassifyFileNameEdit,
            onRerun = onRunClassify,
            onSave = onSaveClassifyResult,
            onDismiss = onDismissClassifyPreview
        )
    }

    if (uiState.classifyOverwriteConflicts.isNotEmpty()) {
        ClassifyOverwriteDialog(
            fileNames = uiState.classifyOverwriteConflicts,
            onConfirm = onConfirmClassifyOverwrite,
            onDismiss = onDismissClassifyOverwrite
        )
    }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MemoStripBorder, RoundedCornerShape(12.dp))
            .background(MemoSurface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (fileItems.isEmpty()) {
            Text(
                text = "표시할 txt 파일이 없습니다.",
                color = MemoSubtle,
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MemoTabSelected else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MemoTabSelectedBorder else MemoTabBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onFileClick(item.file) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.displayName.endsWith(".txt")) item.displayName.dropLast(4) else item.displayName,
                            color = MemoText,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
            // 새 파일 (+) 버튼
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, MemoStripBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = canCreateFile) { onRequestNewFile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "새 파일",
                    tint = if (canCreateFile) MemoSubtle else MemoSubtle.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 삭제 (휴지통) 버튼
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = if (canDelete) MemoDangerBorder else MemoStripBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = canDelete) { onRequestDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = if (canDelete) MemoDanger else MemoSubtle.copy(alpha = 0.4f),
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
            .border(1.dp, MemoStripBorder, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Storage folder",
                color = MemoSubtle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = folderPathDesc,
                color = MemoText,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!environmentStatus.isWildcardDirectoryAccessible) {
                Text(
                    text = "Please select the wildcard folder.",
                    color = MemoDanger,
                    fontSize = 12.sp
                )
            } else if (!environmentStatus.isWildcardDirectoryWritable) {
                Text(
                    text = "Please select the folder again to edit files.",
                    color = MemoDanger,
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
                border = BorderStroke(1.dp, MemoStripBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = MemoText
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (setupInfo.wildcardDirectoryPath.isBlank()) "Select Folder" else "Change Folder",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onRefresh,
                border = BorderStroke(1.dp, MemoStripBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = MemoText
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "Refresh",
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
                color = MemoSubtle,
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
                    color = MemoText,
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
            border = BorderStroke(1.dp, MemoStripBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
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
            border = BorderStroke(1.dp, MemoStripBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
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
                containerColor = MemoPrimary,
                disabledContainerColor = MemoPrimary.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
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
            border = BorderStroke(1.dp, MemoStripBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
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
                containerColor = MemoPrimary,
                disabledContainerColor = MemoPrimary.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .weight(3.8f)
                .height(52.dp)
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
                    text = "Paste Below",
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
                containerColor = MemoPaste,
                disabledContainerColor = MemoPaste.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .weight(3.6f)
                .height(52.dp)
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
                    text = "Paste",
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
                containerColor = MemoSave,
                disabledContainerColor = MemoSave.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .weight(3.6f)
                .height(52.dp)
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
                    text = "Save",
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
            border = BorderStroke(1.dp, if (uiState.canCopy) MemoCopyBorder else MemoStripBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MemoCopy,
                disabledContainerColor = MemoCopy.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp)
            )
        }

        // Undo (weight 1.2)
        OutlinedButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            border = BorderStroke(1.dp, MemoUndoBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                contentColor = MemoText,
                disabledContentColor = MemoText.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .weight(1.2f)
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassifyCriteriaDialog(
    criteria: String,
    provider: AnalysisProvider,
    modelId: String,
    error: String,
    canRun: Boolean,
    onCriteriaChange: (String) -> Unit,
    onProviderSelected: (AnalysisProvider) -> Unit,
    onModelSelected: (String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("와일드카드 분류") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "지금 연 파일의 모든 줄을 기준에 따라 나눕니다. 모델 기본값은 분석 탭「TXT 생성」과 같고, 여기서 바꾸면 함께 저장됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MemoSubtle
                )
                Text(
                    text = "모델",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MemoText
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ClassifyModelChip(
                        label = "Gemini",
                        selected = provider == AnalysisProvider.GEMINI,
                        onClick = { onProviderSelected(AnalysisProvider.GEMINI) }
                    )
                    ClassifyModelChip(
                        label = "Grok",
                        selected = provider == AnalysisProvider.GROK,
                        onClick = { onProviderSelected(AnalysisProvider.GROK) }
                    )
                    when (provider) {
                        AnalysisProvider.GEMINI -> {
                            ClassifyModelChip(
                                label = "3.5 Lite",
                                selected = modelId == MODEL_GEMINI_3_5_FLASH_LITE,
                                onClick = { onModelSelected(MODEL_GEMINI_3_5_FLASH_LITE) }
                            )
                            ClassifyModelChip(
                                label = "3.1 Lite",
                                selected = modelId == MODEL_GEMINI_3_1_FLASH_LITE,
                                onClick = { onModelSelected(MODEL_GEMINI_3_1_FLASH_LITE) }
                            )
                            ClassifyModelChip(
                                label = "3.6 Flash",
                                selected = modelId == MODEL_GEMINI_3_6_FLASH,
                                onClick = { onModelSelected(MODEL_GEMINI_3_6_FLASH) }
                            )
                            ClassifyModelChip(
                                label = "3.7 Flash",
                                selected = modelId == MODEL_GEMINI_3_7_FLASH,
                                onClick = { onModelSelected(MODEL_GEMINI_3_7_FLASH) }
                            )
                        }
                        AnalysisProvider.GROK -> {
                            ClassifyModelChip(
                                label = "Grok 4.5",
                                selected = modelId == MODEL_GROK_4_5,
                                onClick = { onModelSelected(MODEL_GROK_4_5) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = criteria,
                    onValueChange = onCriteriaChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    minLines = 3,
                    label = { Text("분류 기준") },
                    placeholder = { Text("예: 캐주얼 / 포멀 / 기타") }
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MemoDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRun, enabled = canRun) {
                Text("분류 실행")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun ClassifyModelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MemoPrimary.copy(alpha = 0.45f) else Color.White,
        border = BorderStroke(
            1.dp,
            if (selected) MemoTabSelectedBorder else MemoStripBorder
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MemoText
        )
    }
}

@Composable
private fun ClassifyPreviewDialog(
    result: WildcardClassifyResult,
    criteria: String,
    saveEntries: List<WildcardClassifySaveEntry>,
    canSave: Boolean,
    canRerun: Boolean,
    error: String,
    onCriteriaChange: (String) -> Unit,
    onFileNameChange: (Int, String) -> Unit,
    onToggleFileNameEdit: (Int) -> Unit,
    onRerun: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("분류 미리보기") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "전체 목록을 확인한 뒤, 기준을 고쳐 다시 분류하거나 저장하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MemoSubtle
                )
                OutlinedTextField(
                    value = criteria,
                    onValueChange = onCriteriaChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                    minLines = 2,
                    label = { Text("분류 기준 (다시 분류용)") },
                    placeholder = { Text("기준을 수정한 뒤 다시 분류") }
                )
                OutlinedButton(
                    onClick = onRerun,
                    enabled = canRerun,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MemoStripBorder)
                ) {
                    Text("다시 분류 (전체)", fontWeight = FontWeight.Bold)
                }
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MemoDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val dropNote = if (result.droppedLineCount > 0) {
                    " · 미배정 ${result.droppedLineCount}줄(저장 안 함)"
                } else {
                    ""
                }
                Text(
                    text = "원본 ${result.sourceLines.size}줄 → ${saveEntries.size}개 파일 예정$dropNote",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                saveEntries.forEachIndexed { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${entry.groupName} (${entry.items.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MemoText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (entry.isEditingFileName) {
                                OutlinedTextField(
                                    value = entry.fileNameInput,
                                    onValueChange = { onFileNameChange(index, it) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("파일명") },
                                    trailingIcon = {
                                        Text(
                                            text = ".txt",
                                            color = MemoSubtle,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                                IconButton(
                                    onClick = { onToggleFileNameEdit(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "파일명 확정",
                                        tint = MemoSubtle,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "${entry.fileNameInput.trim().removeSuffix(".txt")}.txt",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    color = MemoText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { onToggleFileNameEdit(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "파일명 수정",
                                        tint = MemoSubtle,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        entry.items.forEach { line ->
                            Text(
                                text = "· $line",
                                fontSize = 12.sp,
                                color = MemoSubtle
                            )
                        }
                    }
                }
                if (result.droppedLines.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "미배정 (${result.droppedLines.size}) · 저장 안 함",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MemoDanger
                        )
                        result.droppedLines.forEach { line ->
                            Text(
                                text = "· $line",
                                fontSize = 12.sp,
                                color = MemoSubtle
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text("파일로 저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun ClassifyOverwriteDialog(
    fileNames: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("파일 덮어쓰기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("같은 이름의 파일이 있습니다. 덮어쓸까요?")
                fileNames.forEach { name ->
                    Text("· $name", fontSize = 13.sp, color = MemoSubtle)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("덮어쓰기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun NewFileDialog(
    fileName: String,
    error: String,
    onFileNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 txt 파일") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    singleLine = true,
                    label = { Text("파일명") },
                    placeholder = { Text("예: hair") }
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MemoDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) {
                Text("생성")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("파일 삭제") },
        text = { Text("$fileName 파일을 삭제할까요?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun RenameFileDialog(
    fileName: String,
    error: String,
    onFileNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("파일 이름 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    singleLine = true,
                    label = { Text("파일명") },
                    placeholder = { Text("예: new_hair") }
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MemoDanger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("변경")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("저장하지 않은 변경사항") },
        text = { Text("현재 파일의 변경사항을 어떻게 처리할까요?") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSave) {
                    Text("Save")
                }
                TextButton(onClick = onDiscard) {
                    Text("Discard")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
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

