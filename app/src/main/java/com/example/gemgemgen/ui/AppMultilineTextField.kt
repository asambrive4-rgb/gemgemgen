package com.example.gemgemgen.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.example.gemgemgen.automation.domain.PromptParagraphRange
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.yield
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AppMultilineTextField(
    state: TextFieldState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minLines: Int,
    maxLines: Int = 18,
    paragraphSelectionEnabled: Boolean = false,
    selectedParagraphRange: PromptParagraphRange? = null,
    selectedParagraphColor: Color = Color.Transparent,
    supportingText: String = "",
    onParagraphOffsetSelected: (Int) -> Unit = {},
    onDeleteSelectedParagraph: () -> Unit = {},
    onReplaceSelectedParagraph: (String) -> Unit = {}
) {
    var deleteRequestId by remember { mutableIntStateOf(0) }
    var replaceRequestId by remember { mutableIntStateOf(0) }
    var replacementText by remember { mutableStateOf("") }
    var paragraphTapRequestId by remember { mutableIntStateOf(0) }

    LaunchedEffect(state, onValueChange) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { onValueChange(it) }
    }

    LaunchedEffect(deleteRequestId) {
        if (deleteRequestId > 0) {
            onDeleteSelectedParagraph()
        }
    }

    LaunchedEffect(replaceRequestId) {
        if (replaceRequestId > 0) {
            onReplaceSelectedParagraph(replacementText)
        }
    }

    LaunchedEffect(paragraphTapRequestId) {
        if (paragraphTapRequestId > 0) {
            // The text field updates its cursor from the same tap. Read it after that update settles.
            yield()
            onParagraphOffsetSelected(state.selection.end)
        }
    }

    val deleteOnlyTransformation = remember(
        paragraphSelectionEnabled,
        selectedParagraphRange
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
                if (isPureDeletion && selectedParagraphRange != null) {
                    deleteRequestId += 1
                } else if (selectedParagraphRange != null && insertedText.isNotEmpty()) {
                    replacementText = insertedText
                    replaceRequestId += 1
                }
            }
        }
    }
    val paragraphHighlight = remember(selectedParagraphRange, selectedParagraphColor) {
        selectedParagraphRange?.let { range ->
            OutputTransformation {
                if (range.endExclusive <= length) {
                    addStyle(
                        spanStyle = SpanStyle(background = selectedParagraphColor),
                        start = range.start,
                        end = range.endExclusive
                    )
                }
            }
        }
    }
    val paragraphTapModifier = if (paragraphSelectionEnabled) {
        Modifier.observeSimpleTap {
            paragraphTapRequestId += 1
        }
    } else {
        Modifier
    }

    OutlinedTextField(
        state = state,
        modifier = modifier.then(paragraphTapModifier),
        enabled = enabled,
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
        outputTransformation = paragraphHighlight,
        lineLimits = TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines,
            maxHeightInLines = maxLines
        ),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace)
    )
}

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

