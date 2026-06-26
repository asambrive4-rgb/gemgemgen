package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardSet

interface WildcardSetRepository {
    fun load(): List<WildcardSet>

    fun load(tokens: Set<String>): List<WildcardSet> {
        if (tokens.isEmpty()) return emptyList()
        return load().filter { it.token in tokens }
    }
}
