// 역할: 최근 앱 제어, 제스처 주입, 공통 노드 클릭 위임 및 앱별 자동화 게이트웨이를 제공하는 접근성 인프라 서비스
package com.example.gemgemgen.automation.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGateway
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class GeminiAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var closeAppCompletion: ((CloseGeminiAppResult) -> Unit)? = null
    private var memoryCleanupToken: Any? = null
    private var memoryCleanupCompletion: ((MemoryCleanupResult) -> Unit)? = null
    private var memoryCleanupAutomation: GoogleAppForceStopAutomation? = null
    private var closeTaskTitle: String = GEMINI_TASK_TITLE
    private var closeTaskDescription: String = GEMINI_CLOSE_DESCRIPTION
    private val geminiAutomation by lazy {
        GeminiPromptAutomation(
            coroutineScope = serviceScope,
            rootProvider = { rootInActiveWindow },
            copyToClipboard = ::copyTextToClipboard
        )
    }
    private val chatGptAutomation by lazy {
        ChatGptPromptAutomation(
            coroutineScope = serviceScope,
            rootProvider = { rootInActiveWindow },
            copyToClipboard = ::copyTextToClipboard
        )
    }
    private val flowAutomation by lazy {
        FlowPromptAutomation(
            coroutineScope = serviceScope,
            rootProvider = { rootInActiveWindow },
            tapAtCoordinates = { x, y, onCompleted ->
                tapCoordinates(x, y, onCompleted)
            }
        )
    }

    override fun onServiceConnected() {
        activeService = this
        serviceInfo = serviceInfo?.apply {
            eventTypes = 0
            notificationTimeout = 0L
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        finishMemoryCleanup(MemoryCleanupResult.Failure("접근성 서비스가 중단되었습니다."))
        finishCloseApp(CloseGeminiAppResult.Failure("접근성 서비스가 중단되었습니다."))
        ProcessAutomationHolder.onAccessibilityLost()
        serviceScope.coroutineContext.cancelChildren()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        finishMemoryCleanup(MemoryCleanupResult.Failure("접근성 서비스가 종료되었습니다."))
        if (activeService == this) {
            activeService = null
        }
        finishCloseApp(CloseGeminiAppResult.Failure("접근성 서비스가 종료되었습니다."))
        ProcessAutomationHolder.onAccessibilityLost()
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    internal fun gatewayFor(targetApp: AutomationTargetApp): PromptAutomationGateway {
        return when (targetApp) {
            AutomationTargetApp.GEMINI -> geminiAutomation
            AutomationTargetApp.CHATGPT -> chatGptAutomation
            AutomationTargetApp.FLOW -> flowAutomation
        }
    }

    internal fun variationGateway(): VariationPromptAutomationGateway {
        return geminiAutomation
    }

    internal suspend fun closeGeminiFromRecents(): CloseGeminiAppResult {
        return closeAppFromRecents(
            taskTitle = GEMINI_TASK_TITLE,
            closeDescription = GEMINI_CLOSE_DESCRIPTION
        )
    }

    internal suspend fun cleanDeviceMemory(
        launchDashboard: () -> Boolean
    ): MemoryCleanupResult {
        if (
            memoryCleanupToken != null ||
            closeAppCompletion != null ||
            ProcessAutomationHolder.current()?.runState?.value is AutomationRunState.Running
        ) {
            return MemoryCleanupResult.InProgress
        }

        return suspendCancellableCoroutine { continuation ->
            val token = Any()
            memoryCleanupToken = token
            memoryCleanupCompletion = completion@{ result ->
                if (memoryCleanupToken !== token) return@completion
                memoryCleanupToken = null
                memoryCleanupCompletion = null
                memoryCleanupAutomation?.cancel()
                memoryCleanupAutomation = null
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            handler.post {
                if (memoryCleanupToken !== token) return@post
                memoryCleanupAutomation = GoogleAppForceStopAutomation(
                    handler = handler,
                    rootProvider = { rootInActiveWindow },
                    allRootsProvider = {
                        val winRoots = runCatching { windows.mapNotNull { it.root } }.getOrNull().orEmpty()
                        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
                        (winRoots + listOfNotNull(activeRoot)).distinct()
                    },
                    currentPackageProvider = {
                        rootInActiveWindow?.packageName?.toString()
                    },
                    performBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
                    launchDetails = launchDashboard,
                    onFinished = { result -> finishMemoryCleanup(token, result) }
                )
                memoryCleanupAutomation?.start()
            }
            continuation.invokeOnCancellation {
                handler.post { cancelMemoryCleanup(token) }
            }
        }
    }

    /**
     * 최근 앱에서 [taskTitle] 카드의 닫기 버튼을 눌러 앱을 종료한다.
     * Gemini 종료와 같은 제스처/탐색 경로를 재사용한다.
     */
    internal suspend fun closeAppFromRecents(
        taskTitle: String,
        closeDescription: String
    ): CloseGeminiAppResult {
        return suspendCancellableCoroutine { continuation ->
            closeAppFromRecents(
                taskTitle = taskTitle,
                closeDescription = closeDescription
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                closeAppCompletion = null
            }
        }
    }

    private fun closeAppFromRecents(
        taskTitle: String,
        closeDescription: String,
        onFinished: (CloseGeminiAppResult) -> Unit
    ) {
        if (closeAppCompletion != null) {
            onFinished(CloseGeminiAppResult.Failure("앱 종료가 이미 진행 중입니다."))
            return
        }

        closeTaskTitle = taskTitle
        closeTaskDescription = closeDescription
        closeAppCompletion = onFinished
        handler.post {
            val opened = tapDexRecentsButton {
                handler.postDelayed(
                    { closeNextTaskCard(closedCount = 0, clickCount = 0) },
                    RECENTS_OPEN_WAIT_MS
                )
            }
            if (!opened) {
                finishCloseApp(CloseGeminiAppResult.RecentsUnavailable)
            }
        }
    }

    private fun tapCoordinates(x: Float, y: Float, onCompleted: (() -> Unit)? = null): Boolean {
        val tapPath = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tapPath, 0L, TAP_GESTURE_DURATION_MS))
            .build()

        return dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleted?.invoke()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onCompleted?.invoke()
                }
            },
            handler
        )
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
                    finishCloseApp(CloseGeminiAppResult.RecentsUnavailable)
                }
            },
            handler
        )
    }

    private fun closeNextTaskCard(closedCount: Int, clickCount: Int) {
        serviceScope.launch(Dispatchers.Default) {
            if (clickCount >= MAX_TASK_CLOSE_CLICKS) {
                val result = if (closedCount > 0) {
                    CloseGeminiAppResult.Success(closedCount)
                } else {
                    CloseGeminiAppResult.Failure("${closeTaskTitle} 닫기 버튼을 누르지 못했습니다.")
                }
                finishCloseAppAfterDismissingRecents(result)
                return@launch
            }

            val closeNode = findTaskCloseNode()
            if (closeNode == null) {
                finishCloseAppAfterDismissingRecents(
                    if (closedCount > 0) {
                        CloseGeminiAppResult.Success(closedCount)
                    } else {
                        CloseGeminiAppResult.NotFound
                    }
                )
                return@launch
            }

            val clicked = withContext(Dispatchers.Main.immediate) {
                AccessibilityNodeTraversal.clickNodeOrParent(closeNode, MAX_CLICKABLE_PARENT_DEPTH)
            }
            if (!clicked) {
                finishCloseAppAfterDismissingRecents(
                    CloseGeminiAppResult.Failure("${closeTaskTitle} 닫기 버튼을 누르지 못했습니다.")
                )
                return@launch
            }

            delay(CARD_CLOSE_WAIT_MS)
            closeNextTaskCard(
                closedCount = closedCount + 1,
                clickCount = clickCount + 1
            )
        }
    }

    private fun finishCloseAppAfterDismissingRecents(result: CloseGeminiAppResult) {
        handler.post {
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed(
                { finishCloseApp(result) },
                RECENTS_DISMISS_WAIT_MS
            )
        }
    }

    private fun findTaskCloseNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        // 1) 네이티브 텍스트 인덱스로 닫기 버튼 1순위 탐색 (IPC 극소화)
        val nativeClose = runCatching {
            root.findAccessibilityNodeInfosByText(closeTaskDescription)
        }.getOrNull().orEmpty()
        nativeClose.firstOrNull { it.isClickable }?.let { return it }

        // 2) 지연 평가 조기 종료로 닫기 설명 노드 탐색
        AccessibilityNodeTraversal.lazyTraverse(root).firstOrNull { node ->
            node.contentDescription?.toString() == closeTaskDescription && node.isClickable
        }?.let { return it }

        // 3) 태스크 타이틀 기반 상위 탐색
        val titleNodes = runCatching {
            root.findAccessibilityNodeInfosByText(closeTaskTitle)
        }.getOrNull()?.filter { it.text?.toString() == closeTaskTitle }
            ?: AccessibilityNodeTraversal.lazyTraverse(root).filter { it.text?.toString() == closeTaskTitle }.toList()

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
                    node.contentDescription?.toString() == closeTaskDescription
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

    private fun finishCloseApp(result: CloseGeminiAppResult) {
        val completion = closeAppCompletion ?: return
        closeAppCompletion = null
        completion(result)
    }

    private fun finishMemoryCleanup(result: MemoryCleanupResult) {
        val token = memoryCleanupToken ?: return
        finishMemoryCleanup(token, result)
    }

    private fun finishMemoryCleanup(token: Any, result: MemoryCleanupResult) {
        if (memoryCleanupToken !== token) return
        memoryCleanupCompletion?.invoke(result)
    }

    private fun cancelMemoryCleanup(token: Any) {
        if (memoryCleanupToken !== token) return
        memoryCleanupToken = null
        memoryCleanupCompletion = null
        memoryCleanupAutomation?.cancel()
        memoryCleanupAutomation = null
    }


    private fun copyTextToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("prompt", text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard?.setPrimaryClip(clip)
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
        private const val MAX_TASK_CLOSE_CLICKS = 10
        private const val MAX_CLICKABLE_PARENT_DEPTH = 8
        private const val TITLE_ANCESTOR_SEARCH_DEPTH = 4
        private const val TAP_GESTURE_DURATION_MS = 60L
    }
}


