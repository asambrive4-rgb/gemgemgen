package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import java.util.UUID

interface PromptHistoryRepository {
    fun load(): List<PromptHistoryItem>
    fun save(items: List<PromptHistoryItem>)
}

class PromptHistoryStore(
    private val repository: PromptHistoryRepository,
    private val maxCount: Int = DEFAULT_MAX_HISTORY_COUNT,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    fun load(): List<PromptHistoryItem> {
        return repository.load()
    }

    fun record(
        prompt: String,
        targetApp: AutomationTargetApp
    ): List<PromptHistoryItem> {
        if (prompt.isBlank()) {
            return load()
        }

        val trimmed = prompt.trim()
        val existingItems = repository.load()

        // 방식 1: 기존에 동일한 프롬프트가 있으면 제거하고 최신으로 끌어올림
        val filtered = existingItems.filterNot { it.prompt.trim() == trimmed }

        val newItem = PromptHistoryItem(
            id = idGenerator(),
            prompt = prompt,
            targetApp = targetApp,
            createdAtMillis = currentTimeMillisProvider()
        )

        val updated = (listOf(newItem) + filtered).take(maxCount)
        repository.save(updated)
        return updated
    }

    fun clear() {
        repository.save(emptyList())
    }

    companion object {
        const val DEFAULT_MAX_HISTORY_COUNT = 10
    }
}
