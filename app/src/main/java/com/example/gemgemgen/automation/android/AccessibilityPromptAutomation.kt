// 역할: 무한 리셋 방지 임계값 가드, 전송 확인 루프 중복 탐색 차단 및 2중 텍스트 주입으로 프롬프트를 안전하게 전송하는 베이스 자동화 클래스
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
                val flowSuccess = executePromptFlow(prompt, newChatMode, notifyState)
                if (flowSuccess) {
                    withContext(mainDispatcher) {
                        onDone()
                    }
                }
            } catch (_: CancellationException) {
                // 정상 취소
            } catch (error: Throwable) {
                withContext(mainDispatcher) {
                    onStateChange(AutomationRunState.Failure("자동화 실행 실패: ${error.message}"))
                }
            } finally {
                onRunFinished()
            }
        }
    }

    override fun pastePromptOnly(
        prompt: String,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
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
                val flowSuccess = executePromptPasteOnlyFlow(prompt, notifyState)
                if (flowSuccess) {
                    withContext(mainDispatcher) {
                        onDone()
                    }
                }
            } catch (_: CancellationException) {
                withContext(mainDispatcher) {
                    onStateChange(AutomationRunState.Stopped)
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
        val clickableNode = findClickableNodeOrParent(sendNode)
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
        return findClickableNodeOrParent(node)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    protected open suspend fun applyPromptText(
        inputNode: AccessibilityNodeInfo,
        prompt: String
    ): Boolean {
        clickNodeOrParent(inputNode)
        delay(INPUT_CLICK_SETTLE_MS)

        val isInputStillValid = runCatching { inputNode.refresh() }.getOrDefault(false)
        val targetNode = if (isInputStillValid) inputNode else (findInputNode() ?: inputNode)
        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                prompt
            )
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        val textAppliedDirectly = runCatching {
            targetNode.refresh()
            isNodeTextApplied(targetNode, prompt)
        }.getOrDefault(false)

        if (!textAppliedDirectly && !isPromptTextApplied(prompt)) {
            copyToClipboard?.invoke(prompt)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            delay(INPUT_PASTE_SETTLE_MS)
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

    private fun findClickableNodeOrParent(
        node: AccessibilityNodeInfo,
        maxDepth: Int = MAX_CLICKABLE_PARENT_DEPTH
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(maxDepth) {
            if (current == null) return null
            if (current?.isClickable == true) {
                return current
            }
            current = current?.parent
        }

        return null
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
        const val INPUT_CONFIRM_WAIT_MS = 500L
        const val SEND_CONFIRM_WAIT_MS = 500L
    }
}
