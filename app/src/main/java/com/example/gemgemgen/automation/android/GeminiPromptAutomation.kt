package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode

internal class GeminiPromptAutomation(
    handler: Handler,
    rootProvider: () -> AccessibilityNodeInfo?
) : AccessibilityPromptAutomation(
    handler = handler,
    targetAppName = "Gemini"
) {
    private val nodeFinder = GeminiAccessibilityNodeFinder(
        rootProvider = rootProvider,
        inputResourceId = INPUT_RESOURCE_ID
    )

    override fun openNewChat(
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        when (newChatMode) {
            NewChatMode.Initial -> {
                clickSidebar(
                    attempt = 1,
                    onStateChange = onStateChange,
                    onDone = {
                        clickNewChatNearSearch(
                            attempt = 1,
                            onStateChange = onStateChange,
                            onDone = onDone
                        )
                    }
                )
            }

            NewChatMode.Subsequent -> {
                clickDirectNewChat(
                    attempt = 1,
                    onStateChange = onStateChange,
                    onDone = onDone
                )
            }
        }
    }

    override fun findInputNode(): AccessibilityNodeInfo? {
        return nodeFinder.findInputNode()
    }

    override fun findSendNode(): AccessibilityNodeInfo? {
        return nodeFinder.findNodeByTextOrDescription("보내기")
    }

    private fun clickSidebar(
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("사이드바 여는 중 (#$attempt)"))

        val node = nodeFinder.findNodeByTextOrDescription("사이드바 열기")
        if (node != null && clickNodeOrParent(node)) {
            onStateChange(AutomationRunState.Running("사이드바 열기 완료"))
            onDone()
            return
        }

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = "Gemini 사이드바 열기 못 찾음",
            onStateChange = onStateChange
        ) {
            clickSidebar(attempt + 1, startedAtMillis, onStateChange, onDone)
        }
    }

    private fun clickDirectNewChat(
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("새 채팅 찾는 중 (#$attempt)"))

        val node = nodeFinder.findNodeByTextOrDescription("새 채팅")
        if (node != null && clickNodeOrParent(node)) {
            onStateChange(AutomationRunState.Running("새 채팅 클릭 완료"))
            onDone()
            return
        }

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = "Gemini 새 채팅 못 찾음",
            onStateChange = onStateChange
        ) {
            clickDirectNewChat(attempt + 1, startedAtMillis, onStateChange, onDone)
        }
    }

    private fun clickNewChatNearSearch(
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("채팅 검색 근처 새 채팅 찾는 중 (#$attempt)"))

        val node = nodeFinder.findNewChatNearestToSearch()
        if (node != null && clickNodeOrParent(node)) {
            onStateChange(AutomationRunState.Running("새 채팅 클릭 완료"))
            onDone()
            return
        }

        restoreSidebarTopOnce(onStateChange)
        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = "Gemini 채팅 검색 근처 새 채팅 못 찾음",
            onStateChange = onStateChange
        ) {
            clickNewChatNearSearch(attempt + 1, startedAtMillis, onStateChange, onDone)
        }
    }

    private fun restoreSidebarTopOnce(
        onStateChange: (AutomationRunState) -> Unit
    ) {
        val node = nodeFinder.findSidebarScrollableNode()
        if (node == null) {
            onStateChange(AutomationRunState.Running("사이드바 스크롤 영역 못 찾음"))
            return
        }

        onStateChange(AutomationRunState.Running("사이드바 상단 복원 스크롤 실행"))
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private companion object {
        const val INPUT_RESOURCE_ID =
            "com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet"
    }
}
