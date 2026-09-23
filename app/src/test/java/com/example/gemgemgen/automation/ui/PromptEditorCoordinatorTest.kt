// 역할: 프롬프트 에디터 코디네이터의 입력 동기화, 문구 찾기(검색) 및 네비게이션 동작을 검증하는 단위 테스트입니다.
package com.example.gemgemgen.automation.ui

import com.example.gemgemgen.core.ClipboardGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptEditorCoordinatorTest {

    private class TestClipboardGateway(var text: String = "") : ClipboardGateway {
        override fun readText(): String = text
        override fun writeText(text: String) {
            this.text = text
        }
    }

    private fun createCoordinator(initialPrompt: String = ""): PromptEditorCoordinator {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return PromptEditorCoordinator(
            clipboardGateway = TestClipboardGateway(),
            scope = scope,
            initialPrompt = initialPrompt
        )
    }

    @Test
    fun toggleSearch_activatesSearchMode_and_cancelsParagraphSelection() {
        val coordinator = createCoordinator("First line\nSecond line")
        coordinator.toggleParagraphSelectionMode()
        assertTrue(coordinator.editorUiState.value.isParagraphSelectionMode)

        coordinator.toggleSearch(true)
        val state = coordinator.editorUiState.value
        assertTrue(state.isSearchActive)
        assertFalse(state.isParagraphSelectionMode)
    }

    @Test
    fun setSearchQuery_findsMatches_ignoringCase() {
        val coordinator = createCoordinator("Apple banana APPLE orange apple")
        coordinator.toggleSearch(true)
        coordinator.setSearchQuery("apple")

        val state = coordinator.editorUiState.value
        assertEquals("apple", state.searchQuery)
        assertEquals(3, state.searchMatches.size)
        assertEquals(0, state.activeSearchMatchIndex)

        // 첫 번째 매치: 0..5
        assertEquals(0, state.searchMatches[0].start)
        assertEquals(5, state.searchMatches[0].endExclusive)
        // 두 번째 매치: 13..18 (APPLE)
        assertEquals(13, state.searchMatches[1].start)
        assertEquals(18, state.searchMatches[1].endExclusive)
        // 세 번째 매치: 26..31 (apple)
        assertEquals(26, state.searchMatches[2].start)
        assertEquals(31, state.searchMatches[2].endExclusive)
    }

    @Test
    fun navigateSearchNext_and_previous_cyclesIndex() {
        val coordinator = createCoordinator("test 1, test 2, test 3")
        coordinator.toggleSearch(true)
        coordinator.setSearchQuery("test")

        val initialMatches = coordinator.editorUiState.value.searchMatches
        assertEquals(3, initialMatches.size)
        assertEquals(0, coordinator.editorUiState.value.activeSearchMatchIndex)

        coordinator.navigateSearchNext()
        assertEquals(1, coordinator.editorUiState.value.activeSearchMatchIndex)

        coordinator.navigateSearchNext()
        assertEquals(2, coordinator.editorUiState.value.activeSearchMatchIndex)

        // 순환: 2 다음은 0
        coordinator.navigateSearchNext()
        assertEquals(0, coordinator.editorUiState.value.activeSearchMatchIndex)

        // 이전: 0 이전은 2
        coordinator.navigateSearchPrevious()
        assertEquals(2, coordinator.editorUiState.value.activeSearchMatchIndex)
    }

    @Test
    fun onPromptTemplateChange_recalculatesSearchMatches_whenSearchActive() {
        val coordinator = createCoordinator("cat and dog")
        coordinator.toggleSearch(true)
        coordinator.setSearchQuery("cat")

        assertEquals(1, coordinator.editorUiState.value.searchMatches.size)

        // 본문에 cat 추가 입력
        coordinator.onPromptTemplateChange("cat and dog and another cat")
        assertEquals(2, coordinator.editorUiState.value.searchMatches.size)
    }

    @Test
    fun closeSearch_resetsSearchState() {
        val coordinator = createCoordinator("hello world")
        coordinator.toggleSearch(true)
        coordinator.setSearchQuery("world")

        assertEquals(1, coordinator.editorUiState.value.searchMatches.size)

        coordinator.closeSearch()
        val state = coordinator.editorUiState.value
        assertFalse(state.isSearchActive)
        assertEquals("", state.searchQuery)
        assertTrue(state.searchMatches.isEmpty())
        assertEquals(-1, state.activeSearchMatchIndex)
    }

    @Test
    fun currentPromptText_updatesImmediatelyOnTyping_evenWhenBlanknessUnchanged() {
        val coordinator = createCoordinator("initial prompt")
        assertEquals("initial prompt", coordinator.currentPromptText.value)

        // 에디터에서 타이핑 발생 (updateTextFieldState = false)
        coordinator.onPromptTemplateFromEditor("initial prompt updated")

        // 1) PromptWorkspace 동기화용 실시간 Flow는 즉시 최신 타이핑 텍스트를 방출해야 함
        assertEquals("initial prompt updated", coordinator.currentPromptText.value)

        // 2) 반면 Compose 화면 전체 리컴포지션 방지 최적화로 인해 editorUiState.promptTemplate는 갱신되지 않고 유지됨
        assertEquals("initial prompt", coordinator.editorUiState.value.promptTemplate)
    }
}

