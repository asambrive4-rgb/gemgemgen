// 역할: 과거 프롬프트 기록을 메모리에 캐싱하고 저장소와 동기화합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.domain.PromptHistoryItem
import com.example.gemgemgen.automation.domain.PromptHistoryNavigator
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
        targetApp: AutomationTargetApp
    ): List<PromptHistoryItem> {
        if (prompt.isBlank()) {
            return load()
        }

        val trimmed = prompt.trim()
        val existingItems = load()

        // 방식 1: 기존에 동일한 프롬프트가 있으면 제거하고 최신으로 끌어올림
        val filtered = existingItems.filterNot { it.prompt.trim() == trimmed }

        val newItem = PromptHistoryItem(
            id = idGenerator(),
            prompt = prompt,
            targetApp = targetApp,
            createdAtMillis = currentTimeMillisProvider()
        )

        val updated = (listOf(newItem) + filtered).take(maxCount)
        cachedItems = updated
        repository.save(updated)
        return updated
    }

    companion object {
        const val DEFAULT_MAX_HISTORY_COUNT = PromptHistoryNavigator.MAX_HISTORY_COUNT
    }
}
