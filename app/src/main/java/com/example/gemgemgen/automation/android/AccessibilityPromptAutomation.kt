// 역할: 코루틴 백그라운드 스케줄링을 통해 외부 AI 앱의 화면 요소를 탐색하고 프롬프트를 비차단 방식으로 자동 입력합니다.
package com.example.gemgemgen.automation.android

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
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
    private val targetAppName: String
) : PromptAutomationGateway {
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
        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                prompt
            )
        }
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    protected suspend fun <T> retryUntilFound(
        actionName: String,
        failureMessage: String,
        notifyState: suspend (AutomationRunState) -> Unit,
        action: suspend (attempt: Int) -> T?
    ): T? {
        val startedAtMillis = SystemClock.uptimeMillis()
        var attempt = 1

        while (coroutineContext.isActive) {
            notifyState(AutomationRunState.Running("$actionName (#$attempt)"))
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
        val newChatSuccess = openNewChat(newChatMode, notifyState)
        if (!newChatSuccess) return false

        val inputSuccess = setPromptText(prompt, notifyState)
        if (!inputSuccess) return false

        val sendSuccess = clickSendWhenReady(prompt, notifyState)
        if (!sendSuccess) return false

        return true
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
            if (isSendConfirmed(prompt)) true else null
        }
        return confirmed == true
    }

    private fun checkPromptInputAfterSend(prompt: String): PromptInputAfterSend {
        val inputText = findInputNode()?.text?.toString() ?: return PromptInputAfterSend.Unknown

        return if (inputText.contains(prompt)) {
            PromptInputAfterSend.StillPresent
        } else {
            PromptInputAfterSend.Empty
        }
    }

    private fun isPromptTextApplied(prompt: String): Boolean {
        return findInputNode()?.text?.toString()?.contains(prompt) == true
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
        const val INPUT_CONFIRM_WAIT_MS = 500L
        const val SEND_CONFIRM_WAIT_MS = 500L
    }
}
