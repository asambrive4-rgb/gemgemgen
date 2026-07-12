package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode

internal class ChatGptPromptAutomation(
    handler: Handler,
    rootProvider: () -> AccessibilityNodeInfo?
) : AccessibilityPromptAutomation(
    handler = handler,
    targetAppName = "ChatGPT"
) {
    private val nodeFinder = ChatGptAccessibilityNodeFinder(rootProvider)

    override fun onRunFinished() {
        nodeFinder.invalidateCache()
    }

    override fun openNewChat(
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        when (newChatMode) {
            NewChatMode.Initial -> clickMenuForInitialChat(
                attempt = 1,
                onStateChange = onStateChange,
                onDone = onDone
            )

            NewChatMode.Subsequent -> clickDirectNewChat(
                attempt = 1,
                onStateChange = onStateChange,
                onDone = onDone
            )
        }
    }

    override fun findInputNode(): AccessibilityNodeInfo? {
        return nodeFinder.findInputNode()
    }

    override fun findSendNode(): AccessibilityNodeInfo? {
        return nodeFinder.findSendNode()
    }

    override fun recoverFromInputFailure(
        onStateChange: (AutomationRunState) -> Unit
    ) {
        val closeNode = nodeFinder.findTooManyRequestsCloseNode() ?: return

        onStateChange(AutomationRunState.Running("ChatGPT 요청 제한 알림 닫는 중"))
        val clicked = clickNodeOrParent(closeNode)
        if (clicked) {
            onStateChange(AutomationRunState.Running("ChatGPT 요청 제한 알림 닫기 완료"))
        }
    }

    private fun clickMenuForInitialChat(
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("ChatGPT 메뉴 여는 중 (#$attempt)"))

        val alreadyVisibleChat = if (nodeFinder.isMenuOpen()) {
            nodeFinder.findInitialChatNode()
        } else {
            null
        }
        if (alreadyVisibleChat != null) {
            clickInitialChat(
                attempt = 1,
                onStateChange = onStateChange,
                onDone = onDone
            )
            return
        }

        val menuNode = nodeFinder.findMenuNode()
        if (menuNode != null && clickNodeOrParent(menuNode)) {
            onStateChange(AutomationRunState.Running("ChatGPT 메뉴 열기 완료"))
            clickInitialChat(
                attempt = 1,
                onStateChange = onStateChange,
                onDone = onDone
            )
            return
        }

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = "ChatGPT 메뉴를 찾지 못했습니다.",
            onStateChange = onStateChange
        ) {
            clickMenuForInitialChat(attempt + 1, startedAtMillis, onStateChange, onDone)
        }
    }

    private fun clickInitialChat(
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("ChatGPT 채팅 버튼 찾는 중 (#$attempt)"))

        val chatNode = nodeFinder.findInitialChatNode()
        if (chatNode != null && clickNodeOrParent(chatNode)) {
            onStateChange(AutomationRunState.Running("ChatGPT 채팅 버튼 클릭 완료"))
            onDone()
            return
        }

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = "ChatGPT 채팅 버튼을 찾지 못했습니다.",
            onStateChange = onStateChange
        ) {
            clickInitialChat(attempt + 1, startedAtMillis, onStateChange, onDone)
        }
    }

    private fun clickDirectNewChat(
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("ChatGPT 새 채팅 찾는 중 (#$attempt)"))

        val newChatNode = nodeFinder.findNewChatNode()
        if (newChatNode != null && clickNodeOrParent(newChatNode)) {
            onStateChange(AutomationRunState.Running("ChatGPT 새 채팅 클릭 완료"))
            onDone()
            return
        }

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = "ChatGPT 새 채팅 버튼을 찾지 못했습니다.",
            onStateChange = onStateChange
        ) {
            clickDirectNewChat(attempt + 1, startedAtMillis, onStateChange, onDone)
        }
    }
}
