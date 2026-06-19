package com.example.gemgemgen.automation.android

import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway

internal abstract class AccessibilityPromptAutomation(
    protected val handler: Handler,
    private val targetAppName: String
) : PromptAutomationGateway {
    override fun sendPrompt(
        prompt: String,
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        handler.post {
            openNewChat(
                newChatMode = newChatMode,
                onStateChange = onStateChange,
                onDone = {
                    setPromptText(
                        prompt = prompt,
                        attempt = 1,
                        onStateChange = onStateChange,
                        onDone = {
                            clickSendWhenReady(
                                prompt = prompt,
                                attempt = 1,
                                onStateChange = onStateChange,
                                onDone = onDone
                            )
                        }
                    )
                }
            )
        }
    }

    override fun cancelCurrentRun() {
        handler.removeCallbacksAndMessages(null)
    }

    protected abstract fun openNewChat(
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    )

    protected abstract fun findInputNode(): AccessibilityNodeInfo?

    protected abstract fun findSendNode(): AccessibilityNodeInfo?

    protected open fun recoverFromInputFailure(
        onStateChange: (AutomationRunState) -> Unit
    ) = Unit

    protected fun retryOrFail(
        startedAtMillis: Long,
        failureMessage: String,
        onStateChange: (AutomationRunState) -> Unit,
        retry: () -> Unit
    ) {
        val elapsedMillis = SystemClock.uptimeMillis() - startedAtMillis
        val retryWaitMillis = AutomationRetryWaitPolicy.nextDelayMillis(elapsedMillis)

        if (retryWaitMillis == null) {
            onStateChange(AutomationRunState.Failure(failureMessage))
        } else {
            handler.postDelayed(retry, retryWaitMillis)
        }
    }

    protected fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        return findClickableNodeOrParent(node)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun setPromptText(
        prompt: String,
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("입력창 찾는 중 (#$attempt)"))

        val inputNode = findInputNode()
        if (inputNode != null) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    prompt
                )
            }
            if (inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                onStateChange(AutomationRunState.Running("프롬프트 입력 반영 확인 중"))
                handler.postDelayed(
                    {
                        if (isPromptTextApplied(prompt)) {
                            onStateChange(AutomationRunState.Running("프롬프트 입력 완료"))
                            onDone()
                        } else {
                            handlePromptInputFailure(
                                prompt = prompt,
                                attempt = attempt,
                                startedAtMillis = startedAtMillis,
                                failureMessage = "$targetAppName 프롬프트 입력 반영 실패",
                                onStateChange = onStateChange,
                                onDone = onDone
                            )
                        }
                    },
                    INPUT_CONFIRM_WAIT_MS
                )
                return
            }
        }

        handlePromptInputFailure(
            prompt = prompt,
            attempt = attempt,
            startedAtMillis = startedAtMillis,
            failureMessage = "$targetAppName 입력창 못 찾음",
            onStateChange = onStateChange,
            onDone = onDone
        )
    }

    private fun handlePromptInputFailure(
        prompt: String,
        attempt: Int,
        startedAtMillis: Long,
        failureMessage: String,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        recoverFromInputFailure(onStateChange)

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = failureMessage,
            onStateChange = onStateChange
        ) {
            setPromptText(
                prompt = prompt,
                attempt = attempt + 1,
                startedAtMillis = startedAtMillis,
                onStateChange = onStateChange,
                onDone = onDone
            )
        }
    }

    private fun clickSendWhenReady(
        prompt: String,
        attempt: Int,
        startedAtMillis: Long = SystemClock.uptimeMillis(),
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    ) {
        onStateChange(AutomationRunState.Running("보내기 버튼 활성화 대기 중 (#$attempt)"))

        val node = findSendNode()
        val clickableNode = node?.let(::findClickableNodeOrParent)
        if (clickableNode != null && clickableNode.isEnabled &&
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ) {
            onStateChange(AutomationRunState.Running("보내기 클릭 후 전송 확인 중"))
            handler.postDelayed(
                {
                    when (checkPromptInputAfterSend(prompt)) {
                        PromptInputAfterSend.Empty -> onDone()
                        PromptInputAfterSend.StillPresent,
                        PromptInputAfterSend.Unknown -> {
                            retryOrFail(
                                startedAtMillis = startedAtMillis,
                                failureMessage =
                                    "$targetAppName 보내기 클릭 후 전송 완료를 확인하지 못함",
                                onStateChange = onStateChange
                            ) {
                                clickSendWhenReady(
                                    prompt = prompt,
                                    attempt = attempt + 1,
                                    startedAtMillis = startedAtMillis,
                                    onStateChange = onStateChange,
                                    onDone = onDone
                                )
                            }
                        }
                    }
                },
                SEND_CONFIRM_WAIT_MS
            )
            return
        }

        retryOrFail(
            startedAtMillis = startedAtMillis,
            failureMessage = if (node == null) {
                "$targetAppName 보내기 못 찾음"
            } else {
                "$targetAppName 보내기 버튼이 아직 활성화되지 않음"
            },
            onStateChange = onStateChange
        ) {
            clickSendWhenReady(
                prompt = prompt,
                attempt = attempt + 1,
                startedAtMillis = startedAtMillis,
                onStateChange = onStateChange,
                onDone = onDone
            )
        }
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
        const val SEND_CONFIRM_WAIT_MS = 1000L
    }
}
