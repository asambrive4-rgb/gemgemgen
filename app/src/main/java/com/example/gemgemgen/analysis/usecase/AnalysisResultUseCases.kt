package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardFileName
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import kotlinx.coroutines.withContext

class CopyAnalysisResultsUseCase(
    private val clipboardGateway: ClipboardGateway,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun copy(candidates: List<String>) = withContext(dispatchers.io) {
        clipboardGateway.writeText(candidates.joinToString(separator = "\n"))
    }

    suspend fun copyText(text: String) = withContext(dispatchers.io) {
        clipboardGateway.writeText(text)
    }
}

sealed class AnalysisWildcardSaveResult {
    data class Success(val fileName: String) : AnalysisWildcardSaveResult()
    data class FileExists(val fileName: String) : AnalysisWildcardSaveResult()
    data object InvalidFileName : AnalysisWildcardSaveResult()
}

class SaveAnalysisWildcardFileUseCase(
    private val repository: WildcardFileRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun save(
        fileNameInput: String,
        candidates: List<String>,
        overwrite: Boolean
    ): AnalysisWildcardSaveResult = withContext(dispatchers.io) {
        val fileName = WildcardFileName.normalize(fileNameInput)
            ?: return@withContext AnalysisWildcardSaveResult.InvalidFileName
        val existingFile = repository.listFiles()
            .firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }

        if (existingFile != null && !overwrite) {
            return@withContext AnalysisWildcardSaveResult.FileExists(fileName)
        }

        val targetFile = existingFile ?: repository.createFile(fileName)
        try {
            repository.writeFile(targetFile, candidates.joinToString(separator = "\n"))
        } catch (error: WildcardFileException) {
            throw error
        }
        AnalysisWildcardSaveResult.Success(fileName)
    }
}
