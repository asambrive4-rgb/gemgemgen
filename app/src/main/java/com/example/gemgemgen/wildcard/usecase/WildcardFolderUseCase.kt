package com.example.gemgemgen.wildcard.usecase

sealed interface FolderSelectionResult {
    data object Success : FolderSelectionResult
    data class Failure(val reason: String? = null) : FolderSelectionResult
}

interface WildcardFolderRepository {
    fun save(folderUri: String): FolderSelectionResult
    fun getFolderUri(): String?
}

class SaveWildcardFolderUseCase(
    private val repository: WildcardFolderRepository
) {
    fun save(folderUri: String): FolderSelectionResult {
        return repository.save(folderUri)
    }

    fun getFolderUri(): String? {
        return repository.getFolderUri()
    }
}
