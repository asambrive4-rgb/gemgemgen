// 역할: 최초 실행 시 사이드바 정규 세션 확보 및 툴바 기반 반복/오류 탈출로 Gemini 앱 새 대화 전환 및 프롬프트를 자동 전송합니다.
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

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
        android.util.Log.i("GeminiPerf", "[Gemini Run Stats] " + nodeFinder.getPerformanceStats())
        nodeFinder.invalidateCache()
        nodeFinder.resetPerformanceStats()
    }

    override suspend fun openNewChat(
        newChatMode: NewChatMode,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return when (newChatMode) {
            NewChatMode.Initial -> {
                // 최초 1회 실행 시에는 이전 대화 잔류 상태나 임시채팅 오진입을 방지하기 위해
                // 상단 툴바 우회를 건너뛰고, 반드시 사이드바 드로어를 열어 정규 '새 채팅'으로 진입합니다.
                val sidebarOpened = clickSidebar(notifyState)
                if (!sidebarOpened) {
                    // 사이드바 열기가 끝내 실패한 비정상 화면일 경우에만 안전망(Fallback)으로 상단 툴바 시도
                    return tryFallbackToolbarNewChat(notifyState)
                }
                clickNewChatNearSearch(notifyState)
            }

            NewChatMode.Subsequent -> {
                // 2회차 이후 반복 실행 시에는 이미 정규 세션이 확보되었으므로
                // 불필요한 사이드바 개폐 오버헤드를 없애고 상단 툴바 또는 직접 새 채팅으로 신속 전환합니다.
                val toolbarNewChat = nodeFinder.findToolbarNewChatNode()
                if (toolbarNewChat != null && clickNodeOrParent(toolbarNewChat)) {
                    notifyState(AutomationRunState.Running("상단 툴바 새 채팅 진입 완료"))
                    nodeFinder.invalidateCache()
                    delay(NEW_CHAT_SETTLE_MS)
                    true
                } else {
                    clickDirectNewChat(notifyState)
                }
            }
        }
    }

    override suspend fun recoverFromInputFailure(
        notifyState: suspend (AutomationRunState) -> Unit
    ) {
        // 이전 오류 화면(안전 가이드라인 경고 등)에 갇히지 않도록 상단 툴바의 고정 진입점("새 채팅" 버튼 등)을 직접 식별하고 클릭 후 안정화
        val toolbarNode = nodeFinder.findToolbarNewChatNode() ?: return
        notifyState(AutomationRunState.Running("Gemini 오류 화면 탈출을 위한 새 채팅 전환 중"))
        if (clickNodeOrParent(toolbarNode)) {
            notifyState(AutomationRunState.Running("Gemini 오류 화면 탈출 완료"))
            nodeFinder.invalidateCache()
            delay(NEW_CHAT_SETTLE_MS)
        }
    }

    override fun findInputNode(): AccessibilityNodeInfo? {
        return nodeFinder.findInputNode()
    }

    override fun findSendNode(): AccessibilityNodeInfo? {
        return nodeFinder.findSendNode()
    }

    private suspend fun clickSidebar(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        // 이미 사이드바 드로어가 열려 있는 경우 불필요한 재클릭 없이 바로 통과
        if (nodeFinder.findNodeByTextOrDescription("사이드바 닫기") != null) {
            notifyState(AutomationRunState.Running("사이드바가 이미 열려 있음"))
            return true
        }

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

    private suspend fun tryFallbackToolbarNewChat(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        val toolbarNewChat = nodeFinder.findToolbarNewChatNode() ?: return false
        notifyState(AutomationRunState.Running("사이드바 미감지로 상단 툴바 새 채팅 대체 시도"))
        return if (clickNodeOrParent(toolbarNewChat)) {
            notifyState(AutomationRunState.Running("상단 툴바 새 채팅 진입 완료"))
            nodeFinder.invalidateCache()
            delay(NEW_CHAT_SETTLE_MS)
            true
        } else {
            false
        }
    }

    private suspend fun clickDirectNewChat(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return retryUntilFound(
            actionName = "새 채팅 찾는 중",
            failureMessage = "Gemini 새 채팅 못 찾음",
            notifyState = notifyState
        ) {
            val node = nodeFinder.findNodeByTextOrDescription("새 채팅")
            if (node != null && clickNodeOrParent(node)) {
                notifyState(AutomationRunState.Running("새 채팅 클릭 완료"))
                nodeFinder.invalidateCache()
                delay(NEW_CHAT_SETTLE_MS)
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
                nodeFinder.invalidateCache()
                delay(NEW_CHAT_SETTLE_MS)
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
        const val NEW_CHAT_SETTLE_MS = 350L
    }
}
