package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunLog

class RunLogger(
    private val repository: RunLogRepository,
    private val maxEntries: Int = MAX_ENTRIES
) {
    fun append(log: AutomationRunLog) {
        val logs = (listOf(log) + loadRecent()).take(maxEntries)
        repository.save(logs)
    }

    fun loadRecent(): List<AutomationRunLog> {
        return repository.load()
    }

    companion object {
        private const val MAX_ENTRIES = 10
    }
}

interface RunLogRepository {
    fun load(): List<AutomationRunLog>
    fun save(logs: List<AutomationRunLog>)
}

