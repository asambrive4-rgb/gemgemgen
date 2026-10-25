// 역할: GPU 레이어 캐싱과 스크롤, 자리표시자, 문단 선택, 문구 검색 하이라이트 및 외부 문자열 상태와 TextFieldState 간 양방향 동기화를 지원하는 공용 텍스트 입력창 UI를 제공합니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.appTextFieldColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.abs

data class TextHighlightRange(
    val start: Int,
    val endExclusive: Int
)

/**
 * 외부 문자열 상태(`value`)와 최신 `TextFieldState`를 양방향으로 안전하게 동기화하는 헬퍼.
 *
 * - 사용자가 타이핑하는 동안에는 `TextFieldState`가 내부 버퍼와 한글 조합(IME Composition) 및 커서 위치를 직접 보존합니다.
 * - 클립보드 붙여넣기, 실행 취소, 파일 전환, 초기화처럼 외부에서 `value`가 바뀐 경우에만 `TextFieldState`를 갱신하여
 *   빠른 타이핑 중 이전 프레임 에코로 인해 글자가 씹히거나 커서가 튀는 현상을 방지합니다.
 */
@Composable
fun rememberSyncedTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
    resetKey: Any? = Unit
): TextFieldState {
    val state = remember(resetKey) {
        TextFieldState(
            initialText = value,
            initialSelection = TextRange(value.length)
        )
    }
    val onValueChangeLatest by rememberUpdatedState(onValueChange)
    val lastEmittedText = remember(resetKey) { mutableStateOf(value) }

    LaunchedEffect(value, state) {
        if (value != lastEmittedText.value && !state.text.contentEquals(value)) {
            lastEmittedText.value = value
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { currentText ->
                if (currentText != lastEmittedText.value) {
                    lastEmittedText.value = currentText
                    onValueChangeLatest(currentText)
                }
            }
    }

    return state
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    FlowPreview::class
)
@Composable
fun AppMultilineTextField(
    state: TextFieldState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minLines: Int = 18,
    maxLines: Int = 18,
    paragraphSelectionEnabled: Boolean = false,
    highlightRange: TextHighlightRange? = null,
    selectedParagraphColor: Color = Color.Transparent,
    searchHighlightRanges: List<TextHighlightRange> = emptyList(),
    activeSearchMatchIndex: Int = -1,
    supportingText: String = "",
    onParagraphOffsetSelected: (Int) -> Unit = {},
    onDeleteSelectedParagraph: () -> Unit = {},
    onReplaceSelectedParagraph: (String) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Host가 매 리컴포즈마다 새 람다를 넘기더라도 debounce 구독이 재시작되지 않게 한다.
    val onValueChangeLatest by rememberUpdatedState(onValueChange)
    val onDeleteSelectedParagraphLatest by rememberUpdatedState(onDeleteSelectedParagraph)
    val onReplaceSelectedParagraphLatest by rememberUpdatedState(onReplaceSelectedParagraph)
    val onParagraphOffsetSelectedLatest by rememberUpdatedState(onParagraphOffsetSelected)

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .debounce(TEXT_CHANGE_DEBOUNCE_MILLIS)
            .collect { onValueChangeLatest(it) }
    }

    val deleteOnlyTransformation = remember(
        paragraphSelectionEnabled,
        highlightRange,
        coroutineScope
    ) {
        if (!paragraphSelectionEnabled) {
            null
        } else {
            InputTransformation {
                if (changes.changeCount == 0) {
                    return@InputTransformation
                }
                val isPureDeletion = changes.changeCount > 0 &&
                    (0 until changes.changeCount).all { index ->
                        changes.getRange(index).collapsed &&
                            !changes.getOriginalRange(index).collapsed
                    }
                val insertedText = if (isPureDeletion) {
                    ""
                } else {
                    insertedTextFromChanges(toString(), changes)
                }
                revertAllChanges()
                if (isPureDeletion && highlightRange != null) {
                    coroutineScope.launch {
                        onDeleteSelectedParagraphLatest()
                    }
                } else if (highlightRange != null && insertedText.isNotEmpty()) {
                    coroutineScope.launch {
                        onReplaceSelectedParagraphLatest(insertedText)
                    }
                }
            }
        }
    }
    LaunchedEffect(activeSearchMatchIndex, searchHighlightRanges, textLayoutResult) {
        if (activeSearchMatchIndex in searchHighlightRanges.indices) {
            val match = searchHighlightRanges[activeSearchMatchIndex]
            val layout = textLayoutResult
            if (layout != null && match.start <= layout.layoutInput.text.length) {
                val lineIndex = layout.getLineForOffset(match.start.coerceIn(0, layout.layoutInput.text.length))
                val lineTop = layout.getLineTop(lineIndex)
                val lineHeight = layout.getLineBottom(lineIndex) - lineTop
                // 사용자가 요청한 '상단 1줄 여백': lineTop에서 1줄 높이만큼 뺀 위치로 스크롤하여
                // 주황색 글자가 있는 줄이 상단에서 딱 1줄 여유를 두고 바로 아래에 위치하도록 함
                val targetScroll = (lineTop - lineHeight).toInt().coerceAtLeast(0)
                scrollState.animateScrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
            } else {
                val text = state.text.toString()
                if (text.isNotEmpty() && scrollState.maxValue > 0) {
                    val totalLines = text.count { it == '\n' } + 1
                    val matchLine = text.substring(0, match.start.coerceIn(0, text.length)).count { it == '\n' }
                    val targetScroll = (((matchLine - 1).coerceAtLeast(0).toFloat() / totalLines.coerceAtLeast(1)) * scrollState.maxValue).toInt()
                    scrollState.animateScrollTo(targetScroll.coerceIn(0, scrollState.maxValue))
                }
            }
        }
    }

    val textHighlightTransformation = remember(
        highlightRange,
        selectedParagraphColor,
        searchHighlightRanges,
        activeSearchMatchIndex
    ) {
        if (highlightRange == null && searchHighlightRanges.isEmpty()) {
            null
        } else {
            val paragraphSpanStyle = highlightRange?.let { SpanStyle(background = selectedParagraphColor) }
            val activeSearchSpanStyle = SpanStyle(background = ACTIVE_SEARCH_HIGHLIGHT_COLOR)
            val normalSearchSpanStyle = SpanStyle(background = SEARCH_HIGHLIGHT_COLOR)
            OutputTransformation {
                val pSpan = paragraphSpanStyle
                if (highlightRange != null && pSpan != null && highlightRange.endExclusive <= length) {
                    addStyle(
                        spanStyle = pSpan,
                        start = highlightRange.start,
                        end = highlightRange.endExclusive
                    )
                }
                searchHighlightRanges.forEachIndexed { index, range ->
                    if (range.endExclusive <= length) {
                        val span = if (index == activeSearchMatchIndex) {
                            activeSearchSpanStyle
                        } else {
                            normalSearchSpanStyle
                        }
                        addStyle(
                            spanStyle = span,
                            start = range.start,
                            end = range.endExclusive
                        )
                    }
                }
            }
        }
    }
    val paragraphTapModifier = if (paragraphSelectionEnabled) {
        Modifier.observeSimpleTap {
            coroutineScope.launch {
                // The text field updates its cursor from the same tap. Read it after that update settles.
                yield()
                onParagraphOffsetSelectedLatest(state.selection.end)
            }
        }
    } else {
        Modifier
    }

    val fieldShape = RoundedCornerShape(16.dp)

    OutlinedTextField(
        state = state,
        // 메인 탭 좌우 스와이프와 분리 — 입력칸 위 제스처는 탭 전환에 쓰지 않는다.
        modifier = modifier
            .graphicsLayer()
            .then(paragraphTapModifier),
        enabled = enabled,
        shape = fieldShape,
        colors = appTextFieldColors(),
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder)
            }
        },
        supportingText = if (supportingText.isNotBlank()) {
            { Text(supportingText) }
        } else {
            null
        },
        inputTransformation = deleteOnlyTransformation,
        outputTransformation = textHighlightTransformation,
        onTextLayout = { getResult -> textLayoutResult = getResult() },
        lineLimits = TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines,
            maxHeightInLines = maxLines
        ),
        scrollState = scrollState,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            color = AppTheme.colors.textPrimary
        )
    )
}

private const val TEXT_CHANGE_DEBOUNCE_MILLIS = 75L
private val SEARCH_HIGHLIGHT_COLOR = Color(0x66FFEB3B)
private val ACTIVE_SEARCH_HIGHLIGHT_COLOR = Color(0xFFFF9800)

@OptIn(ExperimentalFoundationApi::class)
private fun insertedTextFromChanges(
    text: String,
    changes: TextFieldBuffer.ChangeList
): String {
    return (0 until changes.changeCount)
        .map { changes.getRange(it) }
        .filter { !it.collapsed }
        .sortedBy { minOf(it.start, it.end) }
        .joinToString(separator = "") { range ->
            val start = minOf(range.start, range.end).coerceIn(0, text.length)
            val end = maxOf(range.start, range.end).coerceIn(start, text.length)
            text.substring(start, end)
        }
}

private fun Modifier.observeSimpleTap(onTap: () -> Unit): Modifier {
    return pointerInput(onTap) {
        while (true) {
            val isSimpleTap = awaitPointerEventScope {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial
                )
                val startPosition = down.position
                val startTime = down.uptimeMillis
                var movedTooFar = false
                var upTime: Long? = null

                while (upTime == null) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.first()
                    if (abs(change.position.x - startPosition.x) > viewConfiguration.touchSlop ||
                        abs(change.position.y - startPosition.y) > viewConfiguration.touchSlop
                    ) {
                        movedTooFar = true
                    }
                    if (!change.pressed) {
                        upTime = change.uptimeMillis
                    }
                }

                val wasLongPress =
                    upTime!! - startTime >= viewConfiguration.longPressTimeoutMillis
                !movedTooFar && !wasLongPress
            }

            if (isSimpleTap) {
                // Let the text field finish moving its cursor before reading state.selection.
                yield()
                onTap()
            }
        }
    }
}
