// 역할: 사용자가 선택한 와일드카드 저장 폴더 경로를 검증하고 저장소에 반영하는 유스케이스입니다.
package com.example.gemgemgen.wildcard.usecase

class SaveWildcardFolderUseCase(
    private val repository: WildcardFolderRepository
) : WildcardFolderRepository by repository {

    override fun save(folderUri: String): FolderSelectionResult {
        val trimmedUri = folderUri.trim()
        if (trimmedUri.isBlank()) {
            return FolderSelectionResult.Failure("폴더 경로가 비어 있습니다.")
        }
        return repository.save(trimmedUri)
    }

    operator fun invoke(folderUri: String): FolderSelectionResult = save(folderUri)
}

