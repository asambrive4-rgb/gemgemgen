package com.example.gemgemgen

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.gemgemgen.ui.theme.GemgemgenTheme
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class FloatingAutomationBarController(
    private val activity: ComponentActivity
) {
    private val appContext = activity.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val positionStore = FloatingBarPositionStore(appContext)
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: FloatingBarLifecycleOwner? = null
    private var currentPosition = positionStore.load()

    fun showOrUpdate(
        uiStateFlow: StateFlow<MainUiState>,
        onCancelAutomation: () -> Unit,
        onAutomationFinished: () -> Unit
    ) {
        if (overlayView != null) return

        val params = createLayoutParams()
        val lifecycleOwner = FloatingBarLifecycleOwner().also { it.start() }
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
        }

        overlayView = view
        overlayLifecycleOwner = lifecycleOwner
        layoutParams = params
        windowManager.addView(view, params)

        view.setContent {
            FloatingAutomationBarOverlay(
                uiStateFlow = uiStateFlow,
                onCancelAutomation = onCancelAutomation,
                onAutomationFinished = onAutomationFinished,
                onDrag = ::moveBy,
                onDragEnd = ::savePosition
            )
        }
    }

    fun hide() {
        val view = overlayView ?: return
        windowManager.removeView(view)
        overlayView = null
        layoutParams = null
        overlayLifecycleOwner?.destroy()
        overlayLifecycleOwner = null
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val defaultPosition = positionStore.defaultPosition()
        val position = currentPosition ?: defaultPosition
        currentPosition = position

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
    }

    private fun moveBy(deltaX: Float, deltaY: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return

        params.x = clampX(params.x + deltaX.roundToInt())
        params.y = clampY(params.y + deltaY.roundToInt())
        currentPosition = FloatingBarPosition(params.x, params.y)
        windowManager.updateViewLayout(view, params)
    }

    private fun savePosition() {
        currentPosition?.let(positionStore::save)
    }

    private fun clampX(value: Int): Int {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val barWidth = positionStore.barWidthPx()
        return min(max(value, 0), max(screenWidth - barWidth, 0))
    }

    private fun clampY(value: Int): Int {
        val screenHeight = appContext.resources.displayMetrics.heightPixels
        val minVisibleHeight = positionStore.minVisibleHeightPx()
        return min(max(value, 0), max(screenHeight - minVisibleHeight, 0))
    }
}

@Composable
private fun FloatingAutomationBarOverlay(
    uiStateFlow: StateFlow<MainUiState>,
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
    uiState: MainUiState,
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

private class FloatingBarLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun start() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return

        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

internal data class FloatingBarPosition(
    val x: Int,
    val y: Int
)

private class FloatingBarPositionStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): FloatingBarPosition? {
        if (!preferences.contains(KEY_X) || !preferences.contains(KEY_Y)) {
            return null
        }
        return FloatingBarPosition(
            x = preferences.getInt(KEY_X, 0),
            y = preferences.getInt(KEY_Y, 0)
        )
    }

    fun save(position: FloatingBarPosition) {
        preferences.edit()
            .putInt(KEY_X, position.x)
            .putInt(KEY_Y, position.y)
            .apply()
    }

    fun defaultPosition(): FloatingBarPosition {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val screenHeight = appContext.resources.displayMetrics.heightPixels
        val x = max((screenWidth - barWidthPx()) / 2, 0)
        val y = max(screenHeight - minVisibleHeightPx() - edgeMarginPx(), 0)
        return FloatingBarPosition(x, y)
    }

    fun barWidthPx(): Int {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        return min(screenWidth - edgeMarginPx() * 2, dpToPx(MAX_BAR_WIDTH_DP))
    }

    fun minVisibleHeightPx(): Int {
        return dpToPx(MIN_VISIBLE_HEIGHT_DP)
    }

    private fun edgeMarginPx(): Int {
        return dpToPx(EDGE_MARGIN_DP)
    }

    private fun dpToPx(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).roundToInt()
    }

    private companion object {
        const val PREFERENCES_NAME = "floating_automation_bar"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val EDGE_MARGIN_DP = 16
        const val MAX_BAR_WIDTH_DP = 420
        const val MIN_VISIBLE_HEIGHT_DP = 96
    }
}
