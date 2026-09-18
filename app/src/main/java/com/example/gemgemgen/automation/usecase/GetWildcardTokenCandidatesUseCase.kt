// 역할: 와일드카드 파일 저장소(WildcardFileRepository)에서 파일 목록을 조회하여 자동완성 토큰 추천 후보 목록을 도출하는 유스케이스.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository

class GetWildcardTokenCandidatesUseCase(
    private val wildcardFileRepository: WildcardFileRepository? = null
) {
    operator fun invoke(): List<WildcardTokenAutocomplete.Candidate> =
        try {
            val files = wildcardFileRepository?.listFiles().orEmpty()
            val fileNames = files.map { it.fileName }
            WildcardTokenAutocomplete.candidatesFromFileNames(fileNames)
        } catch (_: Exception) {
            emptyList()
        }
}
