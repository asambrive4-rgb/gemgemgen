// 역할: 3-in-1 조약돌 세그먼트 탭(보관함 ↔ 등록/수정 ↔ 찾아 바꾸기)으로 상용구를 조회, 추가, 수정, 삭제하고 본문 일괄 치환을 제공하는 다이얼로그입니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.core.android.rememberClipboardGateway
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuModalDialogFrame
import com.example.gemgemgen.ui.theme.NeuSegmentedTabBar
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlinx.coroutines.delay

/**
 * 상용구 다이얼로그 탭 종류 (보관함 vs 등록/수정 폼 vs 찾아 바꾸기)
 */
private enum class SnippetTab {
    VAULT,        // 보관함 (목록 및 빠른 복사/삭제/상세)
    FORM,         // 새 상용구 등록 또는 기존 상용구 수정 폼
    FIND_REPLACE  // 단어 검색 및 본문 일괄 변경
}

/**
 * 프롬프트 상용구 관리 다이얼로그
 */
@Composable
fun PromptSnippetDialog(
    snippets: List<PromptSnippet>,
    currentPromptText: String = "",
    isSelectionPrompt: Boolean = false,
    onAddSnippet: (shortcut: String, content: String) -> Unit,
    onUpdateSnippet: (id: String, shortcut: String, content: String) -> Unit = { _, _, _ -> },
    onDeleteSnippet: (id: String) -> Unit,
    onBatchReplaceSnippets: (searchText: String, replacementText: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val clipboardGateway = rememberClipboardGateway()

    // 현재 활성화된 탭 및 편집 상태
    var currentTab by remember { mutableStateOf(SnippetTab.VAULT) }
    var editingSnippet by remember { mutableStateOf<PromptSnippet?>(null) }

    // 폼 입력 상태
    var shortcutInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // 수정 모드 시작 핸들러
    val startEditing: (PromptSnippet) -> Unit = { snippet ->
        editingSnippet = snippet
        shortcutInput = snippet.shortcut
        contentInput = snippet.content
        errorMessage = ""
        currentTab = SnippetTab.FORM
    }

    // 신규 모드로 폼 열기 핸들러
    val startNew: () -> Unit = {
        editingSnippet = null
        shortcutInput = ""
        contentInput = ""
        errorMessage = ""
        currentTab = SnippetTab.FORM
    }

    // 보관함 탭으로 복귀 핸들러
    val backToVault: () -> Unit = {
        editingSnippet = null
        shortcutInput = ""
        contentInput = ""
        errorMessage = ""
        currentTab = SnippetTab.VAULT
    }

    // 찾아 바꾸기 탭 전환 핸들러
    val openFindReplace: () -> Unit = {
        editingSnippet = null
        errorMessage = ""
        currentTab = SnippetTab.FIND_REPLACE
    }

    NeuModalDialogFrame(
        onDismiss = onDismiss
    ) {
        // 1. 헤더 (타이틀 + 닫기 버튼)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppTheme.colors.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "프롬프트 상용구",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "단축어로 긴 문장을 원터치 대치합니다",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            // 닫기 X 버튼 (최소 44dp 확보)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. 조약돌 세그먼트 탭바 (보관함 vs 등록/수정 vs 찾아 바꾸기)
        NeuSegmentedTabBar(
            items = listOf(SnippetTab.VAULT, SnippetTab.FORM, SnippetTab.FIND_REPLACE),
            selectedItem = currentTab,
            onItemSelected = { tab ->
                when (tab) {
                    SnippetTab.VAULT -> backToVault()
                    SnippetTab.FORM -> if (currentTab != SnippetTab.FORM) startNew()
                    SnippetTab.FIND_REPLACE -> openFindReplace()
                }
            },
            itemLabel = { tab ->
                when (tab) {
                    SnippetTab.VAULT -> "보관함 (${snippets.size})"
                    SnippetTab.FORM -> if (editingSnippet != null) "✏️ 수정" else "+ 새 상용구"
                    SnippetTab.FIND_REPLACE -> "🔍 찾아 바꾸기"
                }
            },
            height = 36.dp,
            shape = RoundedCornerShape(14.dp),
            pillShape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(4.dp),
            tabSpacing = 4.dp
        )

        // 3. 탭별 컨텐츠 (크로스페이드 애니메이션 전환)
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
            },
            label = "SnippetTabContentCrossfade"
        ) { tab ->
            when (tab) {
                SnippetTab.VAULT -> {
                    SnippetVaultContent(
                        snippets = snippets,
                        onEditSnippet = startEditing,
                        onDeleteSnippet = onDeleteSnippet,
                        onCopySnippet = { content ->
                            clipboardGateway.writeText(content)
                        },
                        onAddNewClick = startNew
                    )
                }
                SnippetTab.FORM -> {
                    SnippetFormContent(
                        editingSnippet = editingSnippet,
                        shortcutInput = shortcutInput,
                        contentInput = contentInput,
                        errorMessage = errorMessage,
                        currentPromptText = currentPromptText,
                        isSelectionPrompt = isSelectionPrompt,
                        existingShortcuts = snippets
                            .filter { it.id != editingSnippet?.id }
                            .map { it.shortcut.lowercase() },
                        clipboardGateway = clipboardGateway,
                        onShortcutChange = {
                            shortcutInput = it.filterNot { char -> char.isWhitespace() }
                            errorMessage = ""
                        },
                        onContentChange = {
                            contentInput = it
                            errorMessage = ""
                        },
                        onCancel = backToVault,
                        onSave = {
                            val shortcut = shortcutInput.trim()
                            val content = contentInput.trim()
                            when {
                                shortcut.isEmpty() -> errorMessage = "단축어를 입력해 주세요."
                                content.isEmpty() -> errorMessage = "프롬프트 문구를 입력해 주세요."
                                snippets.any {
                                    it.id != editingSnippet?.id && it.shortcut.equals(shortcut, ignoreCase = true)
                                } -> {
                                    errorMessage = "이미 사용 중인 단축어입니다."
                                }
                                else -> {
                                    if (editingSnippet != null) {
                                        onUpdateSnippet(editingSnippet!!.id, shortcut, content)
                                    } else {
                                        onAddSnippet(shortcut, content)
                                    }
                                    backToVault()
                                }
                            }
                        }
                    )
                }
                SnippetTab.FIND_REPLACE -> {
                    PromptSnippetFindReplaceView(
                        snippets = snippets,
                        onBatchReplace = onBatchReplaceSnippets,
                        onCancel = backToVault
                    )
                }
            }
        }
    }
}

// ==========================================================================
// 2. Dialog Chrome Components (Header & Segment Tabs)
// ==========================================================================

@Composable
private fun SnippetDialogHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = AppTheme.colors.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "프롬프트 상용구",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "단축어로 긴 문장을 원터치 대치합니다",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
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
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SnippetDialogTabBar(
    currentTab: SnippetTab,
    snippetCount: Int,
    isEditing: Boolean,
    onSelectVault: () -> Unit,
    onSelectForm: () -> Unit,
    onSelectFindReplace: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NeuSegmentedTabBar(
        items = listOf(SnippetTab.VAULT, SnippetTab.FORM, SnippetTab.FIND_REPLACE),
        selectedItem = currentTab,
        onItemSelected = { tab ->
            when (tab) {
                SnippetTab.VAULT -> onSelectVault()
                SnippetTab.FORM -> onSelectForm()
                SnippetTab.FIND_REPLACE -> onSelectFindReplace()
            }
        },
        itemLabel = { tab ->
            when (tab) {
                SnippetTab.VAULT -> "보관함 ($snippetCount)"
                SnippetTab.FORM -> if (isEditing) "✏️ 수정" else "+ 새 상용구"
                SnippetTab.FIND_REPLACE -> "🔍 찾아 바꾸기"
            }
        },
        height = 36.dp,
        shape = RoundedCornerShape(14.dp),
        pillShape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(4.dp),
        tabSpacing = 4.dp,
        modifier = modifier
    )
}

// ==========================================================================
// 3. Vault Tab Components (List, Item, Actions, Delete Confirm)
// ==========================================================================

/**
 * 탭 1: 보관함 목록 컨텐츠
 */
@Composable
private fun SnippetVaultContent(
    snippets: List<PromptSnippet>,
    onEditSnippet: (PromptSnippet) -> Unit,
    onDeleteSnippet: (id: String) -> Unit,
    onCopySnippet: (content: String) -> Unit,
    onAddNewClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (snippets.isEmpty()) {
            // 빈 상태 (Empty State)
            EmptySnippetVaultView(
                onAddNewClick = onAddNewClick
            )
        } else {
            // 보관 중인 상용구 목록 (시원한 세로 스크롤)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                snippets.forEach { snippet ->
                    SnippetCardItem(
                        snippet = snippet,
                        onEdit = { onEditSnippet(snippet) },
                        onDelete = { onDeleteSnippet(snippet.id) },
                        onCopy = { onCopySnippet(snippet.content) }
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 하단 새 상용구 추가 소프트 틴트 무테두리 버튼
                Surface(
                    onClick = onAddNewClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppTheme.colors.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "새 상용구 등록하기",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AppTheme.colors.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 상용구 카드 아이템 (3D 조약돌 카드, 긴 본문 아코디언 토글, 복사 피드백, 2단계 삭제 확인)
 */
@Composable
private fun SnippetCardItem(
    snippet: PromptSnippet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }

    // 복사 피드백 자동 복구 (1.5초)
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1500)
            isCopied = false
        }
    }

    NeuCard(
        shape = RoundedCornerShape(14.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 상단: 단축어 뱃지 + 우측 액션 바 (복사, 수정, 삭제)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppTheme.colors.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "📋 ${snippet.shortcut}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                SnippetCardActions(
                    isCopied = isCopied,
                    onCopy = {
                        onCopy()
                        isCopied = true
                    },
                    onEdit = onEdit,
                    onDeleteRequest = { isConfirmingDelete = true }
                )
            }

            // 본문 영역 (클릭 시 펼치기/접기 아코디언)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.colors.insetBed.copy(alpha = 0.5f))
                    .clickable { isExpanded = !isExpanded }
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = snippet.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textPrimary,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "접기" else "전체보기",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = AppTheme.colors.textSecondary
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 삭제 확인 바 (2단계 실수 방지)
            SnippetDeleteConfirmBar(
                visible = isConfirmingDelete,
                onCancel = { isConfirmingDelete = false },
                onConfirmDelete = {
                    isConfirmingDelete = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun SnippetCardActions(
    isCopied: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = onCopy,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = if (isCopied) "복사됨" else "복사",
                tint = if (isCopied) AppTheme.colors.primary else AppTheme.colors.textSecondary,
                modifier = Modifier.size(17.dp)
            )
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "수정",
                tint = AppTheme.colors.accent,
                modifier = Modifier.size(17.dp)
            )
        }

        IconButton(
            onClick = onDeleteRequest,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "삭제",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SnippetDeleteConfirmBar(
    visible: Boolean,
    onCancel: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "정말 삭제할까요?",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("취소", fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                    }
                    TextButton(
                        onClick = onConfirmDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("삭제", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 상용구가 비어 있을 때 노출되는 빈 화면 뷰
 */
@Composable
private fun EmptySnippetVaultView(
    onAddNewClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "등록된 상용구가 없습니다",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "자주 쓰는 긴 프롬프트에 짧은 단축어를 붙여 저장해 보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = AppTheme.colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        NeuButton(
            onClick = onAddNewClick,
            modifier = Modifier.fillMaxWidth(),
            isPrimary = true
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text("직접 새 상용구 작성하기")
            }
        }
    }
}

// ==========================================================================
// 4. Form Tab Components (Form, Mode Banner, Content Toolbar)
// ==========================================================================

@Composable
private fun SnippetFormHeaderBanner(
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
                    "단축어를 입력창에 치면 아래 프롬프트로 자동 대치됩니다."
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
private fun SnippetFormContentToolbar(
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
 * 탭 2: 상용구 등록 / 수정 폼 컨텐츠
 */
@Composable
private fun SnippetFormContent(
    editingSnippet: PromptSnippet?,
    shortcutInput: String,
    contentInput: String,
    errorMessage: String,
    currentPromptText: String,
    isSelectionPrompt: Boolean = false,
    existingShortcuts: List<String>,
    clipboardGateway: ClipboardGateway,
    onShortcutChange: (String) -> Unit,
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
    val contentState = rememberSyncedTextFieldState(
        value = contentInput,
        onValueChange = onContentChange,
        resetKey = editingSnippet?.id
    )
    val currentShortcut = shortcutState.text.toString()
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

        // 1. 단축어 입력
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "단축어",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            OutlinedTextField(
                state = shortcutState,
                label = { Text("호출 단축어 (예: 고화질)") },
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
        }

        // 2. 치환될 프롬프트 문구 입력 & 툴바
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

        // 3. 하단 액션 버튼 (취소 / 저장)
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
