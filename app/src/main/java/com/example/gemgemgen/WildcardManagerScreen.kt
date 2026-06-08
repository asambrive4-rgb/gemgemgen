package com.example.gemgemgen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.ui.theme.*

@Composable
internal fun WildcardManagerScreen(
    uiState: WildcardManagerUiState,
    environmentStatus: EnvironmentStatus,
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

    if (editingTextFieldValueState.text != uiState.editingText) {
        editingTextFieldValueState = editingTextFieldValueState.copy(
            text = uiState.editingText,
            selection = TextRange(uiState.editingText.length)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MemoBackground)
            .imePadding()
            .clearFocusOnOutsideTap(onClearFocus)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1구역: Top Strip (파일 탭 목록 + 새 파일/삭제 버튼)
            FileTabsSection(
                fileItems = uiState.fileItems,
                onFileClick = onFileClick,
                canCreateFile = uiState.canCreateFile,
                canDelete = uiState.canDelete,
                onRequestNewFile = onRequestNewFile,
                onRequestDelete = onRequestDelete
            )

            // 2구역: 에디터 카드 (헤더 + 텍스트 입력창 일체화)
            val isDirty = uiState.selectedFile != null && uiState.editingText != uiState.savedText
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
                        text = statusText,
                        color = MemoText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.selectedFile != null) {
                        IconButton(
                            onClick = onRequestRename,
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
                }

                // 테두리 없는 텍스트 에디터
                OutlinedTextField(
                    value = editingTextFieldValueState,
                    onValueChange = { newVal ->
                        editingTextFieldValueState = newVal
                        onTextChange(newVal.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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

            // 3구역: 고정형 하단 액션 버튼 바
            ActionButtonsBar(
                uiState = uiState,
                onSave = onSave,
                onPaste = onPaste,
                onPasteBelow = onPasteBelow,
                onCopy = onCopy,
                onUndo = onUndo
            )

            // 4구역: 폴더 정보 스트립 (화면 최하단)
            FolderInfoSection(
                environmentStatus = environmentStatus,
                onRefresh = onRefresh,
                onSelectFolder = onSelectFolder
            )

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
            onCreate = onCreateNewFile,
            onDismiss = onDismissNewFile
        )
    }

    if (uiState.showRenameDialog) {
        RenameFileDialog(
            fileName = uiState.renameFileName,
            error = uiState.error,
            onFileNameChange = onRenameFileNameChange,
            onConfirm = onConfirmRename,
            onDismiss = onDismissRename
        )
    }

    if (uiState.showDeleteConfirm) {
        DeleteConfirmDialog(
            fileName = uiState.selectedFile?.fileName.orEmpty(),
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete
        )
    }

    if (uiState.pendingAction != null) {
        UnsavedChangesDialog(
            onSave = onConfirmPendingSave,
            onDiscard = onConfirmPendingDiscard,
            onCancel = onCancelPending
        )
    }
}

private fun getSummaryPath(uriString: String): String {
    if (uriString.isBlank()) return "선택된 폴더 없음"
    val decoded = Uri.decode(uriString)
    
    val parts = decoded.split("/tree/")
    if (parts.size > 1) {
        val pathPart = parts[1]
        val colonIndex = pathPart.indexOf(':')
        return if (colonIndex != -1 && colonIndex < pathPart.length - 1) {
            pathPart.substring(colonIndex + 1)
        } else {
            pathPart
        }
    }
    
    val lastSegment = Uri.parse(uriString).lastPathSegment
    if (!lastSegment.isNullOrBlank()) {
        val colonIndex = lastSegment.indexOf(':')
        return if (colonIndex != -1 && colonIndex < lastSegment.length - 1) {
            lastSegment.substring(colonIndex + 1)
        } else {
            lastSegment
        }
    }
    
    return uriString
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                fileItems.forEach { item ->
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
    onRefresh: () -> Unit,
    onSelectFolder: () -> Unit
) {
    val folderPathDesc = getSummaryPath(environmentStatus.wildcardDirectoryPath)

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
                    text = if (environmentStatus.wildcardDirectoryPath.isBlank()) "Select Folder" else "Change Folder",
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
                    WildcardTextFile("1", "hair.txt", "content://hair"),
                    WildcardTextFile("2", "color.txt", "content://color")
                ),
                selectedFile = WildcardTextFile("1", "hair.txt", "content://hair"),
                savedText = "black hair",
                editingText = "black hair\nsilver hair",
                canModifyFiles = true
            ),
            environmentStatus = EnvironmentStatus(
                isWildcardDirectoryAccessible = true,
                isWildcardDirectoryWritable = true,
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
            onConfirmPendingSave = {},
            onConfirmPendingDiscard = {},
            onCancelPending = {}
        )
    }
}

