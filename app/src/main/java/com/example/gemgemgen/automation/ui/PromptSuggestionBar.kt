// 역할: 상용구·연동 와일드카드 개별 항목·와일드카드 토큰 추천 칩을 3색 시각적 위계와 출처 카테고리 뱃지로 표시합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * 추천 칩 상태를 화면 루트와 격리하여 국소 관찰하는 컴포저블.
 * 커서 이동 시 화면 전체가 리컴포지션되는 것을 방지합니다.
 */
@Composable
internal fun IsolatedPromptSuggestionBar(
    activeSuggestionsFlow: StateFlow<List<WildcardTokenAutocomplete.Candidate>>,
    onSuggestionClick: (WildcardTokenAutocomplete.Candidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions by activeSuggestionsFlow.collectAsStateWithLifecycle()
    if (suggestions.isNotEmpty()) {
        PromptSuggestionBar(
            suggestions = suggestions,
            onSuggestionClick = onSuggestionClick,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PromptSuggestionBar(
    suggestions: List<WildcardTokenAutocomplete.Candidate>,
    onSuggestionClick: (WildcardTokenAutocomplete.Candidate) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 92.dp)
            .graphicsLayer()
            .verticalScroll(rememberScrollState())
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { candidate ->
                key("${candidate.type}:${candidate.name}:${candidate.token}") {
                    WildcardSuggestionChip(
                        candidate = candidate,
                        onClick = { onSuggestionClick(candidate) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun WildcardSuggestionChip(
    candidate: WildcardTokenAutocomplete.Candidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSnippet = candidate.type == WildcardTokenAutocomplete.Candidate.Type.SNIPPET
    val isWildcardItem = candidate.type == WildcardTokenAutocomplete.Candidate.Type.WILDCARD_ITEM
    val hasCategory = (isSnippet || isWildcardItem) && candidate.category.isNotBlank()
    val shape = RoundedCornerShape(10.dp)

    val (containerColor, textColor) = when (candidate.type) {
        WildcardTokenAutocomplete.Candidate.Type.SNIPPET -> {
            AppTheme.colors.snippetPrimary.copy(alpha = 0.14f) to AppTheme.colors.snippetPrimary
        }
        WildcardTokenAutocomplete.Candidate.Type.WILDCARD_ITEM -> {
            AppTheme.colors.wildcardItemPrimary.copy(alpha = 0.14f) to AppTheme.colors.wildcardItemPrimary
        }
        WildcardTokenAutocomplete.Candidate.Type.WILDCARD -> {
            AppTheme.colors.primary.copy(alpha = 0.14f) to AppTheme.colors.primary
        }
    }

    val description = when {
        isWildcardItem && hasCategory -> "와일드카드 [${candidate.category}] ${candidate.name} 삽입"
        isWildcardItem -> "와일드카드 ${candidate.name} 삽입"
        isSnippet && hasCategory -> "상용구 [${candidate.category}] ${candidate.name} 치환"
        isSnippet -> "상용구 ${candidate.name} 치환"
        else -> "와일드카드 토큰 ${candidate.displayText} 삽입"
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier = modifier.semantics {
            contentDescription = description
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(
                start = if (hasCategory) 6.dp else 9.dp,
                end = 9.dp,
                top = 5.dp,
                bottom = 5.dp
            )
        ) {
            if (hasCategory) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = textColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = candidate.category,
                        color = textColor.copy(alpha = 0.90f),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            } else if (isSnippet) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = candidate.displayText,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSnippet || isWildcardItem) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

