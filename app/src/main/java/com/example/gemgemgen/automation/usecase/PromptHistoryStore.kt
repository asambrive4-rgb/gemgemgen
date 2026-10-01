// 역할: 과거 프롬프트 기록을 메모리에 캐싱하고 저장소와 동기화합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.domain.PromptHistoryNavigator

interface PromptHistoryRepository {
    fun load(): List<PromptHistoryItem>
    fun save(items: List<PromptHistoryItem>)
}

class PromptHistoryStore(
    private val repository: PromptHistoryRepository,
    private val maxCount: Int = DEFAULT_MAX_HISTORY_COUNT
) {
    @Volatile
    private var cachedItems: List<PromptHistoryItem>? = null

    @Synchronized
    fun load(): List<PromptHistoryItem> {
        cachedItems?.let { return it }
        return repository.load().also { cachedItems = it }
    }

    @Synchronized
    fun record(
        prompt: String,
        targetApp: AutomationTargetApp? = null
    ): List<PromptHistoryItem> {
        if (prompt.isBlank()) {
            return load()
        }

        val existingItems = load()
        val updatedStrings = PromptHistoryNavigator.prependPrompt(
            executedPrompt = prompt,
            history = existingItems.map { it.prompt }
        ).take(maxCount)

        val updated = updatedStrings.map { PromptHistoryItem(it) }
        cachedItems = updated
        repository.save(updated)
        return updated
    }

    companion object {
        const val DEFAULT_MAX_HISTORY_COUNT = PromptHistoryNavigator.MAX_HISTORY_COUNT
    }
}
