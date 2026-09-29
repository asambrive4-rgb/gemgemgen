// 역할: 코루틴 실행 제어, 상위 노드 클릭 위임 및 텍스트 주입·전송 검증을 공통화하여 프롬프트를 안전하게 전송/붙여넣기하는 베이스 자동화 클래스
package com.example.gemgemgen.automation.android

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal abstract class AccessibilityPromptAutomation(
    protected val coroutineScope: CoroutineScope,
    protected val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    protected val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val targetAppName: String,
    protected val copyToClipboard: ((String) -> Unit)? = null
) : PromptAutomationGateway, VariationPromptAutomationGateway {
    private var activeJob: Job? = null

    override fun sendPrompt(
        prompt: String,
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        launchAutomationFlow(
            onStateChange = onStateChange,
            onDone = onDone,
            notifyStoppedOnCancel = false
        ) { notifyState ->
            executePromptFlow(prompt, newChatMode, notifyState)
        }
    }

    override fun pastePromptOnly(
        prompt: String,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        launchAutomationFlow(
            onStateChange = onStateChange,
            onDone = onDone,
            notifyStoppedOnCancel = true
        ) { notifyState ->
            executePromptPasteOnlyFlow(prompt, notifyState)
        }
    }

    private fun launchAutomationFlow(
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit,
        notifyStoppedOnCancel: Boolean,
        flowBlock: suspend (notifyState: suspend (AutomationRunState) -> Unit) -> Boolean
    ) {
        cancelCurrentRun()

        var lastReportedState: AutomationRunState? = null
        val notifyState: suspend (AutomationRunState) -> Unit = { state ->
            if (lastReportedState != state) {
                lastReportedState = state
                withContext(mainDispatcher) {
                    onStateChange(state)
                }
            }
        }

        activeJob = coroutineScope.launch(dispatcher) {
            try {
                val flowSuccess = flowBlock(notifyState)
                if (flowSuccess) {
                    withContext(mainDispatcher) {
                        onDone()
                    }
                } else if (lastReportedState !is AutomationRunState.Failure) {
                    notifyState(AutomationRunState.Failure("$targetAppName 자동화 실행 단계 완료 실패"))
                }
            } catch (_: CancellationException) {
                if (notifyStoppedOnCancel) {
                    withContext(mainDispatcher) {
                        onStateChange(AutomationRunState.Stopped)
                    }
                }
            } catch (error: Throwable) {
                withContext(mainDispatcher) {
                    onStateChange(AutomationRunState.Failure("자동화 실행 실패: ${error.message}"))
                }
            } finally {
                onRunFinished()
            }
        }
    }

    override fun cancelCurrentRun() {
        activeJob?.cancel()
        activeJob = null
        onRunFinished()
    }

    protected open fun onRunFinished() = Unit

    protected abstract suspend fun openNewChat(
        newChatMode: NewChatMode,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean

    protected abstract fun findInputNode(): AccessibilityNodeInfo?

    protected abstract fun findSendNode(): AccessibilityNodeInfo?

    protected open fun performSendClick(sendNode: AccessibilityNodeInfo): Boolean {
        val clickableNode = AccessibilityNodeTraversal.findClickableNodeOrParent(
            sendNode,
            MAX_CLICKABLE_PARENT_DEPTH
        )
        return clickableNode != null && clickableNode.isEnabled &&
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    protected open fun isSendConfirmed(prompt: String): Boolean {
        return checkPromptInputAfterSend(prompt) == PromptInputAfterSend.Empty
    }

    protected open suspend fun recoverFromInputFailure(
        notifyState: suspend (AutomationRunState) -> Unit
    ) = Unit

    protected fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        return AccessibilityNodeTraversal.clickNodeOrParent(node, MAX_CLICKABLE_PARENT_DEPTH)
    }

    protected open fun invalidateInputCache() = Unit

    protected open suspend fun applyPromptText(
        inputNode: AccessibilityNodeInfo,
        prompt: String
    ): Boolean {
        clickNodeOrParent(inputNode)
        delay(INPUT_CLICK_SETTLE_MS)

        val targetNode = inputNode
        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                prompt
            )
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        invalidateInputCache()

        val textAppliedDirectly = runCatching {
            targetNode.refresh()
            isNodeTextApplied(targetNode, prompt)
        }.getOrDefault(false)

        if (!textAppliedDirectly && !isPromptTextApplied(prompt)) {
            delay(INPUT_SETTLE_RECHECK_MS)
            invalidateInputCache()
            if (!isPromptTextApplied(prompt)) {
                copyToClipboard?.invoke(prompt)
                targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                delay(INPUT_PASTE_SETTLE_MS)
            }
        }

        return true
    }

    protected suspend fun <T> retryUntilFound(
        actionName: String,
        failureMessage: String,
        notifyState: suspend (AutomationRunState) -> Unit,
        action: suspend (attempt: Int) -> T?
    ): T? {
        val startedAtMillis = SystemClock.uptimeMillis()
        var attempt = 1
        var lastNotifiedMillis = 0L

        while (coroutineContext.isActive) {
            val now = SystemClock.uptimeMillis()
            if (attempt == 1 || now - lastNotifiedMillis >= STATE_NOTIFY_THROTTLE_MS) {
                val stateText = if (attempt > 1) "$actionName (#$attempt)" else actionName
                notifyState(AutomationRunState.Running(stateText))
                lastNotifiedMillis = now
            }
            val result = action(attempt)
            if (result != null) {
                return result
            }

            val elapsedMillis = SystemClock.uptimeMillis() - startedAtMillis
            val retryWaitMillis = AutomationRetryWaitPolicy.nextDelayMillis(elapsedMillis)

            if (retryWaitMillis == null) {
                notifyState(AutomationRunState.Failure(failureMessage))
                return null
            }

            delay(retryWaitMillis)
            attempt++
        }
        return null
    }

    private suspend fun executePromptFlow(
        prompt: String,
        newChatMode: NewChatMode,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        delay(LAUNCH_SETTLE_WAIT_MS)
        val newChatSuccess = openNewChat(newChatMode, notifyState)
        if (!newChatSuccess) return false

        val inputSuccess = setPromptText(prompt, notifyState)
        if (!inputSuccess) return false

        val sendSuccess = clickSendWhenReady(prompt, notifyState)
        if (!sendSuccess) return false

        return true
    }

    private suspend fun executePromptPasteOnlyFlow(
        prompt: String,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        delay(LAUNCH_SETTLE_WAIT_MS)
        val newChatSuccess = openNewChat(NewChatMode.Initial, notifyState)
        if (!newChatSuccess) return false

        return setPromptText(prompt, notifyState)
    }

    private suspend fun setPromptText(
        prompt: String,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        var lastRecoverAttempt = 0
        val inputNode = retryUntilFound(
            actionName = "입력창 찾는 중",
            failureMessage = "$targetAppName 입력창 못 찾음",
            notifyState = notifyState
        ) { attempt ->
            val node = findInputNode()
            if (node == null && attempt >= MIN_RECOVER_ATTEMPTS && (attempt - lastRecoverAttempt >= MIN_RECOVER_ATTEMPTS)) {
                lastRecoverAttempt = attempt
                recoverFromInputFailure(notifyState)
            }
            node
        } ?: return false

        val applied = applyPromptText(inputNode, prompt)
        if (!applied) {
            notifyState(AutomationRunState.Failure("$targetAppName 프롬프트 입력 실패"))
            return false
        }

        delay(INPUT_CONFIRM_WAIT_MS)

        if (isPromptTextApplied(prompt)) {
            return true
        }

        val retrySuccess = retryUntilFound(
            actionName = "프롬프트 입력 반영 재확인 중",
            failureMessage = "$targetAppName 프롬프트 입력 반영 실패",
            notifyState = notifyState
        ) {
            val currentNode = findInputNode()
            if (currentNode != null) {
                applyPromptText(currentNode, prompt)
            }
            if (isPromptTextApplied(prompt)) true else null
        }
        return retrySuccess == true
    }

    private suspend fun clickSendWhenReady(
        prompt: String,
        notifyState: suspend (AutomationRunState) -> Unit
    ): Boolean {
        val sendSuccess = retryUntilFound(
            actionName = "보내기 버튼 활성화 대기 중",
            failureMessage = "$targetAppName 보내기 못 찾음",
            notifyState = notifyState
        ) {
            val node = findSendNode()
            if (node != null && performSendClick(node)) {
                true
            } else {
                null
            }
        } ?: return false

        delay(SEND_CONFIRM_WAIT_MS)

        if (isSendConfirmed(prompt)) {
            return true
        }

        val confirmed = retryUntilFound(
            actionName = "전송 완료 확인 중",
            failureMessage = "$targetAppName 보내기 클릭 후 전송 완료를 확인하지 못함",
            notifyState = notifyState
        ) {
            if (isSendConfirmed(prompt)) return@retryUntilFound true

            // 이전 클릭이 씹혔거나 무시된 경우 최신 보내기 버튼을 찾아 재클릭
            val node = findSendNode()
            if (node != null) {
                performSendClick(node)
                delay(SEND_CONFIRM_WAIT_MS)
                if (isSendConfirmed(prompt)) return@retryUntilFound true
            }
            null
        }
        return confirmed == true
    }

    private fun checkPromptInputAfterSend(prompt: String): PromptInputAfterSend {
        val inputNode = findInputNode() ?: return PromptInputAfterSend.Unknown
        val text = inputNode.text ?: return PromptInputAfterSend.Unknown
        val hint = inputNode.hintText

        if (text.isBlank() || (hint != null && text.contentEquals(hint))) {
            return PromptInputAfterSend.Empty
        }

        val sample = if (prompt.length > 50) prompt.take(50) else prompt
        return if (text.contains(sample)) {
            PromptInputAfterSend.StillPresent
        } else {
            PromptInputAfterSend.Empty
        }
    }

    private fun isNodeTextApplied(node: AccessibilityNodeInfo, prompt: String): Boolean {
        val text = node.text ?: return false
        val hint = node.hintText
        if (hint != null && text.contentEquals(hint)) {
            return false
        }
        val sample = if (prompt.length > 50) prompt.take(50) else prompt
        return text.contains(sample)
    }

    private fun isPromptTextApplied(prompt: String): Boolean {
        val inputNode = findInputNode() ?: return false
        return isNodeTextApplied(inputNode, prompt)
    }

    private enum class PromptInputAfterSend {
        Empty,
        StillPresent,
        Unknown
    }

    private companion object {
        const val MIN_RECOVER_ATTEMPTS = 3
        const val MAX_CLICKABLE_PARENT_DEPTH = 8
        const val LAUNCH_SETTLE_WAIT_MS = 300L
        const val STATE_NOTIFY_THROTTLE_MS = 1200L
        const val INPUT_CLICK_SETTLE_MS = 150L
        const val INPUT_PASTE_SETTLE_MS = 100L
        const val INPUT_SETTLE_RECHECK_MS = 60L
        const val INPUT_CONFIRM_WAIT_MS = 500L
        const val SEND_CONFIRM_WAIT_MS = 500L
    }
}
