package com.example.gemgemgen

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GeminiAccessibilityService : AccessibilityService(), GeminiPromptSender {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        if (activeService == this) {
            activeService = null
        }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun sendPrompt(
        prompt: String,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    ) {
        handler.post(
            {
                clickSidebar(
                    attempt = 1,
                    onStateChange = onStateChange,
                    onDone = {
                        handler.postDelayed(
                            {
                                clickNewChatNearSearch(
                                    attempt = 1,
                                    onStateChange = onStateChange,
                                    onDone = {
                                        handler.postDelayed(
                                            {
                                                setPromptText(
                                                    prompt = prompt,
                                                    attempt = 1,
                                                    onStateChange = onStateChange,
                                                    onDone = {
                                                        handler.postDelayed(
                                                            {
                                                                clickSendWhenReady(
                                                                    prompt = prompt,
                                                                    attempt = 1,
                                                                    onStateChange = onStateChange,
                                                                    onDone = onDone
                                                                )
                                                            },
                                                            AFTER_INPUT_WAIT_MS
                                                        )
                                                    }
                                                )
                                            },
                                            AFTER_NEW_CHAT_WAIT_MS
                                        )
                                    }
                                )
                            },
                            AFTER_SIDEBAR_WAIT_MS
                        )
                    }
                )
            }
        )
    }

    override fun cancelCurrentRun() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun clickSidebar(
        attempt: Int,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationUiState.Running("사이드바 여는 중 ($attempt/$SIDEBAR_MAX_ATTEMPTS)"))

        val node = findNodeByTextOrDescription("사이드바 열기")
        if (node != null && clickNodeOrParent(node)) {
            onStateChange(AutomationUiState.Running("사이드바 열기 완료"))
            onDone()
            return
        }

        retryOrFail(
            attempt = attempt,
            maxAttempts = SIDEBAR_MAX_ATTEMPTS,
            failureMessage = "사이드바 열기 못 찾음",
            onStateChange = onStateChange
        ) {
            clickSidebar(attempt + 1, onStateChange, onDone)
        }
    }

    private fun clickNewChatNearSearch(
        attempt: Int,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationUiState.Running("채팅 검색 근처 새 채팅 찾는 중 ($attempt/$NEW_CHAT_MAX_ATTEMPTS)"))

        val node = findNewChatNearestToSearch()
        if (node != null && clickNodeOrParent(node)) {
            onStateChange(AutomationUiState.Running("새 채팅 클릭 완료"))
            onDone()
            return
        }

        retryOrFail(
            attempt = attempt,
            maxAttempts = NEW_CHAT_MAX_ATTEMPTS,
            failureMessage = "채팅 검색 근처 새 채팅 못 찾음",
            onStateChange = onStateChange
        ) {
            clickNewChatNearSearch(attempt + 1, onStateChange, onDone)
        }
    }

    private fun setPromptText(
        prompt: String,
        attempt: Int,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationUiState.Running("입력창 찾는 중 ($attempt/$INPUT_MAX_ATTEMPTS)"))

        val inputNode = findInputNode()
        if (inputNode != null) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    prompt
                )
            }
            if (inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                onStateChange(AutomationUiState.Running("프롬프트 입력 완료"))
                onDone()
                return
            }
        }

        retryOrFail(
            attempt = attempt,
            maxAttempts = INPUT_MAX_ATTEMPTS,
            failureMessage = "입력창 못 찾음",
            onStateChange = onStateChange
        ) {
            setPromptText(prompt, attempt + 1, onStateChange, onDone)
        }
    }

    private fun clickSendWhenReady(
        prompt: String,
        attempt: Int,
        onStateChange: (AutomationUiState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationUiState.Running("보내기 버튼 활성화 대기 중 ($attempt/$SEND_MAX_ATTEMPTS)"))

        val node = findNodeByTextOrDescription("보내기")
        val clickableNode = node?.let(::findClickableNodeOrParent)
        if (clickableNode != null && clickableNode.isEnabled &&
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ) {
            onStateChange(AutomationUiState.Running("보내기 클릭 후 전송 확인 중"))
            handler.postDelayed(
                {
                    when (checkPromptInputAfterSend(prompt)) {
                        PromptInputAfterSend.Empty -> onDone()
                        PromptInputAfterSend.StillPresent,
                        PromptInputAfterSend.Unknown -> {
                            retryOrFail(
                                attempt = attempt,
                                maxAttempts = SEND_MAX_ATTEMPTS,
                                failureMessage = "보내기 클릭 후 전송 완료를 확인하지 못함",
                                onStateChange = onStateChange
                            ) {
                                clickSendWhenReady(prompt, attempt + 1, onStateChange, onDone)
                            }
                        }
                    }
                },
                SEND_CONFIRM_WAIT_MS
            )
            return
        }

        retryOrFail(
            attempt = attempt,
            maxAttempts = SEND_MAX_ATTEMPTS,
            failureMessage = if (node == null) {
                "보내기 못 찾음"
            } else {
                "보내기 버튼이 아직 활성화되지 않음"
            },
            onStateChange = onStateChange
        ) {
            clickSendWhenReady(prompt, attempt + 1, onStateChange, onDone)
        }
    }

    private fun retryOrFail(
        attempt: Int,
        maxAttempts: Int,
        failureMessage: String,
        onStateChange: (AutomationUiState) -> Unit,
        retry: () -> Unit
    ) {
        if (attempt >= maxAttempts) {
            onStateChange(AutomationUiState.Failure(failureMessage))
        } else {
            handler.postDelayed(retry, RETRY_WAIT_MS)
        }
    }

    private fun findInputNode(): AccessibilityNodeInfo? {
        findNodeByViewId(INPUT_RESOURCE_ID)?.let { return it }

        return rootInActiveWindow
            ?.let(::flattenNodes)
            ?.firstOrNull { node ->
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                    node.isEditable
            }
    }

    private fun checkPromptInputAfterSend(prompt: String): PromptInputAfterSend {
        val inputText = findInputNode()?.text?.toString() ?: return PromptInputAfterSend.Unknown

        return if (inputText.contains(prompt)) {
            PromptInputAfterSend.StillPresent
        } else {
            PromptInputAfterSend.Empty
        }
    }

    private fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val nodes = rootInActiveWindow?.let(::flattenNodes) ?: return null
        val searchNode = nodes.firstOrNull { node ->
            node.matchesTextOrDescription("채팅 검색")
        } ?: return null
        val candidates = nodes.filter { node ->
            node.matchesTextOrDescription("새 채팅")
        }.mapNotNull { node ->
            val bounds = node.nodeBounds() ?: return@mapNotNull null
            node to bounds
        }
        val searchBounds = searchNode.nodeBounds() ?: return null

        return NearestNodeSelector.nearestTo(
            anchor = searchBounds,
            candidates = candidates
        ) { it.second }?.first
    }

    private fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
                ?.findAccessibilityNodeInfosByViewId(viewId)
                ?.firstOrNull()
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? {
        return rootInActiveWindow
            ?.let(::flattenNodes)
            ?.firstOrNull { node -> node.matchesTextOrDescription(value) }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }

        return false
    }

    private fun findClickableNodeOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }

        return null
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

    private fun AccessibilityNodeInfo.matchesTextOrDescription(value: String): Boolean {
        return text?.toString()?.contains(value, ignoreCase = true) == true ||
            contentDescription?.toString()?.contains(value, ignoreCase = true) == true
    }

    private fun AccessibilityNodeInfo.nodeBounds(): NodeBounds? {
        val rect = Rect()
        getBoundsInScreen(rect)
        if (rect.isEmpty) return null

        return NodeBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
    }

    companion object {
        var activeService: GeminiAccessibilityService? = null
            private set

        private const val INPUT_RESOURCE_ID =
            "com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet"
        private const val AFTER_SIDEBAR_WAIT_MS = 300L
        private const val AFTER_NEW_CHAT_WAIT_MS = 1500L
        private const val AFTER_INPUT_WAIT_MS = 500L
        private const val SEND_CONFIRM_WAIT_MS = 1000L
        private const val RETRY_WAIT_MS = 1000L
        private const val SIDEBAR_MAX_ATTEMPTS = 10
        private const val NEW_CHAT_MAX_ATTEMPTS = 10
        private const val INPUT_MAX_ATTEMPTS = 10
        private const val SEND_MAX_ATTEMPTS = 10
    }

    private enum class PromptInputAfterSend {
        Empty,
        StillPresent,
        Unknown
    }
}
