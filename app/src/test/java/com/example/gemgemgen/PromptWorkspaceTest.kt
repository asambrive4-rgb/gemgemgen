// 역할: 자동화 화면과 AI 분석 화면 간의 프롬프트 동기화 및 핸드오프 이벤트를 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.core.PromptHandoffEvent
import com.example.gemgemgen.core.PromptWorkspace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PromptWorkspaceTest {

    @Test
    fun updateCurrentPrompt_updatesStateFlow() {
        val workspace = PromptWorkspace()
        assertEquals("", workspace.currentPrompt.value)

        workspace.updateCurrentPrompt("updated prompt")
        assertEquals("updated prompt", workspace.currentPrompt.value)
    }

    @Test
    fun updateCurrentPrompt_consecutiveTypingUpdatesStateFlowImmediately() {
        val workspace = PromptWorkspace()
        workspace.updateCurrentPrompt("draft")
        assertEquals("draft", workspace.currentPrompt.value)

        workspace.updateCurrentPrompt("drafting more text")
        assertEquals("drafting more text", workspace.currentPrompt.value)
    }

    @Test
    fun replaceSegment_delegatesToReplacer() {
        val workspace = PromptWorkspace()
        assertNull(workspace.replaceSegment("exp", "rep", 0))

        workspace.segmentReplacer = { expected, replacement, start ->
            if (expected == "exp") start + replacement.length else null
        }

        val result = workspace.replaceSegment("exp", "replacement", 5)
        assertEquals(16, result)

        val failed = workspace.replaceSegment("wrong", "rep", 5)
        assertNull(failed)
    }

    @Test
    fun handoffEntirely_updatesPromptAndEmitsEvent() = runBlocking {
        val workspace = PromptWorkspace()
        workspace.handoffEntirely("handoff prompt")

        assertEquals("handoff prompt", workspace.currentPrompt.value)
        val event = workspace.handoffEvents.first()
        assertEquals(PromptHandoffEvent.ReplaceEntirely("handoff prompt"), event)
    }
}
