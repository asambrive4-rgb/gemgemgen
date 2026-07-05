package com.example.gemgemgen.automation.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class GeminiAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var closeGeminiCompletion: ((CloseGeminiAppResult) -> Unit)? = null
    private val geminiAutomation by lazy {
        GeminiPromptAutomation(
            handler = handler,
            rootProvider = { rootInActiveWindow }
        )
    }
    private val chatGptAutomation by lazy {
        ChatGptPromptAutomation(
            handler = handler,
            rootProvider = { rootInActiveWindow }
        )
    }

    override fun onServiceConnected() {
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        finishCloseGemini(CloseGeminiAppResult.Failure("접근성 서비스가 중단되었습니다."))
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        if (activeService == this) {
            activeService = null
        }
        finishCloseGemini(CloseGeminiAppResult.Failure("접근성 서비스가 종료되었습니다."))
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    internal fun gatewayFor(targetApp: AutomationTargetApp): PromptAutomationGateway {
        return when (targetApp) {
            AutomationTargetApp.GEMINI -> geminiAutomation
            AutomationTargetApp.CHATGPT -> chatGptAutomation
        }
    }

    internal suspend fun closeGeminiFromRecents(): CloseGeminiAppResult {
        return suspendCancellableCoroutine { continuation ->
            closeGeminiFromRecents { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                closeGeminiCompletion = null
            }
        }
    }

    private fun closeGeminiFromRecents(onFinished: (CloseGeminiAppResult) -> Unit) {
        if (closeGeminiCompletion != null) {
            onFinished(CloseGeminiAppResult.Failure("Gemini 재시작이 이미 진행 중입니다."))
            return
        }

        closeGeminiCompletion = onFinished
        handler.post {
            val opened = tapDexRecentsButton {
                handler.postDelayed(
                    { closeNextGeminiCard(closedCount = 0, clickCount = 0) },
                    RECENTS_OPEN_WAIT_MS
                )
            }
            if (!opened) {
                finishCloseGemini(CloseGeminiAppResult.RecentsUnavailable)
            }
        }
    }

    private fun tapDexRecentsButton(onCompleted: () -> Unit): Boolean {
        val windowMetrics = getSystemService(WindowManager::class.java).currentWindowMetrics
        val displayBounds = windowMetrics.bounds
        val navigationInsets = windowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
        val navigationBarHeight = navigationInsets.bottom.takeIf { it > 0 }
            ?: (displayBounds.height() * NAVIGATION_BAR_HEIGHT_RATIO).toInt()
        val density = resources.displayMetrics.density
        val tapX = RECENTS_BUTTON_X_DP * density
        val tapY = displayBounds.bottom - (navigationBarHeight / 2f)
        val tapPath = Path().apply {
            moveTo(tapX, tapY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0L, RECENTS_BUTTON_TAP_MS))
            .build()

        return dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleted()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    finishCloseGemini(CloseGeminiAppResult.RecentsUnavailable)
                }
            },
            handler
        )
    }

    private fun closeNextGeminiCard(closedCount: Int, clickCount: Int) {
        if (clickCount >= MAX_GEMINI_CLOSE_CLICKS) {
            val result = if (closedCount > 0) {
                CloseGeminiAppResult.Success(closedCount)
            } else {
                CloseGeminiAppResult.Failure("Gemini 닫기 버튼을 누르지 못했습니다.")
            }
            finishCloseGeminiAfterDismissingRecents(result)
            return
        }

        val closeNode = findGeminiCloseNode()
        if (closeNode == null) {
            finishCloseGeminiAfterDismissingRecents(
                if (closedCount > 0) {
                    CloseGeminiAppResult.Success(closedCount)
                } else {
                    CloseGeminiAppResult.NotFound
                }
            )
            return
        }

        if (!clickNodeOrParent(closeNode)) {
            finishCloseGeminiAfterDismissingRecents(
                CloseGeminiAppResult.Failure("Gemini 닫기 버튼을 누르지 못했습니다.")
            )
            return
        }

        handler.postDelayed(
            {
                closeNextGeminiCard(
                    closedCount = closedCount + 1,
                    clickCount = clickCount + 1
                )
            },
            CARD_CLOSE_WAIT_MS
        )
    }

    private fun finishCloseGeminiAfterDismissingRecents(result: CloseGeminiAppResult) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed(
            { finishCloseGemini(result) },
            RECENTS_DISMISS_WAIT_MS
        )
    }

    private fun findGeminiCloseNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = flattenNodes(root)
        nodes.firstOrNull { node ->
            node.contentDescription?.toString() == GEMINI_CLOSE_DESCRIPTION && node.isClickable
        }?.let { return it }

        val titleNodes = nodes.filter { node ->
            node.text?.toString() == GEMINI_TASK_TITLE
        }

        for (titleNode in titleNodes) {
            var current = titleNode.parent
            repeat(TITLE_ANCESTOR_SEARCH_DEPTH) {
                val closeNode = current?.let(::findCloseNodeInSubtree)
                if (closeNode != null) return closeNode
                current = current?.parent
            }
        }

        return null
    }

    private fun findCloseNodeInSubtree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && (
                node.viewIdResourceName?.endsWith(":id/task_close") == true ||
                    node.contentDescription?.toString() == GEMINI_CLOSE_DESCRIPTION
            )
        ) {
            return node
        }

        for (index in 0 until node.childCount) {
            val closeNode = node.getChild(index)?.let(::findCloseNodeInSubtree)
            if (closeNode != null) {
                return closeNode
            }
        }

        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }

        return false
    }

    private fun flattenNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun visit(node: AccessibilityNodeInfo) {
            nodes += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::visit)
            }
        }

        visit(root)
        return nodes
    }

    private fun finishCloseGemini(result: CloseGeminiAppResult) {
        val completion = closeGeminiCompletion ?: return
        closeGeminiCompletion = null
        completion(result)
    }

    companion object {
        var activeService: GeminiAccessibilityService? = null
            private set

        private const val GEMINI_TASK_TITLE = "Gemini"
        private const val GEMINI_CLOSE_DESCRIPTION = "Gemini 앱 종료"
        private const val RECENTS_OPEN_WAIT_MS = 700L
        private const val RECENTS_DISMISS_WAIT_MS = 250L
        private const val RECENTS_BUTTON_X_DP = 132f
        private const val RECENTS_BUTTON_TAP_MS = 80L
        private const val NAVIGATION_BAR_HEIGHT_RATIO = 0.06125f
        private const val CARD_CLOSE_WAIT_MS = 450L
        private const val MAX_GEMINI_CLOSE_CLICKS = 10
        private const val TITLE_ANCESTOR_SEARCH_DEPTH = 4
    }
}
