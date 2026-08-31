package com.example.gemgemgen.automation.android

import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.gemgemgen.automation.ui.AutomationBarUiState
import com.example.gemgemgen.automation.ui.FloatingAutomationBarOverlay
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
    private val choreographer = Choreographer.getInstance()
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: FloatingBarLifecycleOwner? = null
    private var currentPosition = positionStore.load()
    private var pendingDragX = 0f
    private var pendingDragY = 0f
    private var isDragFrameScheduled = false
    private val dragFrameCallback = Choreographer.FrameCallback {
        isDragFrameScheduled = false
        applyPendingDrag()
    }

    fun showOrUpdate(
        uiStateFlow: StateFlow<AutomationBarUiState>,
        onCancelAutomation: () -> Unit,
        onRepeatCountChange: (String) -> Unit,
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
                onRepeatCountChange = onRepeatCountChange,
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
        choreographer.removeFrameCallback(dragFrameCallback)
        isDragFrameScheduled = false
        pendingDragX = 0f
        pendingDragY = 0f
        overlayLifecycleOwner?.destroy()
        overlayLifecycleOwner = null
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val defaultPosition = positionStore.defaultPosition()
        val position = currentPosition ?: defaultPosition
        currentPosition = position

        return WindowManager.LayoutParams(
            positionStore.barWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position.x
            y = position.y
        }
    }

    private fun moveBy(deltaX: Float, deltaY: Float) {
        pendingDragX += deltaX
        pendingDragY += deltaY
        if (!isDragFrameScheduled) {
            isDragFrameScheduled = true
            choreographer.postFrameCallback(dragFrameCallback)
        }
    }

    private fun applyPendingDrag() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val deltaX = pendingDragX
        val deltaY = pendingDragY
        pendingDragX = 0f
        pendingDragY = 0f
        if (deltaX == 0f && deltaY == 0f) return

        params.x = clampX(params.x + deltaX.roundToInt())
        params.y = clampY(params.y + deltaY.roundToInt())
        currentPosition = FloatingBarPosition(params.x, params.y)
        windowManager.updateViewLayout(view, params)
    }

    private fun savePosition() {
        if (isDragFrameScheduled) {
            choreographer.removeFrameCallback(dragFrameCallback)
            isDragFrameScheduled = false
            applyPendingDrag()
        }
        currentPosition?.let(positionStore::save)
    }

    private fun clampX(value: Int): Int {
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val barWidth = positionStore.barWidthPx()
        val minX = min(0, screenWidth - barWidth)
        val maxX = max(0, screenWidth - barWidth)
        return min(max(value, minX), maxX)
    }

    private fun clampY(value: Int): Int {
        val screenHeight = appContext.resources.displayMetrics.heightPixels
        val minVisibleHeight = positionStore.minVisibleHeightPx()
        return min(max(value, 0), max(screenHeight - minVisibleHeight, 0))
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
