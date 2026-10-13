// 역할: 와일드카드 및 프롬프트 상용구 추천 칩을 가로 스크롤 목록으로 표시합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.ui.theme.AppTheme

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Composable
internal fun PromptSuggestionBar(
    suggestions: List<WildcardTokenAutocomplete.Candidate>,
    onSuggestionClick: (WildcardTokenAutocomplete.Candidate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        suggestions.forEach { candidate ->
            WildcardSuggestionChip(
                candidate = candidate,
                onClick = { onSuggestionClick(candidate) }
            )
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
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (isSnippet) {
        AppTheme.colors.snippetBorder
    } else {
        AppTheme.colors.primary.copy(alpha = 0.4f)
    }
    val textColor = if (isSnippet) {
        AppTheme.colors.snippetPrimary
    } else {
        AppTheme.colors.primary
    }
    val shadowColor = if (isSnippet) {
        AppTheme.colors.snippetShadow
    } else {
        AppTheme.colors.primary.copy(alpha = 0.2f)
    }
    val description = if (isSnippet) {
        "상용구 ${candidate.name} 치환"
    } else {
        "와일드카드 ${candidate.displayText} 삽입"
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = AppTheme.colors.card,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor.copy(alpha = 0.15f)
            )
            .semantics {
                contentDescription = description
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            if (isSnippet) {
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
