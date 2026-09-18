// 역할: 와일드카드 폴더 URI 경로를 저장하고 조회하는 저장소 인터페이스입니다.
package com.example.gemgemgen.wildcard.usecase

sealed interface FolderSelectionResult {
    data object Success : FolderSelectionResult
    data class Failure(val reason: String? = null) : FolderSelectionResult
}

interface WildcardFolderRepository {
    fun save(folderUri: String): FolderSelectionResult
    fun getFolderUri(): String?
}
