package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardClassifyFileName
import com.example.gemgemgen.wildcard.domain.WildcardClassifySaveEntry
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import kotlinx.coroutines.withContext

sealed class WildcardClassifySaveResult {
    data class Success(val savedFileNames: List<String>) : WildcardClassifySaveResult()
    data class FileExists(val conflictingFileNames: List<String>) : WildcardClassifySaveResult()
    data object NothingToSave : WildcardClassifySaveResult()
    data class InvalidFileName(val groupName: String) : WildcardClassifySaveResult()
}

class SaveWildcardClassifyResultUseCase(
    private val repository: WildcardFileRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun save(
        entries: List<WildcardClassifySaveEntry>,
        overwrite: Boolean
    ): WildcardClassifySaveResult = withContext(dispatchers.io) {
        val plans = planSaves(entries)
        if (plans.isEmpty()) {
            return@withContext WildcardClassifySaveResult.NothingToSave
        }
        val invalid = plans.firstOrNull { it.fileName == null }
        if (invalid != null) {
            return@withContext WildcardClassifySaveResult.InvalidFileName(invalid.groupName)
        }

        val existingNames = repository.listFiles().map { it.fileName }.toSet()
        val conflicts = plans.mapNotNull { plan ->
            val fileName = plan.fileName ?: return@mapNotNull null
            if (existingNames.any { it.equals(fileName, ignoreCase = true) }) fileName else null
        }.distinct()

        if (conflicts.isNotEmpty() && !overwrite) {
            return@withContext WildcardClassifySaveResult.FileExists(conflicts)
        }

        val saved = ArrayList<String>()
        for (plan in plans) {
            val fileName = checkNotNull(plan.fileName)
            val existing = repository.listFiles()
                .firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
            val target = existing ?: repository.createFile(fileName)
            try {
                repository.writeFile(target, plan.items.joinToString(separator = "\n"))
            } catch (error: WildcardFileException) {
                throw error
            }
            saved.add(fileName)
        }
        WildcardClassifySaveResult.Success(saved)
    }

    private data class SavePlan(
        val groupName: String,
        val fileName: String?,
        val items: List<String>
    )

    private fun planSaves(entries: List<WildcardClassifySaveEntry>): List<SavePlan> {
        val usedNames = linkedSetOf<String>()
        return entries.mapNotNull { entry ->
            if (entry.items.isEmpty()) return@mapNotNull null
            val normalized = WildcardClassifyFileName.normalizeUserInput(entry.fileNameInput)
                ?: return@mapNotNull SavePlan(entry.groupName, null, entry.items)

            var candidate = normalized
            var suffix = 2
            while (!usedNames.add(candidate.lowercase())) {
                val stem = normalized.removeSuffix(".txt")
                candidate = "${stem}_$suffix.txt"
                suffix++
            }
            SavePlan(entry.groupName, candidate, entry.items)
        }
    }
}
