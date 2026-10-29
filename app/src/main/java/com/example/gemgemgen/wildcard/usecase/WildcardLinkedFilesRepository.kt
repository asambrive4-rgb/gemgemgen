// 역할: 상용구 자동완성 칩과 연동할 와일드카드 파일 목록을 영구 저장·조회하고 실시간 변경 Flow를 제공하는 저장소 인터페이스입니다.
package com.example.gemgemgen.wildcard.usecase

import kotlinx.coroutines.flow.Flow

interface WildcardLinkedFilesRepository {
    fun getLinkedFileNames(): Set<String>
    suspend fun saveLinkedFileNames(fileNames: Set<String>)
    fun observeLinkedFileNames(): Flow<Set<String>>
}
