// 역할: 상용구 본문에서 특정 텍스트를 대소문자 구분 없이 검색하고 일치 결과를 확인한 뒤 안전하게 일괄 치환하는 3D 뉴모피즘 UI 화면을 제공합니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.automation.domain.PromptSnippetMatch
import com.example.gemgemgen.automation.domain.PromptSnippetTextUseCase
import com.example.gemgemgen.ui.rememberSyncedTextFieldState
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuCard
import com.example.gemgemgen.ui.theme.appTextFieldColors

@Composable
fun PromptSnippetFindReplaceView(
    snippets: List<PromptSnippet>,
    onBatchReplace: (searchText: String, replacementText: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val useCase = remember { PromptSnippetTextUseCase() }

    var searchInput by remember { mutableStateOf("") }
    var replacementInput by remember { mutableStateOf("") }
    var searchedQuery by remember { mutableStateOf<String?>(null) }
    var matchedResults by remember { mutableStateOf<List<PromptSnippetMatch>>(emptyList()) }
    var isConfirmingReplace by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val searchState = rememberSyncedTextFieldState(
        value = searchInput,
        onValueChange = {
            searchInput = it
            searchedQuery = null
            matchedResults = emptyList()
            isConfirmingReplace = false
            successMessage = null
        }
    )

    val replacementState = rememberSyncedTextFieldState(
        value = replacementInput,
        onValueChange = {
            replacementInput = it
            isConfirmingReplace = false
            successMessage = null
        }
    )

    // 상용구 목록 변경 시(치환 완료 후) 일치 결과 갱신
    LaunchedEffect(snippets) {
        val query = searchedQuery
        if (!query.isNullOrEmpty()) {
            matchedResults = useCase.findMatches(snippets, query)
        }
    }

    val hasMatches = searchedQuery != null && matchedResults.isNotEmpty()
    val isSameQueryAndReplacement = searchInput.isNotEmpty() && searchInput == replacementInput
    val canReplace = hasMatches && !isSameQueryAndReplacement

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 상단 안내 배너
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AppTheme.colors.primary.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FindReplace,
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "상용구 본문에서 특정 문구를 대소문자 무시로 찾아 한 번에 바꿉니다.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textPrimary
                )
            }
        }

        // 2. 찾을 내용 입력 & 찾기 버튼
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "찾을 문구",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    state = searchState,
                    label = { Text("찾을 단어 또는 문구") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = appTextFieldColors()
                )

                NeuButton(
                    onClick = {
                        focusManager.clearFocus()
                        if (searchInput.isNotEmpty()) {
                            searchedQuery = searchInput
                            matchedResults = useCase.findMatches(snippets, searchInput)
                            isConfirmingReplace = false
                            successMessage = null
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    isPrimary = true,
                    enabled = searchInput.isNotEmpty()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("찾기", fontSize = 13.sp)
                    }
                }
            }
        }

        // 3. 검색 결과 피드백 & 매칭 목록
        searchedQuery?.let { query ->
            if (matchedResults.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AppTheme.colors.insetBed.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "일치하는 상용구가 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                val totalOccurrences = matchedResults.sumOf { it.occurrenceCount }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "✓ 총 ${matchedResults.size}개 상용구에서 ${totalOccurrences}회 일치",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primary,
                        fontSize = 12.sp
                    )

                    // 매칭 카드 목록 (외부 스크롤러에서 자연스럽게 함께 스크롤)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchedResults.forEach { match ->
                            FindMatchItemCard(
                                match = match,
                                searchText = query
                            )
                        }
                    }
                }
            }
        }

        // 4. 바꿀 문구 입력
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "바꿀 문구",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasMatches) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary
            )
            OutlinedTextField(
                state = replacementState,
                enabled = hasMatches,
                placeholder = {
                    Text(
                        text = "비워두면 검색된 단어가 본문에서 완전히 삭제됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = appTextFieldColors()
            )
            if (isSameQueryAndReplacement) {
                Text(
                    text = "찾을 문구와 바꿀 문구가 같습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp
                )
            }
        }

        // 5. 성공 메시지 배너
        successMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppTheme.colors.primary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // 6. 2단계 인라인 치환 확인 바 (실수 방지 & Flicker-Free)
        AnimatedVisibility(
            visible = isConfirmingReplace,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val isDeleteMode = replacementInput.isEmpty()
            val promptCount = matchedResults.size
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDeleteMode) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else AppTheme.colors.primary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDeleteMode) "${promptCount}개 상용구에서 삭제할까요?" else "${promptCount}개 상용구를 변경할까요?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDeleteMode) MaterialTheme.colorScheme.error else AppTheme.colors.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { isConfirmingReplace = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("취소", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        }

                        TextButton(
                            onClick = {
                                val query = searchInput
                                val repl = replacementInput
                                onBatchReplace(query, repl)
                                isConfirmingReplace = false
                                successMessage = if (repl.isEmpty()) {
                                    "${promptCount}개 상용구에서 문구를 삭제했습니다."
                                } else {
                                    "${promptCount}개 상용구를 성공적으로 변경했습니다."
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = if (isDeleteMode) {
                                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.primary)
                            },
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (isDeleteMode) "삭제 실행" else "변경 실행",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 7. 하단 조작 버튼
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
                Text("목록으로")
            }

            NeuButton(
                onClick = {
                    focusManager.clearFocus()
                    isConfirmingReplace = true
                },
                modifier = Modifier.weight(1.3f),
                isPrimary = true,
                enabled = canReplace && !isConfirmingReplace
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (replacementInput.isEmpty()) "일괄 삭제" else "일괄 바꾸기"
                    )
                }
            }
        }
    }
}

@Composable
private fun FindMatchItemCard(
    match: PromptSnippetMatch,
    searchText: String,
    modifier: Modifier = Modifier
) {
    val highlightedSnippet = rememberHighlightedText(
        text = match.preview,
        searchText = searchText
    )

    NeuCard(
        shape = RoundedCornerShape(10.dp),
        elevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppTheme.colors.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "📋 ${match.snippet.shortcut}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "${match.occurrenceCount}회 일치",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = AppTheme.colors.textSecondary
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppTheme.colors.insetBed.copy(alpha = 0.5f))
                    .padding(6.dp)
            ) {
                Text(
                    text = highlightedSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun rememberHighlightedText(
    text: String,
    searchText: String,
    highlightStyle: SpanStyle = SpanStyle(
        background = AppTheme.colors.primary.copy(alpha = 0.24f),
        color = AppTheme.colors.primary,
        fontWeight = FontWeight.ExtraBold
    )
): AnnotatedString {
    return remember(text, searchText, highlightStyle) {
        if (searchText.isEmpty() || !text.contains(searchText, ignoreCase = true)) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var currentIndex = 0
                while (currentIndex < text.length) {
                    val matchIndex = text.indexOf(searchText, currentIndex, ignoreCase = true)
                    if (matchIndex < 0) {
                        append(text.substring(currentIndex))
                        break
                    }
                    if (matchIndex > currentIndex) {
                        append(text.substring(currentIndex, matchIndex))
                    }
                    pushStyle(highlightStyle)
                    append(text.substring(matchIndex, matchIndex + searchText.length))
                    pop()
                    currentIndex = matchIndex + searchText.length
                }
            }
        }
    }
}
