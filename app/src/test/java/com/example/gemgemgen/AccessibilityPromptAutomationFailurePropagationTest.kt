// 역할: AccessibilityPromptAutomation에서 전송 및 붙여넣기 플로우 실패 시 안전망 Failure 상태가 올바르게 전파되는지 검증하는 단위 테스트
package com.example.gemgemgen

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.android.AccessibilityPromptAutomation
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.usecase.NewChatMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccessibilityPromptAutomationFailurePropagationTest {

    @Test
    fun sendPrompt_whenFlowFailsWithoutReportingFailure_propagatesDefaultFailure() = runBlocking {
        val failureDeferred = CompletableDeferred<AutomationRunState.Failure>()
        var doneCalled = false

        val automation = FakeAccessibilityPromptAutomation(
            coroutineScope = this,
            targetAppName = "Gemini",
            onOpenNewChat = { _, _ -> false }
        )

        automation.sendPrompt(
            prompt = "test prompt",
            newChatMode = NewChatMode.Initial,
            onStateChange = { state ->
                if (state is AutomationRunState.Failure) {
                    failureDeferred.complete(state)
                }
            },
            onDone = { doneCalled = true }
        )

        val failure = withTimeout(3000) { failureDeferred.await() }
        assertFalse("onDone은 호출되지 않아야 합니다.", doneCalled)
        assertEquals("Gemini 자동화 실행 단계 완료 실패", failure.message)
    }

    @Test
    fun sendPrompt_whenFlowFailsWithReportedFailure_preservesOriginalFailure() = runBlocking {
        val failureDeferred = CompletableDeferred<AutomationRunState.Failure>()
        var doneCalled = false
        val specificError = "Gemini 사이드바 및 상단 툴바 새 채팅 버튼을 찾지 못했습니다."

        val automation = FakeAccessibilityPromptAutomation(
            coroutineScope = this,
            targetAppName = "Gemini",
            onOpenNewChat = { _, notifyState ->
                notifyState(AutomationRunState.Failure(specificError))
                false
            }
        )

        automation.sendPrompt(
            prompt = "test prompt",
            newChatMode = NewChatMode.Initial,
            onStateChange = { state ->
                if (state is AutomationRunState.Failure) {
                    failureDeferred.complete(state)
                }
            },
            onDone = { doneCalled = true }
        )

        val failure = withTimeout(3000) { failureDeferred.await() }
        assertFalse("onDone은 호출되지 않아야 합니다.", doneCalled)
        assertEquals(specificError, failure.message)
    }

    @Test
    fun pastePromptOnly_whenFlowFailsWithoutReportingFailure_propagatesDefaultFailure() = runBlocking {
        val failureDeferred = CompletableDeferred<AutomationRunState.Failure>()
        var doneCalled = false

        val automation = FakeAccessibilityPromptAutomation(
            coroutineScope = this,
            targetAppName = "Gemini",
            onOpenNewChat = { _, _ -> false }
        )

        automation.pastePromptOnly(
            prompt = "test prompt",
            onStateChange = { state ->
                if (state is AutomationRunState.Failure) {
                    failureDeferred.complete(state)
                }
            },
            onDone = { doneCalled = true }
        )

        val failure = withTimeout(3000) { failureDeferred.await() }
        assertFalse("onDone은 호출되지 않아야 합니다.", doneCalled)
        assertEquals("Gemini 자동화 실행 단계 완료 실패", failure.message)
    }

    @Test
    fun pastePromptOnly_whenFlowFailsWithReportedFailure_preservesOriginalFailure() = runBlocking {
        val failureDeferred = CompletableDeferred<AutomationRunState.Failure>()
        var doneCalled = false
        val specificError = "클립보드 준비 실패"

        val automation = FakeAccessibilityPromptAutomation(
            coroutineScope = this,
            targetAppName = "Gemini",
            onOpenNewChat = { _, notifyState ->
                notifyState(AutomationRunState.Failure(specificError))
                false
            }
        )

        automation.pastePromptOnly(
            prompt = "test prompt",
            onStateChange = { state ->
                if (state is AutomationRunState.Failure) {
                    failureDeferred.complete(state)
                }
            },
            onDone = { doneCalled = true }
        )

        val failure = withTimeout(3000) { failureDeferred.await() }
        assertFalse("onDone은 호출되지 않아야 합니다.", doneCalled)
        assertEquals(specificError, failure.message)
    }

    private class FakeAccessibilityPromptAutomation(
        coroutineScope: CoroutineScope,
        targetAppName: String = "TestTarget",
        private val onOpenNewChat: suspend (NewChatMode, suspend (AutomationRunState) -> Unit) -> Boolean
    ) : AccessibilityPromptAutomation(
        coroutineScope = coroutineScope,
        dispatcher = Dispatchers.Unconfined,
        mainDispatcher = Dispatchers.Unconfined,
        targetAppName = targetAppName
    ) {
        override suspend fun openNewChat(
            newChatMode: NewChatMode,
            notifyState: suspend (AutomationRunState) -> Unit
        ): Boolean {
            return onOpenNewChat(newChatMode, notifyState)
        }

        override fun findInputNode(): AccessibilityNodeInfo? = null
        override fun findSendNode(): AccessibilityNodeInfo? = null
    }
}
