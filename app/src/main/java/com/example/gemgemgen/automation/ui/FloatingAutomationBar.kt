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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.automation.domain.isTerminal
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun FloatingAutomationBarOverlay(
    uiStateFlow: StateFlow<AutomationBarUiState>,
    onCancelAutomation: () -> Unit,
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
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )
    }
}

@Composable
private fun FloatingAutomationBar(
    uiState: AutomationBarUiState,
    onCancelAutomation: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val width = with(LocalDensity.current) {
        min(
            420.dp.toPx(),
            max(280.dp.toPx(), configuration.screenWidthDp.dp.toPx() - 32.dp.toPx())
        ).toDp()
    }

    GemgemgenTheme {
        Box(
            modifier = Modifier
                .width(width)
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
                onRepeatCountChange = {},
                onRunMvp = {},
                onCancelAutomation = onCancelAutomation,
                canRun = false,
                isRunning = uiState.isRunning,
                automationState = uiState.automationState
            )
        }
    }
}
