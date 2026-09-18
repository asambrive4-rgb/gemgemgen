// 역할: 대상 앱 화면 위에 항상 표시되어 자동화 진행률과 중지 버튼을 제공하는 플로팅 오버레이 바 UI입니다.
package com.example.gemgemgen.automation.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun FloatingOverlayBar(
    uiStateFlow: StateFlow<AutomationBarUiState>,
    onCancelAutomation: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onAutomationFinished: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val uiState by uiStateFlow.collectAsState()

    LaunchedEffect(uiState.automationState) {
        if (uiState.automationState.isTerminal()) {
            onAutomationFinished()
        }
    }

    if (uiState.isRunning) {
        FloatingOverlayContent(
            uiState = uiState,
            onCancelAutomation = onCancelAutomation,
            onRepeatCountChange = onRepeatCountChange,
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )
    }
}

@Composable
private fun FloatingOverlayContent(
    uiState: AutomationBarUiState,
    onCancelAutomation: () -> Unit,
    onRepeatCountChange: (String) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    GemgemgenTheme {
        Box(
            modifier = Modifier
                .width(470.dp)
                .padding(8.dp)
                .graphicsLayer {
                    clip = true
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { _, dragAmount ->
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
        ) {
            AutomationBottomBar(
                repeatCountText = uiState.repeatCountText,
                onRepeatCountChange = onRepeatCountChange,
                onRunMvp = {},
                onCancelAutomation = onCancelAutomation,
                canRun = false,
                isRunning = uiState.isRunning,
                automationState = uiState.automationState
            )
        }
    }
}
