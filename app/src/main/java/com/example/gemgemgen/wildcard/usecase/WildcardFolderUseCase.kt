// 역할: 사용자가 와일드카드 저장 폴더를 선택하고 경로를 검증하는 작업을 처리합니다.
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
