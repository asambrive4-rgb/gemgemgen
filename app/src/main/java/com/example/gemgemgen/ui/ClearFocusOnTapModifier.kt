package com.example.gemgemgen.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * 입력창이 아닌 영역을 짧게 탭하면 포커스를 해제한다.
 *
 * Initial 패스에서 처리해 스크롤 등이 이벤트를 소비해도 동작한다.
 * TextField를 직접 탭한 경우에는 같은 제스처로 TextField가 다시 포커스를 가져가므로
 * 입력은 그대로 가능하다.
 */
internal fun Modifier.clearFocusOnOutsideTap(onClearFocus: () -> Unit): Modifier {
    return pointerInput(onClearFocus) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
            val dx = abs(up.position.x - down.position.x)
            val dy = abs(up.position.y - down.position.y)
            val isSimpleTap = dx <= touchSlop && dy <= touchSlop
            if (isSimpleTap) {
                // TextField/버튼 위 탭이어도 먼저 clear.
                // 포커스 가능 자식이 같은 탭으로 다시 focus 를 가져간다.
                onClearFocus()
            }
        }
    }
}
