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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun FloatingAutomationBarOverlay(
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
        FloatingAutomationBar(
            uiState = uiState,
            onCancelAutomation = onCancelAutomation,
            onRepeatCountChange = onRepeatCountChange,
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )
    }
}

@Composable
private fun FloatingAutomationBar(
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
            AutomationActionBar(
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
