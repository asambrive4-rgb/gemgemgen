// 역할: 와일드카드 파일 저장소를 통해 와일드카드 세트 목록을 로드하고 파싱합니다.
package com.example.gemgemgen.wildcard.android

import android.content.Context
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import com.example.gemgemgen.wildcard.usecase.loadWildcardSets

class AndroidWildcardSetRepository(
    private val fileRepository: WildcardFileRepository
) : WildcardSetRepository {
    constructor(context: Context) : this(AndroidWildcardFileRepository(context))

    override fun load(): List<WildcardSet> = fileRepository.loadWildcardSets()

    override fun load(tokens: Set<String>): List<WildcardSet> =
        if (tokens.isEmpty()) emptyList() else fileRepository.loadWildcardSets(tokens)
}
