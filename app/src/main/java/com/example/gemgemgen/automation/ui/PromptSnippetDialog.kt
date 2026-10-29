// 역할: 토스 침전 바닥형 3단계 표면 위계(NeuInsetBed + NeuCard)와 상단 툴바 액션 기반 단일 모달 호스트(Single Dialog Host)로 상용구 목록 조회·카테고리 필터링, 상용구 추가/수정, 독립된 카테고리 관리(추가·이름 변경·순서 변경·안전 삭제), 본문 아코디언 토글 및 찾아 바꾸기 화면 전환을 제공하는 다이얼로그입니다.
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.gemgemgen.automation.domain.PromptExperimentPolicy
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.core.android.rememberClipboardGateway
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuModalDialogFrame
import com.example.gemgemgen.ui.theme.NeuPillChip
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlinx.coroutines.delay

/**
 * 상용구 다이얼로그 내부 화면 단계 (Single Dialog Host 원칙에 따라 단일 모달 창 안에서 크로스페이드 전환).
 */
private sealed interface PromptSnippetStage {
    data object Vault : PromptSnippetStage
    data class Form(val editingSnippet: PromptSnippet?) : PromptSnippetStage
    data object ManageCategories : PromptSnippetStage
    data object FindReplace : PromptSnippetStage
}

/**
 * 보관함 카테고리 필터 선택 상태
 */
private sealed interface SnippetCategoryFilter {
    data object All : SnippetCategoryFilter
    data class Named(val category: String) : SnippetCategoryFilter
    data object Uncategorized : SnippetCategoryFilter
}

/**
 * 프롬프트 상용구 관리 다이얼로그
 */
@Composable
fun PromptSnippetDialog(
    snippets: List<PromptSnippet>,
    categories: List<String> = emptyList(),
    currentPromptText: String = "",
    isSelectionPrompt: Boolean = false,
    onAddSnippet: (shortcut: String, content: String, category: String) -> Unit,
    onUpdateSnippet: (id: String, shortcut: String, content: String, category: String) -> Unit = { _, _, _, _ -> },
    onDeleteSnippet: (id: String) -> Unit,
    onBatchReplaceSnippets: (searchText: String, replacementText: String) -> Unit = { _, _ -> },
    onAddCategory: (category: String) -> Unit = {},
    onRenameCategory: (oldCategory: String, newCategory: String) -> Unit = { _, _ -> },
    onDeleteCategory: (category: String) -> Unit = {},
    onMoveCategory: (category: String, delta: Int) -> Unit = { _, _ -> },
    onReorderCategories: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val clipboardGateway = rememberClipboardGateway()

    // 등록된 카테고리 목록 + 상용구에 사용된 카테고리 병합(순서 유지)
    val effectiveCategories = remember(categories, snippets) {
        val list = mutableListOf<String>()
        categories.forEach { cat ->
            val trimmed = cat.trim()
            if (trimmed.isNotEmpty() && list.none { it.equals(trimmed, ignoreCase = true) }) {
                list.add(trimmed)
            }
        }
        snippets.forEach { snippet ->
            val trimmed = snippet.category.trim()
            if (trimmed.isNotEmpty() && list.none { it.equals(trimmed, ignoreCase = true) }) {
                list.add(trimmed)
            }
        }
        list
    }

    // 현재 활성화된 화면 단계 (보관함 vs 폼 vs 카테고리 관리 vs 찾아 바꾸기)
    var activeStage by remember { mutableStateOf<PromptSnippetStage>(PromptSnippetStage.Vault) }

    // 보관함 필터 상태
    var selectedFilter by remember { mutableStateOf<SnippetCategoryFilter>(SnippetCategoryFilter.All) }

    // 선택된 카테고리 필터가 삭제되었거나 더 이상 유효하지 않을 때 전체로 안전 복귀
    LaunchedEffect(effectiveCategories, snippets) {
        when (val current = selectedFilter) {
            SnippetCategoryFilter.All -> Unit
            is SnippetCategoryFilter.Named -> {
                if (effectiveCategories.none { it.equals(current.category, ignoreCase = true) }) {
                    selectedFilter = SnippetCategoryFilter.All
                }
            }
            SnippetCategoryFilter.Uncategorized -> {
                if (snippets.none { it.category.isBlank() }) {
                    selectedFilter = SnippetCategoryFilter.All
                }
            }
        }
    }

    // 폼 입력 상태
    var shortcutInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // 수정 모드로 폼 열기 핸들러
    val openEditFormStage: (PromptSnippet) -> Unit = { snippet ->
        shortcutInput = snippet.shortcut
        categoryInput = snippet.category
        contentInput = snippet.content
        errorMessage = ""
        activeStage = PromptSnippetStage.Form(editingSnippet = snippet)
    }

    // 신규 등록 모드로 폼 열기 핸들러 (현재 필터가 특정 카테고리면 기본 선택값으로 채워줌)
    val openNewFormStage: () -> Unit = {
        shortcutInput = ""
        categoryInput = (selectedFilter as? SnippetCategoryFilter.Named)?.category.orEmpty()
        contentInput = ""
        errorMessage = ""
        activeStage = PromptSnippetStage.Form(editingSnippet = null)
    }

    // 보관함 목록으로 복귀 핸들러
    val backToVault: () -> Unit = {
        shortcutInput = ""
        categoryInput = ""
        contentInput = ""
        errorMessage = ""
        activeStage = PromptSnippetStage.Vault
    }

    // 카테고리 관리 화면 전환 핸들러
    val openManageCategoriesStage: () -> Unit = {
        errorMessage = ""
        activeStage = PromptSnippetStage.ManageCategories
    }

    // 찾아 바꾸기 화면 전환 핸들러
    val openFindReplaceStage: () -> Unit = {
        errorMessage = ""
        activeStage = PromptSnippetStage.FindReplace
    }

    NeuModalDialogFrame(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 헤더 (타이틀 + 닫기 버튼)
            SnippetDialogHeader(
                snippetCount = snippets.size,
                categoryCount = effectiveCategories.size,
                onDismiss = onDismiss
            )

            // 2. 단일 모달 내부 화면 전환 (보관함 목록 ↔ 등록/수정 폼 ↔ 카테고리 관리 ↔ 찾아 바꾸기)
            AnimatedContent(
                targetState = activeStage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                },
                contentKey = { stage ->
                    when (stage) {
                        PromptSnippetStage.Vault -> "Vault"
                        is PromptSnippetStage.Form -> "Form_${stage.editingSnippet?.id ?: "new"}"
                        PromptSnippetStage.ManageCategories -> "ManageCategories"
                        PromptSnippetStage.FindReplace -> "FindReplace"
                    }
                },
                label = "PromptSnippetModalCrossfade"
            ) { stage ->
                when (stage) {
                    PromptSnippetStage.Vault -> {
                        SnippetVaultContent(
                            snippets = snippets,
                            categories = effectiveCategories,
                            selectedFilter = selectedFilter,
                            onSelectFilter = { selectedFilter = it },
                            onAddNewClick = openNewFormStage,
                            onOpenManageCategories = openManageCategoriesStage,
                            onOpenFindReplace = openFindReplaceStage,
                            onEditSnippet = openEditFormStage,
                            onDeleteSnippet = onDeleteSnippet,
                            onCopySnippet = { content ->
                                clipboardGateway.writeText(content)
                            }
                        )
                    }
                    is PromptSnippetStage.Form -> {
                        SnippetFormContent(
                            editingSnippet = stage.editingSnippet,
                            shortcutInput = shortcutInput,
                            categoryInput = categoryInput,
                            contentInput = contentInput,
                            availableCategories = effectiveCategories,
                            errorMessage = errorMessage,
                            currentPromptText = currentPromptText,
                            isSelectionPrompt = isSelectionPrompt,
                            existingShortcuts = snippets
                                .filter { it.id != stage.editingSnippet?.id }
                                .map { it.shortcut.lowercase() },
                            clipboardGateway = clipboardGateway,
                            onShortcutChange = {
                                shortcutInput = it.filterNot { char -> char.isWhitespace() }
                                errorMessage = ""
                            },
                            onCategoryChange = {
                                categoryInput = it.filterNot { char -> char.isWhitespace() }
                                errorMessage = ""
                            },
                            onContentChange = {
                                contentInput = it
                                errorMessage = ""
                            },
                            onCancel = backToVault,
                            onSave = {
                                val shortcut = shortcutInput.trim()
                                val category = categoryInput.trim()
                                val content = contentInput.trim()
                                when {
                                    shortcut.isEmpty() -> errorMessage = "단축어를 입력해 주세요."
                                    content.isEmpty() -> errorMessage = "프롬프트 문구를 입력해 주세요."
                                    snippets.any {
                                        it.id != stage.editingSnippet?.id && it.shortcut.equals(shortcut, ignoreCase = true)
                                    } -> {
                                        errorMessage = "이미 사용 중인 단축어입니다."
                                    }
                                    else -> {
                                        if (stage.editingSnippet != null) {
                                            onUpdateSnippet(stage.editingSnippet.id, shortcut, content, category)
                                        } else {
                                            onAddSnippet(shortcut, content, category)
                                        }
                                        backToVault()
                                    }
                                }
                            }
                        )
                    }
                    PromptSnippetStage.ManageCategories -> {
                        SnippetManageCategoriesContent(
                            categories = effectiveCategories,
                            snippets = snippets,
                            onAddCategory = onAddCategory,
                            onRenameCategory = onRenameCategory,
                            onDeleteCategory = onDeleteCategory,
                            onMoveCategory = onMoveCategory,
                            onReorderCategories = onReorderCategories,
                            onBack = backToVault
                        )
                    }
                    PromptSnippetStage.FindReplace -> {
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
}

// ==========================================================================
// 2. Dialog Header Component
// ==========================================================================

@Composable
private fun SnippetDialogHeader(
    snippetCount: Int,
    categoryCount: Int,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
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
                    text = when {
                        snippetCount == 0 && categoryCount == 0 -> "단축어·카테고리로 긴 문장을 원터치 대치합니다"
                        categoryCount > 0 -> "상용구 ${snippetCount}개 · 카테고리 ${categoryCount}개"
                        else -> "등록된 상용구 ${snippetCount}개 · 원터치 자동 치환"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = AppTheme.colors.textSecondary
                )
            }
        }

        // 닫기(✕) 버튼 (계정 모달과 동일한 원형 36dp 규격)
        Surface(
            shape = CircleShape,
            color = AppTheme.colors.insetBed,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "모달 닫기" }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = AppTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==========================================================================
// 3. Vault Tab Components (List, Filter Chips, Inset Bed, Item, Actions)
// ==========================================================================

/**
 * 보관함 목록 컨텐츠 (상단 툴바 + 카테고리 필터 칩 바 + 침전 바닥형 수직 스크롤 목록)
 */
@Composable
private fun SnippetVaultContent(
    snippets: List<PromptSnippet>,
    categories: List<String>,
    selectedFilter: SnippetCategoryFilter,
    onSelectFilter: (SnippetCategoryFilter) -> Unit,
    onAddNewClick: () -> Unit,
    onOpenManageCategories: () -> Unit,
    onOpenFindReplace: () -> Unit,
    onEditSnippet: (PromptSnippet) -> Unit,
    onDeleteSnippet: (id: String) -> Unit,
    onCopySnippet: (content: String) -> Unit
) {
    val filteredSnippets = remember(snippets, selectedFilter) {
        when (selectedFilter) {
            SnippetCategoryFilter.All -> snippets
            is SnippetCategoryFilter.Named -> snippets.filter {
                it.category.trim().equals(selectedFilter.category, ignoreCase = true)
            }
            SnippetCategoryFilter.Uncategorized -> snippets.filter {
                it.category.isBlank()
            }
        }
    }

    val hasUncategorized = remember(snippets) {
        snippets.any { it.category.isBlank() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 상단 액션 툴바: [+ 새 상용구] & [카테고리] & [찾아 바꾸기]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuButton(
                onClick = onAddNewClick,
                isPrimary = true,
                shape = RoundedCornerShape(11.dp),
                contentModifier = Modifier.padding(PaddingValues(0.dp)),
                modifier = Modifier
                    .weight(1.15f)
                    .height(38.dp)
                    .semantics { contentDescription = "새 상용구 등록" }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "새 상용구",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            NeuButton(
                onClick = onOpenManageCategories,
                isPrimary = false,
                shape = RoundedCornerShape(11.dp),
                contentModifier = Modifier.padding(PaddingValues(0.dp)),
                modifier = Modifier
                    .weight(0.95f)
                    .height(38.dp)
                    .semantics { contentDescription = "카테고리 관리" }
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "카테고리",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }

            NeuButton(
                onClick = onOpenFindReplace,
                enabled = snippets.isNotEmpty(),
                isPrimary = false,
                shape = RoundedCornerShape(11.dp),
                contentModifier = Modifier.padding(PaddingValues(0.dp)),
                modifier = Modifier
                    .weight(0.95f)
                    .height(38.dp)
                    .semantics { contentDescription = "찾아 바꾸기" }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "찾아 바꾸기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }
        }

        // 상단 카테고리 필터 칩 바 (카테고리가 있거나 상용구가 있을 때 노출)
        if (categories.isNotEmpty() || snippets.isNotEmpty()) {
            SnippetCategoryFilterBar(
                categories = categories,
                hasUncategorized = hasUncategorized && categories.isNotEmpty(),
                selectedFilter = selectedFilter,
                onSelectFilter = onSelectFilter
            )
        }

        // 본문: 빈 화면 안내 또는 토스 침전 바닥(NeuInsetBed) 안의 LazyColumn
        if (snippets.isEmpty()) {
            EmptySnippetVaultView(
                onAddNewClick = onAddNewClick
            )
        } else if (filteredSnippets.isEmpty()) {
            EmptyCategoryFilterView(
                filterName = when (selectedFilter) {
                    SnippetCategoryFilter.All -> "전체"
                    is SnippetCategoryFilter.Named -> selectedFilter.category
                    SnippetCategoryFilter.Uncategorized -> "미분류"
                },
                onResetFilter = { onSelectFilter(SnippetCategoryFilter.All) },
                onAddNewInCategory = onAddNewClick
            )
        } else {
            NeuInsetBed(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 440.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = AppTheme.colors.insetBed
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    item(key = "header_snippets") {
                        val headerTitle = when (selectedFilter) {
                            SnippetCategoryFilter.All -> "보관된 상용구 ${filteredSnippets.size}개"
                            is SnippetCategoryFilter.Named -> "[${selectedFilter.category}] 상용구 ${filteredSnippets.size}개"
                            SnippetCategoryFilter.Uncategorized -> "[미분류] 상용구 ${filteredSnippets.size}개"
                        }
                        SnippetSectionHeader(
                            title = headerTitle,
                            hint = "터치하여 본문 펼치기",
                            indicatorColor = AppTheme.colors.primary
                        )
                    }
                    items(
                        items = filteredSnippets,
                        key = { it.id }
                    ) { snippet ->
                        SnippetCardItem(
                            snippet = snippet,
                            onEdit = { onEditSnippet(snippet) },
                            onDelete = { onDeleteSnippet(snippet.id) },
                            onCopy = { onCopySnippet(snippet.content) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 보관함 상단 가로 스크롤 카테고리 필터 칩 바 (`[전체] [스타일] [카메라] ... [미분류]`)
 */
@Composable
private fun SnippetCategoryFilterBar(
    categories: List<String>,
    hasUncategorized: Boolean,
    selectedFilter: SnippetCategoryFilter,
    onSelectFilter: (SnippetCategoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuPillChip(
            text = "전체",
            selected = selectedFilter is SnippetCategoryFilter.All,
            onClick = { onSelectFilter(SnippetCategoryFilter.All) },
            height = 30.dp,
            textStyle = MaterialTheme.typography.labelSmall
        )

        categories.forEach { category ->
            val isSelected = selectedFilter is SnippetCategoryFilter.Named &&
                selectedFilter.category.equals(category, ignoreCase = true)
            NeuPillChip(
                text = category,
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        onSelectFilter(SnippetCategoryFilter.All)
                    } else {
                        onSelectFilter(SnippetCategoryFilter.Named(category))
                    }
                },
                height = 30.dp,
                textStyle = MaterialTheme.typography.labelSmall
            )
        }

        if (hasUncategorized) {
            val isUncategorizedSelected = selectedFilter is SnippetCategoryFilter.Uncategorized
            NeuPillChip(
                text = "미분류",
                selected = isUncategorizedSelected,
                onClick = {
                    if (isUncategorizedSelected) {
                        onSelectFilter(SnippetCategoryFilter.All)
                    } else {
                        onSelectFilter(SnippetCategoryFilter.Uncategorized)
                    }
                },
                height = 30.dp,
                textStyle = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * 특정 카테고리 필터를 선택했을 때 해당 카테고리에 상용구가 없을 때 보여주는 안내 뷰
 */
@Composable
private fun EmptyCategoryFilterView(
    filterName: String,
    onResetFilter: () -> Unit,
    onAddNewInCategory: () -> Unit
) {
    NeuInsetBed(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = AppTheme.colors.insetBed
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "'$filterName' 카테고리에 상용구가 없습니다",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = "이 카테고리에 새 상용구를 바로 등록하거나 전체 목록으로 돌아갈 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = AppTheme.colors.textSecondary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuButton(
                    onClick = onResetFilter,
                    isPrimary = false,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("전체 보기", style = MaterialTheme.typography.labelMedium)
                }
                NeuButton(
                    onClick = onAddNewInCategory,
                    isPrimary = true,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("이 카테고리에 등록", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 목록 상단에 표시되는 가벼운 섹션 카운트 헤더
 */
@Composable
private fun SnippetSectionHeader(
    title: String,
    hint: String,
    indicatorColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontSize = 11.sp
        )
    }
}

/**
 * 상용구 카드 아이템 (토스 스타일 부유 카드, 좌측 5dp 포인트 액센트 바, 카테고리 보조 뱃지 + 단축어 뱃지, 본문 아코디언 토글, 원터치 복사, 2단계 삭제 확인)
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        elevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 좌측 5dp 풀높이 플러시 포인트 액센트 바 (토스 계정 카드 스타일)
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(5.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .background(AppTheme.colors.primary)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        top = 10.dp,
                        end = 12.dp,
                        bottom = 10.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 상단: [카테고리 보조 뱃지 + 단축어 뱃지] + 우측 액션 바 (복사, 수정, 삭제)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (snippet.category.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppTheme.colors.insetBed
                            ) {
                                Text(
                                    text = snippet.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppTheme.colors.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "📋 ${snippet.shortcut}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
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

                // 본문 영역 (순백색 표면 위 가독성 높은 텍스트)
                Text(
                    text = snippet.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textPrimary,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                // 하단 펼치기/접기 안내 힌트
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
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
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
        enter = fadeIn(tween(140)) + expandVertically(tween(140)),
        exit = fadeOut(tween(100)) + shrinkVertically(tween(100)),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "정말 삭제할까요?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "취소",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    TextButton(
                        onClick = onConfirmDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
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
// 4. Form Components (Form, Mode Banner, Category Quick Chips, Content Toolbar)
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
 * 상용구 등록 / 수정 폼 컨텐츠
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SnippetFormContent(
    editingSnippet: PromptSnippet?,
    shortcutInput: String,
    categoryInput: String,
    contentInput: String,
    availableCategories: List<String>,
    errorMessage: String,
    currentPromptText: String,
    isSelectionPrompt: Boolean = false,
    existingShortcuts: List<String>,
    clipboardGateway: ClipboardGateway,
    onShortcutChange: (String) -> Unit,
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
    val currentShortcut = shortcutState.text.toString()
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

        // 2. 단축어 입력
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

// ==========================================================================
// 5. Category Management Stage (독립된 카테고리 관리 화면)
// ==========================================================================

@Composable
private fun SnippetManageCategoriesContent(
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

    var localCategories by remember { mutableStateOf(categories) }
    var draggingCategory by remember { mutableStateOf<String?>(null) }
    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }

    LaunchedEffect(categories) {
        if (draggingCategory == null) {
            localCategories = categories
        }
    }

    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }

    fun handleDragStart(category: String) {
        val index = localCategories.indexOfFirst { it.equals(category, ignoreCase = true) }
        if (index == -1) return
        draggingCategory = category
        initialDragIndex = index
        dragAccumulatedY = 0f
    }

    fun handleDrag(deltaY: Float) {
        val currentCat = draggingCategory ?: return
        var curIndex = localCategories.indexOfFirst { it.equals(currentCat, ignoreCase = true) }
        if (curIndex == -1) return
        dragAccumulatedY += deltaY

        val threshold = rowHeightPx * 0.5f
        while (dragAccumulatedY > threshold && curIndex < localCategories.lastIndex) {
            val target = curIndex + 1
            localCategories = PromptExperimentPolicy.moveElementTo(localCategories, curIndex, target)
            curIndex = target
            dragAccumulatedY -= rowHeightPx
        }
        while (dragAccumulatedY < -threshold && curIndex > 0) {
            val target = curIndex - 1
            localCategories = PromptExperimentPolicy.moveElementTo(localCategories, curIndex, target)
            curIndex = target
            dragAccumulatedY += rowHeightPx
        }
    }

    fun handleDragEnd() {
        val currentCat = draggingCategory
        val from = initialDragIndex
        val to = if (currentCat != null) {
            localCategories.indexOfFirst { it.equals(currentCat, ignoreCase = true) }
        } else {
            -1
        }
        if (from != null && to != -1 && from != to) {
            onReorderCategories(from, to)
        }
        draggingCategory = null
        initialDragIndex = null
        dragAccumulatedY = 0f
    }

    fun handleDragCancel() {
        localCategories = categories
        draggingCategory = null
        initialDragIndex = null
        dragAccumulatedY = 0f
    }

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
        if (localCategories.isEmpty()) {
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
                            enabled = draggingCategory == null
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    localCategories.forEachIndexed { index, category ->
                        key(category.lowercase()) {
                            val isDragging = draggingCategory?.equals(category, ignoreCase = true) == true
                            val isEditingThis = editingTarget?.equals(category, ignoreCase = true) == true
                            val isConfirmingDelete = confirmingDeleteCategory?.equals(category, ignoreCase = true) == true
                            val count = snippets.count { it.category.trim().equals(category, ignoreCase = true) }
                            val number = index + 1

                            val currentOnDragStart by rememberUpdatedState { handleDragStart(category) }
                            val currentOnDrag by rememberUpdatedState { deltaY: Float -> handleDrag(deltaY) }
                            val currentOnDragEnd by rememberUpdatedState { handleDragEnd() }
                            val currentOnDragCancel by rememberUpdatedState { handleDragCancel() }

                            NeuCard(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = when {
                                    isDragging -> AppTheme.colors.accent.copy(alpha = 0.14f)
                                    isEditingThis -> AppTheme.colors.primary.copy(alpha = 0.1f)
                                    else -> AppTheme.colors.card
                                },
                                borderColor = when {
                                    isDragging -> AppTheme.colors.accent
                                    isEditingThis -> AppTheme.colors.primary
                                    else -> AppTheme.colors.cardBorder
                                },
                                elevation = if (isDragging) 8.dp else 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragAccumulatedY else 0f
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
                                                enabled = index < localCategories.lastIndex,
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "아래로 이동",
                                                    tint = if (index < localCategories.lastIndex) AppTheme.colors.textSecondary else AppTheme.colors.textSecondary.copy(alpha = 0.3f),
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
                                                    TextButton(
                                                        onClick = { confirmingDeleteCategory = null },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "취소",
                                                            fontSize = 11.sp,
                                                            color = AppTheme.colors.textSecondary
                                                        )
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            confirmingDeleteCategory = null
                                                            if (editingTarget?.equals(category, ignoreCase = true) == true) {
                                                                editingTarget = null
                                                                editingName = ""
                                                            }
                                                            onDeleteCategory(category)
                                                        },
                                                        colors = ButtonDefaults.textButtonColors(
                                                            contentColor = MaterialTheme.colorScheme.error
                                                        ),
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

