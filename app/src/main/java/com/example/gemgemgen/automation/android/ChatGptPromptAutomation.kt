// 역할: ChatGPT 앱을 대상으로 코루틴 비차단 방식을 통해 새 대화 전환 및 클립보드 붙여넣기 연동 프롬프트 자동 입력을 수행합니다.
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class ChatGptPromptAutomation(
    coroutineScope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    rootProvider: () -> AccessibilityNodeInfo?,
    copyToClipboard: ((String) -> Unit)? = null
) : AccessibilityPromptAutomation(
    coroutineScope = coroutineScope,
    dispatcher = dispatcher,
    mainDispatcher = mainDispatcher,
    targetAppName = "ChatGPT",
    copyToClipboard = copyToClipboard
) {
    private val nodeFinder = ChatGptAccessibilityNodeFinder(rootProvider)

    override fun onRunFinished() {
        nodeFinder.invalidateCache()
    }

    override suspend fun openNewChat(
        newChatMode: NewChatMode,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return when (newChatMode) {
            NewChatMode.Initial -> clickMenuForInitialChat(notifyState)
            NewChatMode.Subsequent -> clickDirectNewChat(notifyState)
        }
    }

    override fun findInputNode(): AccessibilityNodeInfo? {
        return nodeFinder.findInputNode()
    }

    override fun findSendNode(): AccessibilityNodeInfo? {
        return nodeFinder.findSendNode()
    }

    override suspend fun recoverFromInputFailure(
        notifyState: suspend (AutomationRunState) -> Unit
    ) {
        val closeNode = nodeFinder.findTooManyRequestsCloseNode() ?: return

        notifyState(AutomationRunState.Running("ChatGPT 요청 제한 알림 닫는 중"))
        val clicked = clickNodeOrParent(closeNode)
        if (clicked) {
            notifyState(AutomationRunState.Running("ChatGPT 요청 제한 알림 닫기 완료"))
        }
    }

    private suspend fun clickMenuForInitialChat(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        val alreadyVisibleChat = if (nodeFinder.isMenuOpen()) {
            nodeFinder.findInitialChatNode()
        } else {
            null
        }
        if (alreadyVisibleChat != null) {
            return clickInitialChat(notifyState)
        }

        val menuOpened = retryUntilFound(
            actionName = "ChatGPT 메뉴 여는 중",
            failureMessage = "ChatGPT 메뉴를 찾지 못했습니다.",
            notifyState = notifyState
        ) {
            val menuNode = nodeFinder.findMenuNode()
            if (menuNode != null && clickNodeOrParent(menuNode)) {
                notifyState(AutomationRunState.Running("ChatGPT 메뉴 열기 완료"))
                true
            } else {
                null
            }
        }
        if (menuOpened != true) return false

        return clickInitialChat(notifyState)
    }

    private suspend fun clickInitialChat(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return retryUntilFound(
            actionName = "ChatGPT 채팅 버튼 찾는 중",
            failureMessage = "ChatGPT 채팅 버튼을 찾지 못했습니다.",
            notifyState = notifyState
        ) {
            val chatNode = nodeFinder.findInitialChatNode()
            if (chatNode != null && clickNodeOrParent(chatNode)) {
                notifyState(AutomationRunState.Running("ChatGPT 채팅 버튼 클릭 완료"))
                true
            } else {
                null
            }
        } == true
    }

    private suspend fun clickDirectNewChat(
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        return retryUntilFound(
            actionName = "ChatGPT 새 채팅 찾는 중",
            failureMessage = "ChatGPT 새 채팅 버튼을 찾지 못했습니다.",
            notifyState = notifyState
        ) {
            val newChatNode = nodeFinder.findNewChatNode()
            if (newChatNode != null && clickNodeOrParent(newChatNode)) {
                notifyState(AutomationRunState.Running("ChatGPT 새 채팅 클릭 완료"))
                true
            } else {
                null
            }
        } == true
    }
}
