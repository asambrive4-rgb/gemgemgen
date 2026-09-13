// 역할: Gemini 앱을 대상으로 프롬프트 쾌속 연속 입력 및 새 대화방 안전 전환을 자동 수행합니다.
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

    override fun onRunFinished() {
        nodeFinder.invalidateCache()
    }

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

    override fun waitForResponseIfSupported(
        runToken: Any,
        startedAtMillis: Long,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        // 옵션 A (스마트 쾌속 모드): 이미지 생성을 기다리지 않고 즉시 다음 회차로 진행합니다.
        onStateChange(AutomationRunState.Running("전송 완료 확인 -> 다음 회차 즉시 준비"))
        postDelayedOnRun(runToken, QUICK_MODE_POST_SEND_DELAY_MS) {
            onDone()
        }
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
        nodeFinder.invalidateCache()

        onStateChange(AutomationRunState.Running("새 채팅 찾는 중 (#$attempt)"))

        val node = nodeFinder.findDirectNewChatNode()
        if (node != null && clickNodeOrParent(node)) {
            onStateChange(AutomationRunState.Running("새 채팅 클릭 완료 -> 새 대화방 전환 대기"))
            // 새 채팅 클릭 후 화면이 새 대화방으로 완전히 전환될 수 있도록 안전 버퍼를 둡니다.
            handler.postDelayed({
                waitForNewChatReady(
                    attempt = 1,
                    startedAtMillis = SystemClock.uptimeMillis(),
                    onStateChange = onStateChange,
                    onDone = onDone
                )
            }, NEW_CHAT_TRANSITION_DELAY_MS)
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

    private fun waitForNewChatReady(
        attempt: Int,
        startedAtMillis: Long,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        nodeFinder.invalidateCache()

        val hasStopButton = nodeFinder.isResponseGenerating()
        val inputNode = nodeFinder.findInputNode()

        if (!hasStopButton && inputNode != null) {
            onStateChange(AutomationRunState.Running("새 대화방 진입 확인 완료"))
            onDone()
            return
        }

        if (SystemClock.uptimeMillis() - startedAtMillis >= MAX_NEW_CHAT_WAIT_MS) {
            onStateChange(AutomationRunState.Running("새 대화방 전환 대기 완료"))
            onDone()
            return
        }

        handler.postDelayed({
            waitForNewChatReady(attempt + 1, startedAtMillis, onStateChange, onDone)
        }, NEW_CHAT_POLL_INTERVAL_MS)
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
        const val QUICK_MODE_POST_SEND_DELAY_MS = 150L
        const val NEW_CHAT_TRANSITION_DELAY_MS = 250L
        const val NEW_CHAT_POLL_INTERVAL_MS = 100L
        const val MAX_NEW_CHAT_WAIT_MS = 2000L
    }
}
