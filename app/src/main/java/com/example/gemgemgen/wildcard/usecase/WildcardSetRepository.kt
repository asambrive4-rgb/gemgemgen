// 역할: 앱 내 와일드카드 세트 데이터를 관리하는 저장소 인터페이스를 정의합니다.
package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardSet

interface WildcardSetRepository {
    fun load(): List<WildcardSet>

    fun load(tokens: Set<String>): List<WildcardSet> {
        if (tokens.isEmpty()) return emptyList()
        return load().filter { it.token in tokens }
    }
}

object NoOpWildcardSetRepository : WildcardSetRepository {
    override fun load(): List<WildcardSet> = emptyList()
}

