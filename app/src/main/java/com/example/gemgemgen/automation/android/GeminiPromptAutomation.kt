// 역할: Gemini 앱을 대상으로 코루틴 비차단 방식을 통해 새 대화 전환 및 클립보드 붙여넣기 연동 프롬프트 자동 입력을 수행합니다.
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class GeminiPromptAutomation(
    coroutineScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    rootProvider: () -> AccessibilityNodeInfo?,
    copyToClipboard: ((String) -> Unit)? = null
) : AccessibilityPromptAutomation(
    coroutineScope = coroutineScope,
    dispatcher = dispatcher,
    mainDispatcher = mainDispatcher,
    targetAppName = "Gemini",
    copyToClipboard = copyToClipboard
) {
    private val nodeFinder = GeminiAccessibilityNodeFinder(
        rootProvider = rootProvider,
        inputResourceId = INPUT_RESOURCE_ID
    )

    override fun onRunFinished() {
        nodeFinder.invalidateCache()
    }

    override suspend fun openNewChat(
        newChatMode: NewChatMode,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return when (newChatMode) {
            NewChatMode.Initial -> {
                val sidebarOpened = clickSidebar(notifyState)
                if (!sidebarOpened) return false
                clickNewChatNearSearch(notifyState)
            }

            NewChatMode.Subsequent -> {
                clickDirectNewChat(notifyState)
            }
        }
    }

    override fun findInputNode(): AccessibilityNodeInfo? {
        return nodeFinder.findInputNode()
    }

    override fun findSendNode(): AccessibilityNodeInfo? {
        return nodeFinder.findNodeByTextOrDescription("보내기")
    }

    private suspend fun clickSidebar(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return retryUntilFound(
            actionName = "사이드바 여는 중",
            failureMessage = "Gemini 사이드바 열기 못 찾음",
            notifyState = notifyState
        ) {
            val node = nodeFinder.findNodeByTextOrDescription("사이드바 열기")
            if (node != null && clickNodeOrParent(node)) {
                notifyState(AutomationRunState.Running("사이드바 열기 완료"))
                true
            } else {
                null
            }
        } == true
    }

    private suspend fun clickDirectNewChat(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        notifyState(AutomationRunState.Running("새 채팅 찾는 중 (#1)"))

        if (!nodeFinder.hasMoreOptions()) {
            notifyState(AutomationRunState.Running("이미 새 대화 상태임 (새 채팅 클릭 생략)"))
            return true
        }

        return retryUntilFound(
            actionName = "새 채팅 찾는 중",
            failureMessage = "Gemini 새 채팅 못 찾음",
            notifyState = notifyState
        ) {
            val node = nodeFinder.findNewChatWithMoreOptions()
            if (node != null && clickNodeOrParent(node)) {
                notifyState(AutomationRunState.Running("새 채팅 클릭 완료"))
                true
            } else {
                null
            }
        } == true
    }

    private suspend fun clickNewChatNearSearch(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return retryUntilFound(
            actionName = "채팅 검색 근처 새 채팅 찾는 중",
            failureMessage = "Gemini 채팅 검색 근처 새 채팅 못 찾음",
            notifyState = notifyState
        ) {
            val node = nodeFinder.findNewChatNearestToSearch()
            if (node != null && clickNodeOrParent(node)) {
                notifyState(AutomationRunState.Running("새 채팅 클릭 완료"))
                true
            } else {
                restoreSidebarTopOnce(notifyState)
                null
            }
        } == true
    }

    private suspend fun restoreSidebarTopOnce(
        notifyState: suspend (AutomationRunState) -> Unit
    ) {
        val node = nodeFinder.findSidebarScrollableNode()
        if (node == null) {
            notifyState(AutomationRunState.Running("사이드바 스크롤 영역 못 찾음"))
            return
        }

        notifyState(AutomationRunState.Running("사이드바 상단 복원 스크롤 실행"))
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private companion object {
        const val INPUT_RESOURCE_ID =
            "com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet"
    }
}
