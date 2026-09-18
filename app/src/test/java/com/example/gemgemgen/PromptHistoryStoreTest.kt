// 역할: 프롬프트 실행 기록 저장 및 조회 리포지토리 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.usecase.PromptHistoryRepository
import com.example.gemgemgen.automation.usecase.PromptHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePromptHistoryRepository(
    initialItems: List<PromptHistoryItem> = emptyList()
) : PromptHistoryRepository {
    private var items: List<PromptHistoryItem> = initialItems

    override fun load(): List<PromptHistoryItem> = items

    override fun save(items: List<PromptHistoryItem>) {
        this.items = items
    }
}

class PromptHistoryStoreTest {

    @Test
    fun record_blankPrompt_isIgnored() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(repo)

        val result = store.record("   ", AutomationTargetApp.CHATGPT)

        assertTrue(result.isEmpty())
        assertTrue(repo.load().isEmpty())
    }

    @Test
    fun record_newPrompt_prependsToHistory() {
        val repo = FakePromptHistoryRepository()
        var time = 1000L
        val store = PromptHistoryStore(
            repository = repo,
            currentTimeMillisProvider = { time++ }
        )

        store.record("첫 번째 프롬프트", AutomationTargetApp.CHATGPT)
        val result = store.record("두 번째 프롬프트", AutomationTargetApp.GEMINI)

        assertEquals(2, result.size)
        assertEquals("두 번째 프롬프트", result[0].prompt)
        assertEquals(AutomationTargetApp.GEMINI, result[0].targetApp)
        assertEquals("첫 번째 프롬프트", result[1].prompt)
    }

    @Test
    fun record_duplicatePrompt_reordersToTopWithoutDuplicate() {
        val repo = FakePromptHistoryRepository()
        var time = 1000L
        val store = PromptHistoryStore(
            repository = repo,
            currentTimeMillisProvider = { time++ }
        )

        store.record("A", AutomationTargetApp.CHATGPT)
        store.record("B", AutomationTargetApp.GEMINI)
        store.record("C", AutomationTargetApp.GEMINI)

        // B를 다시 실행했을 때: 중복 생성 없이 B가 맨 위(0번 인덱스)로 올라와야 함 (B, C, A 순)
        val updated = store.record("B", AutomationTargetApp.CHATGPT)

        assertEquals(3, updated.size)
        assertEquals("B", updated[0].prompt)
        assertEquals(AutomationTargetApp.CHATGPT, updated[0].targetApp)
        assertEquals("C", updated[1].prompt)
        assertEquals("A", updated[2].prompt)
    }

    @Test
    fun record_exceedingMaxCount_dropsOldestItems() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(
            repository = repo,
            maxCount = 3
        )

        store.record("1", AutomationTargetApp.CHATGPT)
        store.record("2", AutomationTargetApp.CHATGPT)
        store.record("3", AutomationTargetApp.CHATGPT)
        val result = store.record("4", AutomationTargetApp.CHATGPT)

        assertEquals(3, result.size)
        assertEquals("4", result[0].prompt)
        assertEquals("3", result[1].prompt)
        assertEquals("2", result[2].prompt)
    }

    @Test
    fun record_defaultMaxCount_capsAtSix() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(repo)

        repeat(7) { index ->
            store.record("Prompt $index", AutomationTargetApp.CHATGPT)
        }

        val result = store.load()
        assertEquals(PromptHistoryStore.DEFAULT_MAX_HISTORY_COUNT, result.size)
        assertEquals(6, result.size)
        assertEquals("Prompt 6", result[0].prompt)
        assertEquals("Prompt 1", result[5].prompt)
    }

    @Test
    fun clear_removesAllItems() {
        val repo = FakePromptHistoryRepository()
        val store = PromptHistoryStore(repo)

        store.record("A", AutomationTargetApp.CHATGPT)
        store.record("B", AutomationTargetApp.CHATGPT)
        assertEquals(2, store.load().size)

        store.clear()
        assertTrue(store.load().isEmpty())
    }
}
