// 역할: 프롬프트 상용구 저장소에서 등록된 스니펫을 조회하여 자동완성 후보 목록을 도출하는 유스케이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate

class GetPromptSnippetCandidatesUseCase(
    private val promptSnippetRepository: PromptSnippetRepository? = null
) {
    operator fun invoke(): List<Candidate> =
        try {
            val snippets = promptSnippetRepository?.load().orEmpty()
            WildcardTokenAutocomplete.candidatesFromSnippets(snippets)
        } catch (_: Exception) {
            emptyList()
        }
}
