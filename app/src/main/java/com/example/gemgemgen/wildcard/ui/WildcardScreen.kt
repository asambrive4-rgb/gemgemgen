// 역할: 와일드카드 세트 목록을 다단(Flow) 칩 및 접기/펼치기·검색 필터로 관리하고 에디터 편집, 상용구 칩 연동, 분류 작업을 제공하는 토스 카드 스타일 메인 탭 화면 UI입니다 (TextFieldState 기반 에디터 국소 리컴포지션 격리).
package com.example.gemgemgen.wildcard.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.gemgemgen.ui.rememberIsImeVisible
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuDropdownMenu
import com.example.gemgemgen.ui.theme.NeuDropdownMenuDivider
import com.example.gemgemgen.ui.theme.NeuInsetBed
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
    val isKeyboardVisible = rememberIsImeVisible()
    val isDirty = uiState.editor.hasUnsavedChanges
    val fileItems = remember(uiState.files, selectedFile?.id, isDirty, uiState.linkedFileNames) {
        uiState.files.map { file ->
            val isSelected = selectedFile?.id == file.id
            val isLinked = file.fileName in uiState.linkedFileNames
            WildcardFileUiItem(
                file = file,
                displayName = if (isSelected && isDirty) "${file.fileName} *" else file.fileName,
                isSelected = isSelected,
                isLinkedToAutocomplete = isLinked
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
            // 1구역: Top Strip (파일 탭 목록 + 새 파일/삭제/더보기 버튼)
            FileTabsSection(
                fileItems = fileItems,
                onFileClick = actions::selectFile,
                canCreateFile = uiState.canCreateFile,
                canDelete = uiState.canDelete,
                onRequestNewFile = actions::requestNewFile,
                onRequestDelete = actions::requestDeleteSelectedFile,
                environmentStatus = environmentStatus,
                setupInfo = environmentSetupInfo,
                onRefresh = actions::onRefresh,
                onSelectFolder = actions::onSelectFolder
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
                            val isLinked = uiState.isCurrentFileLinkedToAutocomplete
                            Surface(
                                onClick = { actions.toggleAutocompleteLink(selectedFile) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLinked) {
                                    AppTheme.colors.wildcardItemPrimary.copy(alpha = 0.15f)
                                } else {
                                    Color.Transparent
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isLinked) {
                                        AppTheme.colors.wildcardItemPrimary.copy(alpha = 0.45f)
                                    } else {
                                        AppTheme.colors.cardBorder
                                    }
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .padding(end = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmarks,
                                        contentDescription = null,
                                        tint = if (isLinked) AppTheme.colors.wildcardItemPrimary else AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isLinked) "상용구 연동 됨" else "상용구 연동",
                                        color = if (isLinked) AppTheme.colors.wildcardItemPrimary else AppTheme.colors.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isLinked) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                            TextButton(
                                onClick = actions.classify::requestClassify,
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
                        WildcardEditorTextField(
                            fileKey = selectedFile?.id,
                            editingText = editingText,
                            canEditText = uiState.canEditText,
                            onTextChange = actions::onTextChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                    onDeriveFile = actions::requestDeriveFile,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileTabsSection(
    fileItems: List<WildcardFileUiItem>,
    onFileClick: (WildcardTextFile) -> Unit,
    canCreateFile: Boolean,
    canDelete: Boolean,
    onRequestNewFile: () -> Unit,
    onRequestDelete: () -> Unit,
    environmentStatus: EnvironmentStatus,
    setupInfo: EnvironmentSetupInfo,
    onRefresh: () -> Unit,
    onSelectFolder: () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isMoreMenuOpen by remember { mutableStateOf(false) }

    val hasFolderIssue = !environmentStatus.isWildcardDirectoryAccessible || !environmentStatus.isWildcardDirectoryWritable
    val folderPathDesc = WildcardFolderPathFormatter.summaryPath(setupInfo.wildcardDirectoryPath)

    val filteredItems = remember(fileItems, searchQuery) {
        if (searchQuery.isBlank()) {
            fileItems
        } else {
            fileItems.filter {
                it.file.fileName.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    NeuCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        elevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 헤더: [파일 목록 (N) ▼/▲]  ...  [검색 🔍] [새 파일 ➕] [삭제 🗑️] [더보기 ⋮]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 좌측: 라벨 + 접기/펼치기 토글
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "와일드카드 (${fileItems.size})",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "접기" else "펼치기",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 우측 액션 버튼들 (검색, 새 파일, 삭제, 더보기)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    FileTabIconActionButton(
                        icon = Icons.Default.Search,
                        contentDescription = "검색",
                        enabled = fileItems.isNotEmpty(),
                        activeTint = if (isSearchActive) AppTheme.colors.primary else AppTheme.colors.textSecondary,
                        activeBorderColor = AppTheme.colors.cardBorder,
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                            if (isSearchActive) isExpanded = true
                        }
                    )
                    FileTabIconActionButton(
                        icon = Icons.Default.Add,
                        contentDescription = "새 파일",
                        enabled = canCreateFile,
                        activeTint = AppTheme.colors.primary,
                        activeBorderColor = AppTheme.colors.cardBorder,
                        onClick = onRequestNewFile
                    )
                    FileTabIconActionButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "삭제",
                        enabled = canDelete,
                        activeTint = MaterialTheme.colorScheme.error,
                        activeBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        onClick = onRequestDelete
                    )
                    Box {
                        FileTabIconActionButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "더보기",
                            enabled = true,
                            activeTint = if (hasFolderIssue) MaterialTheme.colorScheme.error else AppTheme.colors.textSecondary,
                            activeBorderColor = AppTheme.colors.cardBorder,
                            onClick = { isMoreMenuOpen = true }
                        )

                        NeuDropdownMenu(
                            expanded = isMoreMenuOpen,
                            onDismissRequest = { isMoreMenuOpen = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .widthIn(max = 240.dp)
                            ) {
                                Text(
                                    text = "와일드카드 저장소",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = folderPathDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!environmentStatus.isWildcardDirectoryAccessible) {
                                    Text(
                                        text = "와일드카드 폴더를 선택해주세요.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else if (!environmentStatus.isWildcardDirectoryWritable) {
                                    Text(
                                        text = "파일 편집을 위해 폴더를 다시 선택해주세요.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            NeuDropdownMenuDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (setupInfo.wildcardDirectoryPath.isBlank()) "폴더 선택" else "폴더 변경",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isMoreMenuOpen = false
                                    onSelectFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "새로고침",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = AppTheme.colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = AppTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isMoreMenuOpen = false
                                    onRefresh()
                                }
                            )
                        }
                    }
                }
            }

            // 인셋 검색창 (검색 활성화 시 노출)
            if (isSearchActive) {
                NeuInsetBed(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = AppTheme.colors.textPrimary,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(AppTheme.colors.primary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "파일명 검색...",
                                        color = AppTheme.colors.textSecondary.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "검색어 지우기",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 칩 목록 영역
            if (fileItems.isEmpty()) {
                Text(
                    text = "표시할 txt 파일이 없습니다.",
                    color = AppTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else if (filteredItems.isEmpty()) {
                Text(
                    text = "'$searchQuery' 일치하는 파일이 없습니다.",
                    color = AppTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else if (isExpanded) {
                // 펼침 상태: FlowRow 다단 칩 (최대 높이 160dp 내 스크롤로 20개 이상도 쾌적하게 조망)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredItems.forEach { item ->
                            key(item.file.id) {
                                val baseName = item.displayName.removeSuffix(".txt")
                                val label = if (item.isLinkedToAutocomplete) "🔖 $baseName" else baseName
                                NeuPillChip(
                                    text = label,
                                    selected = item.isSelected,
                                    onClick = { onFileClick(item.file) },
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // 접힘 상태: 가로 1줄 스크롤 (에디터 공간을 최대로 넓게 쓸 때)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.file.id }
                    ) { item ->
                        val baseName = item.displayName.removeSuffix(".txt")
                        val label = if (item.isLinkedToAutocomplete) "🔖 $baseName" else baseName
                        NeuPillChip(
                            text = label,
                            selected = item.isSelected,
                            onClick = { onFileClick(item.file) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
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
    @Suppress("UNUSED_PARAMETER") activeBorderColor: Color,
    onClick: () -> Unit
) {
    val actionBtnShape = RoundedCornerShape(10.dp)
    val bgColor = if (enabled) {
        activeTint.copy(alpha = 0.14f)
    } else {
        AppTheme.colors.insetBed.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(actionBtnShape)
            .background(bgColor)
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
    onDeriveFile: () -> Unit,
    onExit: () -> Unit
) {
    val barShape = RoundedCornerShape(14.dp)
    val btnPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
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
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Text(text = "전체", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NeuButton(
            onClick = onDeselectAll,
            enabled = uiState.canDeselectAllLines,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Text(text = "해제", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        NeuButton(
            onClick = onCompose,
            enabled = uiState.canComposeDynamicPrompt,
            isPrimary = false,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.6f)
                .height(42.dp)
        ) {
            Text(
                text = "다이나믹",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NeuButton(
            onClick = onDeriveFile,
            enabled = uiState.canDeriveFile,
            isPrimary = true,
            shape = barShape,
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.8f)
                .height(42.dp)
        ) {
            Text(
                text = "새 파일로",
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
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.1f)
                .height(42.dp)
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
    val barShape = RoundedCornerShape(14.dp)
    val btnPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    val iconOnlyPadding = PaddingValues(0.dp)
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
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1.15f)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "아래 붙여넣기",
                fontSize = 11.5.sp,
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
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "붙여넣기",
                fontSize = 11.5.sp,
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
            contentPadding = btnPadding,
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "저장",
                fontSize = 11.5.sp,
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
            contentPadding = iconOnlyPadding,
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "복사", modifier = Modifier.size(18.dp))
        }

        NeuButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            isPrimary = false,
            shape = barShape,
            contentPadding = iconOnlyPadding,
            modifier = Modifier
                .width(42.dp)
                .height(42.dp)
        ) {
            Icon(imageVector = Icons.Default.Undo, contentDescription = "실행 취소", modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 와일드카드 텍스트 에디터 영역을 TextFieldState 기반 국소 컴포저블로 분리하여
 * 한글 조합(IME) 중 커서 튐과 상위 전체 화면의 불필요한 리컴포지션을 방지합니다.
 */
@Composable
private fun WildcardEditorTextField(
    fileKey: String?,
    editingText: String,
    canEditText: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val editorState = rememberSyncedTextFieldState(
        value = editingText,
        onValueChange = onTextChange,
        resetKey = fileKey
    )

    OutlinedTextField(
        state = editorState,
        modifier = modifier,
        enabled = canEditText,
        placeholder = { Text(text = "파일을 선택하거나 새 txt 파일을 만들어주세요.") },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 8),
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
