package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.wildcard.domain.WildcardSet

interface WildcardSetRepository {
    fun load(): List<WildcardSet>
}
