// 역할: 가상 키보드(IME) 개폐 상태를 파악하며, 애니메이션 중 불필요한 전체 화면 리컴포지션을 방지하는 헬퍼 함수를 제공합니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * 가상 키보드(IME)의 노출 여부를 관찰하는 Composable 헬퍼 함수.
 *
 * [WindowInsets.ime.getBottom]의 픽셀값은 키보드가 오르내리는 애니메이션 동안
 * 매 프레임(초당 60~120회) 변경됩니다. 본 함수는 [derivedStateOf]를 통해
 * 불리언(열림/닫힘) 상태가 반전될 때만 리컴포지션을 발생시켜 전체 화면의 프레임 드랍을 방지합니다.
 */
@Composable
internal fun rememberIsImeVisible(): Boolean {
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    return remember(imeInsets, density) {
        derivedStateOf {
            imeInsets.getBottom(density) > 0
        }
    }.value
}
