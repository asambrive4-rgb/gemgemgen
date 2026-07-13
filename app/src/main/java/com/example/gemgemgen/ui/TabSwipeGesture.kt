package com.example.gemgemgen.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * 메인 탭 HorizontalPager 스와이프를 텍스트 입력 등에서 막기 위한 카운터.
 * [isBlocked]는 Compose 상태에 연결되어 Pager의 userScrollEnabled에 반영된다.
 */
internal class TabSwipeBlocker {
    private var depth: Int = 0

    var isBlocked by mutableStateOf(false)
        private set

    fun acquire() {
        depth += 1
        isBlocked = true
    }

    fun release() {
        depth = (depth - 1).coerceAtLeast(0)
        isBlocked = depth > 0
    }
}

private val FallbackTabSwipeBlocker = TabSwipeBlocker()

internal val LocalTabSwipeBlocker = staticCompositionLocalOf { FallbackTabSwipeBlocker }

@Composable
internal fun ProvideTabSwipeBlocker(content: @Composable () -> Unit) {
    val blocker = remember { TabSwipeBlocker() }
    CompositionLocalProvider(LocalTabSwipeBlocker provides blocker, content = content)
}

/**
 * 이 Modifier가 붙은 영역에서는 메인 탭 Pager 가로 스와이프를 하지 않는다.
 * (텍스트 입력칸·가로 슬라이더 등)
 *
 * - nestedScroll로 가로 델타를 소비해 Pager로 전달되지 않게 하고
 * - 포인터 다운 동안 blocker를 잡아 userScrollEnabled도 끈다.
 */
internal fun Modifier.blockMainTabSwipe(): Modifier = composed {
    val blocker = LocalTabSwipeBlocker.current
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (abs(available.x) > abs(available.y) && available.x != 0f) {
                    Offset(x = available.x, y = 0f)
                } else {
                    Offset.Zero
                }
            }
        }
    }
    this
        .nestedScroll(nestedScrollConnection)
        .pointerInput(blocker) {
            awaitEachGesture {
                // Initial: Pager보다 먼저 블록 표시
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                blocker.acquire()
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                } finally {
                    blocker.release()
                }
            }
        }
}
