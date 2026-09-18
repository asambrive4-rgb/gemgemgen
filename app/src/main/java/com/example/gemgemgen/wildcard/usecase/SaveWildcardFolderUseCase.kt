// 역할: 사용자가 선택한 와일드카드 저장 폴더 경로를 검증하고 저장하는 유스케이스입니다.
package com.example.gemgemgen.wildcard.usecase

class SaveWildcardFolderUseCase(
    private val repository: WildcardFolderRepository
) : WildcardFolderRepository by repository
