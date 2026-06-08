package com.example.gemgemgen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.GemgemgenTheme

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
    onPaste: () -> Unit,
    onPasteBelow: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
    onConfirmPendingSave: () -> Unit,
    onConfirmPendingDiscard: () -> Unit,
    onCancelPending: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .clearFocusOnOutsideTap(onClearFocus)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HeaderRow(
                environmentStatus = environmentStatus,
                onRefresh = onRefresh,
                onSelectFolder = onSelectFolder
            )

            if (!environmentStatus.isWildcardDirectoryAccessible) {
                Text(
                    text = "wildcard 폴더를 선택하거나 다시 선택해주세요.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (!environmentStatus.isWildcardDirectoryWritable) {
                Text(
                    text = "txt 파일을 편집하려면 wildcard 폴더를 다시 선택해주세요.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            FileTabs(
                fileItems = uiState.fileItems,
                onFileClick = onFileClick
            )

            ActionRows(
                uiState = uiState,
                onSave = onSave,
                onRequestNewFile = onRequestNewFile,
                onRequestDelete = onRequestDelete,
                onPaste = onPaste,
                onPasteBelow = onPasteBelow,
                onCopy = onCopy,
                onUndo = onUndo
            )

            SelectedFileStatus(uiState)

            AppMultilineTextField(
                value = uiState.editingText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
                enabled = uiState.canEditText,
                placeholder = "파일을 선택하거나 새 txt 파일을 만들어주세요.",
                minLines = 8
            )

            if (uiState.message.isNotBlank()) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.primary,
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

    if (uiState.showNewFileDialog) {
        NewFileDialog(
            fileName = uiState.newFileName,
            error = uiState.error,
            onFileNameChange = onNewFileNameChange,
            onCreate = onCreateNewFile,
            onDismiss = onDismissNewFile
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

@Composable
private fun HeaderRow(
    environmentStatus: EnvironmentStatus,
    onRefresh: () -> Unit,
    onSelectFolder: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "와일드카드 txt",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onRefresh) {
                Text("새로고침")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onSelectFolder) {
                Text("폴더 선택")
            }
            Text(
                text = environmentStatus.wildcardDirectoryPath.ifBlank { "선택된 폴더 없음" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FileTabs(
    fileItems: List<WildcardFileUiItem>,
    onFileClick: (WildcardTextFile) -> Unit
) {
    if (fileItems.isEmpty()) {
        Text(
            text = "표시할 txt 파일이 없습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fileItems.forEach { item ->
            OutlinedButton(
                onClick = { onFileClick(item.file) },
                border = BorderStroke(
                    width = if (item.isSelected) 2.dp else 1.dp,
                    color = if (item.isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            ) {
                Text(item.displayName)
            }
        }
    }
}

@Composable
private fun ActionRows(
    uiState: WildcardManagerUiState,
    onSave: () -> Unit,
    onRequestNewFile: () -> Unit,
    onRequestDelete: () -> Unit,
    onPaste: () -> Unit,
    onPasteBelow: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRequestNewFile,
                enabled = uiState.canCreateFile
            ) {
                Text("새 파일")
            }
            OutlinedButton(
                onClick = onRequestDelete,
                enabled = uiState.canDelete
            ) {
                Text("삭제")
            }
            Button(
                onClick = onSave,
                enabled = uiState.canSave
            ) {
                Text("저장")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPaste,
                enabled = uiState.canPaste
            ) {
                Text("붙여넣기")
            }
            OutlinedButton(
                onClick = onPasteBelow,
                enabled = uiState.canPaste
            ) {
                Text("아래 붙여넣기")
            }
            OutlinedButton(
                onClick = onCopy,
                enabled = uiState.canCopy
            ) {
                Text("복사")
            }
            OutlinedButton(
                onClick = onUndo,
                enabled = uiState.canUndo
            ) {
                Text("Undo")
            }
        }
    }
}

@Composable
private fun SelectedFileStatus(uiState: WildcardManagerUiState) {
    Text(
        text = uiState.selectedFileDisplayName,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
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
                        color = MaterialTheme.colorScheme.error,
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
