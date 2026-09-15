// 역할: 입력창 탭 활성화, SET_TEXT·붙여넣기 2중 주입 및 전송 보강으로 프롬프트를 자동 입력하거나 붙여넣기만 합니다.
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

        val notifyState: suspend (AutomationRunState) -> Unit = { state ->
            withContext(mainDispatcher) {
                onStateChange(state)
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

        val notifyState: suspend (AutomationRunState) -> Unit = { state ->
            withContext(mainDispatcher) {
                onStateChange(state)
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

        val targetNode = findInputNode() ?: inputNode
        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                prompt
            )
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (!isPromptTextApplied(prompt)) {
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
                notifyState(AutomationRunState.Running("$actionName (#$attempt)"))
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
        val inputNode = retryUntilFound(
            actionName = "입력창 찾는 중",
            failureMessage = "$targetAppName 입력창 못 찾음",
            notifyState = notifyState
        ) {
            val node = findInputNode()
            if (node == null) {
                recoverFromInputFailure(notifyState)
            }
            node
        } ?: return false

        val applied = applyPromptText(inputNode, prompt)
        if (!applied) {
            notifyState(AutomationRunState.Failure("$targetAppName 프롬프트 입력 실패"))
            return false
        }

        notifyState(AutomationRunState.Running("프롬프트 입력 반영 확인 중"))
        delay(INPUT_CONFIRM_WAIT_MS)

        if (isPromptTextApplied(prompt)) {
            notifyState(AutomationRunState.Running("프롬프트 입력 완료"))
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

        notifyState(AutomationRunState.Running("보내기 클릭 후 전송 확인 중"))
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
            }
            if (isSendConfirmed(prompt)) true else null
        }
        return confirmed == true
    }

    private fun checkPromptInputAfterSend(prompt: String): PromptInputAfterSend {
        val inputNode = findInputNode() ?: return PromptInputAfterSend.Unknown
        val inputText = inputNode.text?.toString() ?: return PromptInputAfterSend.Unknown
        val hint = inputNode.hintText?.toString()

        if (inputText.isBlank() || (hint != null && inputText == hint)) {
            return PromptInputAfterSend.Empty
        }

        val sample = if (prompt.length > 50) prompt.take(50) else prompt
        return if (inputText.contains(prompt) || inputText.contains(sample)) {
            PromptInputAfterSend.StillPresent
        } else {
            PromptInputAfterSend.Empty
        }
    }

    private fun isPromptTextApplied(prompt: String): Boolean {
        val inputNode = findInputNode() ?: return false
        val text = inputNode.text?.toString() ?: return false
        val hint = inputNode.hintText?.toString()
        if (hint != null && text == hint) {
            return false
        }
        val sample = if (prompt.length > 50) prompt.take(50) else prompt
        return text.contains(prompt) || text.contains(sample)
    }

    private fun findClickableNodeOrParent(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }

        return null
    }

    private enum class PromptInputAfterSend {
        Empty,
        StillPresent,
        Unknown
    }

    private companion object {
        const val LAUNCH_SETTLE_WAIT_MS = 300L
        const val STATE_NOTIFY_THROTTLE_MS = 800L
        const val INPUT_CLICK_SETTLE_MS = 150L
        const val INPUT_PASTE_SETTLE_MS = 100L
        const val INPUT_CONFIRM_WAIT_MS = 500L
        const val SEND_CONFIRM_WAIT_MS = 500L
    }
}
