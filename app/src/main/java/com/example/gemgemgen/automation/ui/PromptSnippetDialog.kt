// 역할: 토스 침전 바닥형 표면 위계와 상단 툴바 액션 기반 단일 모달 호스트(Single Dialog Host)로 상용구(다중 단축어 포함) 목록 조회·필터링, 아코디언 토글 및 흔들림 없는 등록 폼·카테고리 관리·찾아 바꾸기 서브 스테이지 전환을 제공하는 다이얼로그입니다.
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.core.android.rememberClipboardGateway
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuModalDialogFrame
import com.example.gemgemgen.ui.theme.NeuPillButton
import com.example.gemgemgen.ui.theme.NeuPillChip
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
 * 타이핑 중 매 글자마다 상위 다이얼로그 전체가 리컴포지션되는 현상을 막기 위한 폼 드래프트 홀더.
 */
private class SnippetFormDraftHolder {
    var shortcutInput: String = ""
    var categoryInput: String = ""
    var contentInput: String = ""
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
    onAddSnippet: (shortcut: String, additionalShortcuts: List<String>, content: String, category: String) -> Unit,
    onUpdateSnippet: (id: String, shortcut: String, additionalShortcuts: List<String>, content: String, category: String) -> Unit = { _, _, _, _, _ -> },
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

    // 폼 입력 상태 (단순 타이핑 중 상위 모달 전체 리컴포지션을 유발하지 않도록 텍스트는 DraftHolder에 보관)
    val formDraft = remember { SnippetFormDraftHolder() }
    var additionalShortcutsInput by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf("") }

    // 수정 모드로 폼 열기 핸들러
    val openEditFormStage: (PromptSnippet) -> Unit = { snippet ->
        formDraft.shortcutInput = snippet.shortcut
        additionalShortcutsInput = snippet.additionalShortcuts
        formDraft.categoryInput = snippet.category
        formDraft.contentInput = snippet.content
        errorMessage = ""
        activeStage = PromptSnippetStage.Form(editingSnippet = snippet)
    }

    // 신규 등록 모드로 폼 열기 핸들러 (현재 필터가 특정 카테고리면 기본 선택값으로 채워줌)
    val openNewFormStage: () -> Unit = {
        formDraft.shortcutInput = ""
        additionalShortcutsInput = emptyList()
        formDraft.categoryInput = (selectedFilter as? SnippetCategoryFilter.Named)?.category.orEmpty()
        formDraft.contentInput = ""
        errorMessage = ""
        activeStage = PromptSnippetStage.Form(editingSnippet = null)
    }

    // 보관함 목록으로 복귀 핸들러
    val backToVault: () -> Unit = {
        formDraft.shortcutInput = ""
        additionalShortcutsInput = emptyList()
        formDraft.categoryInput = ""
        formDraft.contentInput = ""
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
            // SizeTransform(스프링 크기 애니메이션)을 비활성화(using(null))하여 키보드 개폐 및 입력 중 외곽 바운스를 차단한다.
            AnimatedContent(
                targetState = activeStage,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140)))
                        .using(null)
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
                        val otherShortcuts = remember(snippets, stage.editingSnippet) {
                            snippets
                                .filter { it.id != stage.editingSnippet?.id }
                                .flatMap { it.allShortcuts }
                                .map { it.lowercase() }
                        }
                        SnippetFormContent(
                            editingSnippet = stage.editingSnippet,
                            shortcutInput = formDraft.shortcutInput,
                            additionalShortcutsInput = additionalShortcutsInput,
                            categoryInput = formDraft.categoryInput,
                            contentInput = formDraft.contentInput,
                            availableCategories = effectiveCategories,
                            errorMessage = errorMessage,
                            currentPromptText = currentPromptText,
                            isSelectionPrompt = isSelectionPrompt,
                            existingShortcuts = otherShortcuts,
                            clipboardGateway = clipboardGateway,
                            onShortcutChange = {
                                formDraft.shortcutInput = it.filterNot { char -> char.isWhitespace() }
                                if (errorMessage.isNotEmpty()) {
                                    errorMessage = ""
                                }
                            },
                            onAdditionalShortcutsChange = {
                                additionalShortcutsInput = it
                                if (errorMessage.isNotEmpty()) {
                                    errorMessage = ""
                                }
                            },
                            onCategoryChange = {
                                formDraft.categoryInput = it.filterNot { char -> char.isWhitespace() }
                                if (errorMessage.isNotEmpty()) {
                                    errorMessage = ""
                                }
                            },
                            onContentChange = {
                                formDraft.contentInput = it
                                if (errorMessage.isNotEmpty()) {
                                    errorMessage = ""
                                }
                            },
                            onCancel = backToVault,
                            onSave = {
                                val shortcut = formDraft.shortcutInput.trim()
                                val cleanedAdditional = additionalShortcutsInput
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                val category = formDraft.categoryInput.trim()
                                val content = formDraft.contentInput.trim()
                                val otherShortcutsSet = otherShortcuts.toSet()

                                when {
                                    shortcut.isEmpty() -> errorMessage = "메인 단축어를 입력해 주세요."
                                    otherShortcutsSet.contains(shortcut.lowercase()) -> {
                                        errorMessage = "'$shortcut'은(는) 이미 사용 중인 단축어입니다."
                                    }
                                    cleanedAdditional.any { it.equals(shortcut, ignoreCase = true) } -> {
                                        errorMessage = "추가 단축어가 메인 단축어와 중복됩니다."
                                    }
                                    cleanedAdditional.groupBy { it.lowercase() }.any { it.value.size > 1 } -> {
                                        errorMessage = "추가 단축어 목록에 중복된 단어가 있습니다."
                                    }
                                    cleanedAdditional.any { otherShortcutsSet.contains(it.lowercase()) } -> {
                                        val dup = cleanedAdditional.first { otherShortcutsSet.contains(it.lowercase()) }
                                        errorMessage = "'$dup'은(는) 이미 다른 상용구에서 사용 중인 단축어입니다."
                                    }
                                    content.isEmpty() -> errorMessage = "프롬프트 문구를 입력해 주세요."
                                    else -> {
                                        if (stage.editingSnippet != null) {
                                            onUpdateSnippet(stage.editingSnippet.id, shortcut, cleanedAdditional, content, category)
                                        } else {
                                            onAddSnippet(shortcut, cleanedAdditional, content, category)
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
@OptIn(ExperimentalLayoutApi::class)
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
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

                        snippet.additionalShortcuts.forEach { alias ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppTheme.colors.insetBed
                            ) {
                                Text(
                                    text = "+ $alias",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
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
                    NeuPillButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "취소",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    NeuPillButton(
                        onClick = onConfirmDelete,
                        contentColor = MaterialTheme.colorScheme.error,
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
