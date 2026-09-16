// 역할: 롱프레스 드래그 시 가장자리 자동 스크롤을 완벽 지원하는 네이티브 EditText 기반 다중 행 텍스트 입력창 UI를 제공합니다.
package com.example.gemgemgen.ui

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gemgemgen.ui.theme.AppTheme

/**
 * 롱프레스 드래그 시 상위 스크롤뷰가 이벤트를 뺏지 못하도록 인터셉트를 방지하고,
 * 텍스트 선택 변경 시점을 외부에 알릴 수 있는 커스텀 EditText.
 */
private class InternalNativeEditText(context: Context) : EditText(context) {
    var onSelectionChangedListener: ((start: Int, end: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 부모 스크롤뷰(Column.verticalScroll)가 롱프레스 드래그 제스처를 가로채지 못하도록 잠금
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Android 네이티브 EditText를 활용하여 장문 텍스트 선택 시
 * 롱프레스 드래그 가장자리 자동 스크롤(Edge Auto-Scroll)을 부드럽게 지원하는 에디터 컴포저블.
 */
@Composable
fun NativeMultilineTextField(
    state: TextFieldState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    minLines: Int = 6,
    maxLines: Int = 18,
    supportingText: String = ""
) {
    val density = LocalDensity.current
    val onValueChangeLatest by rememberUpdatedState(onValueChange)

    var isFocused by remember { mutableStateOf(false) }

    val textColor = AppTheme.colors.textPrimary
    val hintColor = AppTheme.colors.textSecondary
    val cardBgColor = AppTheme.colors.card
    val borderColor = if (isFocused) AppTheme.colors.primary else AppTheme.colors.cardBorder
    val borderWidth = if (isFocused) 1.5.dp else 1.dp
    val fieldShape = RoundedCornerShape(14.dp)

    // 외부 상태 갱신 중 발생하는 내부 텍스트 감지 이벤트 루프 차단용 플래그
    var isUpdatingFromState by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 2.dp,
                    shape = fieldShape,
                    ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.35f),
                    spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f)
                ),
            shape = fieldShape,
            color = cardBgColor,
            border = BorderStroke(borderWidth, borderColor)
        ) {
            AndroidView<InternalNativeEditText>(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    InternalNativeEditText(context).apply {
                        this.isEnabled = enabled
                        this.typeface = Typeface.MONOSPACE
                        this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        this.setTextColor(textColor.toArgb())
                        this.setHintTextColor(hintColor.toArgb())
                        this.hint = placeholder
                        this.background = null
                        this.gravity = Gravity.TOP or Gravity.START
                        this.isVerticalScrollBarEnabled = true
                        this.scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
                        this.movementMethod = ScrollingMovementMethod.getInstance()
                        this.setHorizontallyScrolling(false)
                        this.inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

                        this.minLines = minLines
                        this.maxLines = maxLines

                        val padH = with(density) { 14.dp.roundToPx() }
                        val padV = with(density) { 12.dp.roundToPx() }
                        this.setPadding(padH, padV, padH, padV)

                        val initialText = state.text.toString()
                        this.setText(initialText)
                        val initialSel = state.selection.end.coerceIn(0, initialText.length)
                        this.setSelection(initialSel)

                        this.setOnFocusChangeListener { _, hasFocus ->
                            isFocused = hasFocus
                        }

                        this.onSelectionChangedListener = { start, end ->
                            if (!isUpdatingFromState) {
                                val currentSel = state.selection
                                if (currentSel.start != start || currentSel.end != end) {
                                    state.edit {
                                        selection = TextRange(start, end)
                                    }
                                }
                            }
                        }

                        this.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) {
                                if (isUpdatingFromState) return
                                val newText = s?.toString().orEmpty()
                                if (!state.text.contentEquals(newText)) {
                                    state.edit {
                                        replace(0, length, newText)
                                        selection = TextRange(
                                            this@apply.selectionStart,
                                            this@apply.selectionEnd
                                        )
                                    }
                                    onValueChangeLatest(newText)
                                }
                            }
                        })
                    }
                },
                update = { editText ->
                    editText.isEnabled = enabled
                    editText.setTextColor(textColor.toArgb())
                    editText.setHintTextColor(hintColor.toArgb())
                    if (editText.hint != placeholder) {
                        editText.hint = placeholder
                    }

                    val currentComposeText = state.text.toString()
                    val currentEditText = editText.text?.toString().orEmpty()

                    if (currentComposeText != currentEditText) {
                        isUpdatingFromState = true
                        try {
                            val selStart = state.selection.start.coerceIn(0, currentComposeText.length)
                            val selEnd = state.selection.end.coerceIn(0, currentComposeText.length)
                            editText.setText(currentComposeText)
                            editText.setSelection(minOf(selStart, selEnd), maxOf(selStart, selEnd))
                        } finally {
                            isUpdatingFromState = false
                        }
                    } else {
                        // 텍스트는 같고 선택 영역만 달라진 경우 동기화
                        val composeSelStart = state.selection.start.coerceIn(0, currentEditText.length)
                        val composeSelEnd = state.selection.end.coerceIn(0, currentEditText.length)
                        if (editText.selectionStart != composeSelStart || editText.selectionEnd != composeSelEnd) {
                            editText.setSelection(minOf(composeSelStart, composeSelEnd), maxOf(composeSelStart, composeSelEnd))
                        }
                    }
                }
            )
        }

        if (supportingText.isNotBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp)
            )
        }
    }
}
