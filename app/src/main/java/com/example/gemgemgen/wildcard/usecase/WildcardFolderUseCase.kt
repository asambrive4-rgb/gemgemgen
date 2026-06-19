package com.example.gemgemgen.wildcard.usecase

data class FolderSelectionResult(
    val message: String = "",
    val error: String = ""
)

interface WildcardFolderRepository {
    fun save(folderUri: String): FolderSelectionResult
}

class SaveWildcardFolderUseCase(
    private val repository: WildcardFolderRepository
) {
    fun save(folderUri: String): FolderSelectionResult {
        return repository.save(folderUri)
    }
}
