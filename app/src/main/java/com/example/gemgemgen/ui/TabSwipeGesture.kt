package com.example.gemgemgen.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 메인 탭 Pager 제거 후 호환성을 위해 유지되는 no-op Modifier.
 * (더 이상 Pager 스와이프 제스처가 없으므로 터치 가로채기 오버헤드를 유발하지 않고 자기 자신을 반환합니다.)
 */
internal fun Modifier.blockMainTabSwipe(): Modifier = this

/**
 * Pager 제거 후 호환성을 위해 유지되는 더미 Provider.
 */
@Composable
internal fun ProvideTabSwipeBlocker(content: @Composable () -> Unit) {
    content()
}

