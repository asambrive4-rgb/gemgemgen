// 역할: 프롬프트 상용구(스니펫) 목록을 영구 저장소에 보관하고 불러오는 저장소 인터페이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptSnippet

interface PromptSnippetRepository {
    fun load(): List<PromptSnippet>
    fun save(snippets: List<PromptSnippet>)
}
