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
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.CloseGeminiAppResult
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.core.AppDefaults
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class GeminiAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var closeAppCompletion: ((CloseGeminiAppResult) -> Unit)? = null
    private var memoryCleanupToken: Any? = null
    private var memoryCleanupCompletion: ((MemoryCleanupResult) -> Unit)? = null
    private var memoryCleanupAutomation: DeviceCareMemoryAutomation? = null
    private var previousMemoryPackageRestriction: Array<String>? = null
    private var closeTaskTitle: String = GEMINI_TASK_TITLE
    private var closeTaskDescription: String = GEMINI_CLOSE_DESCRIPTION
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
        clearPackageRestriction()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        finishMemoryCleanup(MemoryCleanupResult.Failure("접근성 서비스가 중단되었습니다."))
        finishCloseApp(CloseGeminiAppResult.Failure("접근성 서비스가 중단되었습니다."))
        ProcessAutomationHolder.onAccessibilityLost()
        handler.removeCallbacksAndMessages(null)
        clearPackageRestriction()
    }

    override fun onDestroy() {
        finishMemoryCleanup(MemoryCleanupResult.Failure("접근성 서비스가 종료되었습니다."))
        if (activeService == this) {
            activeService = null
        }
        finishCloseApp(CloseGeminiAppResult.Failure("접근성 서비스가 종료되었습니다."))
        ProcessAutomationHolder.onAccessibilityLost()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    internal fun gatewayFor(targetApp: AutomationTargetApp): PromptAutomationGateway {
        val delegate = when (targetApp) {
            AutomationTargetApp.GEMINI -> geminiAutomation
            AutomationTargetApp.CHATGPT -> chatGptAutomation
        }
        return PackageScopedPromptAutomation(
            delegate = delegate,
            targetApp = targetApp,
            service = this
        )
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
                restoreMemoryPackageRestriction()
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }

            handler.post {
                if (memoryCleanupToken !== token) return@post
                previousMemoryPackageRestriction = serviceInfo?.packageNames?.copyOf()
                restrictPackagesToDeviceCare()
                memoryCleanupAutomation = DeviceCareMemoryAutomation(
                    handler = handler,
                    rootProvider = { rootInActiveWindow },
                    currentPackageProvider = {
                        rootInActiveWindow?.packageName?.toString()
                    },
                    performBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
                    launchDashboard = launchDashboard,
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
            // Recents/system UI must stay visible to package filter.
            clearPackageRestriction()
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

    private fun restrictPackagesTo(targetApp: AutomationTargetApp) {
        applyAccessibilitySubscription(packageNamesFor(targetApp))
    }

    private fun restrictPackagesToDeviceCare() {
        applyAccessibilitySubscription(arrayOf(DEVICE_CARE_PACKAGE_NAME))
    }

    private fun clearPackageRestriction() {
        applyAccessibilitySubscription(packageNames = null)
    }

    private fun restoreMemoryPackageRestriction() {
        val previousPackageRestriction = previousMemoryPackageRestriction
        previousMemoryPackageRestriction = null
        applyAccessibilitySubscription(previousPackageRestriction)
    }

    private fun applyAccessibilitySubscription(packageNames: Array<String>?) {
        val info = serviceInfo ?: return
        info.eventTypes = 0
        info.packageNames = packageNames
        setServiceInfo(info)
    }

    private fun packageNamesFor(targetApp: AutomationTargetApp): Array<String> {
        return when (targetApp) {
            AutomationTargetApp.GEMINI -> arrayOf(
                AppDefaults.GEMINI_PACKAGE_NAME,
                AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME
            )
            AutomationTargetApp.CHATGPT -> arrayOf(AppDefaults.CHATGPT_PACKAGE_NAME)
        }
    }

    private class PackageScopedPromptAutomation(
        private val delegate: PromptAutomationGateway,
        private val targetApp: AutomationTargetApp,
        private val service: GeminiAccessibilityService
    ) : PromptAutomationGateway {
        override fun sendPrompt(
            prompt: String,
            newChatMode: NewChatMode,
            onStateChange: (AutomationRunState) -> Unit,
            onDone: () -> Unit
        ) {
            service.restrictPackagesTo(targetApp)
            delegate.sendPrompt(
                prompt = prompt,
                newChatMode = newChatMode,
                onStateChange = { state ->
                    if (state is AutomationRunState.Failure || state is AutomationRunState.Stopped) {
                        service.clearPackageRestriction()
                    }
                    onStateChange(state)
                },
                onDone = {
                    service.clearPackageRestriction()
                    onDone()
                }
            )
        }

        override fun cancelCurrentRun() {
            delegate.cancelCurrentRun()
            service.clearPackageRestriction()
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
                    finishCloseApp(CloseGeminiAppResult.RecentsUnavailable)
                }
            },
            handler
        )
    }

    private fun closeNextTaskCard(closedCount: Int, clickCount: Int) {
        if (clickCount >= MAX_TASK_CLOSE_CLICKS) {
            val result = if (closedCount > 0) {
                CloseGeminiAppResult.Success(closedCount)
            } else {
                CloseGeminiAppResult.Failure("${closeTaskTitle} 닫기 버튼을 누르지 못했습니다.")
            }
            finishCloseAppAfterDismissingRecents(result)
            return
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
            return
        }

        if (!clickNodeOrParent(closeNode)) {
            finishCloseAppAfterDismissingRecents(
                CloseGeminiAppResult.Failure("${closeTaskTitle} 닫기 버튼을 누르지 못했습니다.")
            )
            return
        }

        handler.postDelayed(
            {
                closeNextTaskCard(
                    closedCount = closedCount + 1,
                    clickCount = clickCount + 1
                )
            },
            CARD_CLOSE_WAIT_MS
        )
    }

    private fun finishCloseAppAfterDismissingRecents(result: CloseGeminiAppResult) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed(
            { finishCloseApp(result) },
            RECENTS_DISMISS_WAIT_MS
        )
    }

    private fun findTaskCloseNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val nodes = flattenNodes(root)
        nodes.firstOrNull { node ->
            node.contentDescription?.toString() == closeTaskDescription && node.isClickable
        }?.let { return it }

        val titleNodes = nodes.filter { node ->
            node.text?.toString() == closeTaskTitle
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
        restoreMemoryPackageRestriction()
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
        private const val TITLE_ANCESTOR_SEARCH_DEPTH = 4
        private const val DEVICE_CARE_PACKAGE_NAME = "com.samsung.android.lool"
    }
}
