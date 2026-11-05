// 역할: 리스트 항목의 롱프레스 수직 드래그 순서 변경(Reordering) 오프셋 누적과 인덱스 스왑 상태를 관리하는 공통 UI 상태 홀더입니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.example.gemgemgen.automation.domain.PromptExperimentPolicy

/**
 * 롱프레스 수직 드래그를 통한 리스트 아이템 순서 재배치(Reordering) 상태 홀더.
 */
internal class ReorderableListDragState<T>(
    initialItems: List<T>,
    var rowHeightPx: Float,
    private val keyMatches: (T, String) -> Boolean,
    private val onReorderCommitted: (fromIndex: Int, toIndex: Int) -> Unit
) {
    var items: List<T> by mutableStateOf(initialItems)
        internal set

    var draggingKey: String? by mutableStateOf(null)
        private set

    var dragAccumulatedY: Float by mutableFloatStateOf(0f)
        private set

    private var initialDragIndex: Int? = null
    private var sourceItems: List<T> = initialItems

    val isDragging: Boolean
        get() = draggingKey != null

    fun isDraggingItem(key: String): Boolean {
        val current = draggingKey ?: return false
        return current.equals(key, ignoreCase = true)
    }

    fun syncItems(newItems: List<T>) {
        sourceItems = newItems
        if (draggingKey == null) {
            items = newItems
        }
    }

    fun startDrag(key: String) {
        val index = items.indexOfFirst { keyMatches(it, key) }
        if (index == -1) return
        draggingKey = key
        initialDragIndex = index
        dragAccumulatedY = 0f
    }

    fun onDrag(deltaY: Float) {
        val currentKey = draggingKey ?: return
        var curIndex = items.indexOfFirst { keyMatches(it, currentKey) }
        if (curIndex == -1) return
        dragAccumulatedY += deltaY

        val threshold = rowHeightPx * 0.5f
        while (dragAccumulatedY > threshold && curIndex < items.lastIndex) {
            val target = curIndex + 1
            items = PromptExperimentPolicy.moveElementTo(items, curIndex, target)
            curIndex = target
            dragAccumulatedY -= rowHeightPx
        }
        while (dragAccumulatedY < -threshold && curIndex > 0) {
            val target = curIndex - 1
            items = PromptExperimentPolicy.moveElementTo(items, curIndex, target)
            curIndex = target
            dragAccumulatedY += rowHeightPx
        }
    }

    fun endDrag() {
        val currentKey = draggingKey
        val from = initialDragIndex
        val to = if (currentKey != null) {
            items.indexOfFirst { keyMatches(it, currentKey) }
        } else {
            -1
        }
        if (from != null && to != -1 && from != to) {
            onReorderCommitted(from, to)
        }
        draggingKey = null
        initialDragIndex = null
        dragAccumulatedY = 0f
    }

    fun cancelDrag(fallbackItems: List<T>? = null) {
        items = fallbackItems ?: sourceItems
        draggingKey = null
        initialDragIndex = null
        dragAccumulatedY = 0f
    }
}

/**
 * 롱프레스 수직 드래그 순서 변경 상태 홀더를 생성하고 기억합니다.
 */
@Composable
internal fun <T> rememberReorderableListDragState(
    items: List<T>,
    rowHeightPx: Float,
    keyMatches: (T, String) -> Boolean,
    onReorderCommitted: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderableListDragState<T> {
    val currentKeyMatches by rememberUpdatedState(keyMatches)
    val currentOnReorderCommitted by rememberUpdatedState(onReorderCommitted)

    val state = remember {
        ReorderableListDragState(
            initialItems = items,
            rowHeightPx = rowHeightPx,
            keyMatches = { item, key -> currentKeyMatches(item, key) },
            onReorderCommitted = { from, to -> currentOnReorderCommitted(from, to) }
        )
    }

    state.rowHeightPx = rowHeightPx

    LaunchedEffect(items) {
        state.syncItems(items)
    }

    return state
}
