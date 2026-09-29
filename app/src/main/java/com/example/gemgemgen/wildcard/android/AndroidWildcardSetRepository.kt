// 역할: 와일드카드 파일 저장소를 통해 와일드카드 세트 목록을 로드하고 파싱합니다.
package com.example.gemgemgen.wildcard.android

import android.content.Context
import com.example.gemgemgen.wildcard.domain.WildcardFileParser
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository

class AndroidWildcardSetRepository(
    private val fileRepository: WildcardFileRepository
) : WildcardSetRepository {
    constructor(context: Context) : this(AndroidWildcardFileRepository(context))

    override fun load(): List<WildcardSet> = loadSets(tokens = null)

    override fun load(tokens: Set<String>): List<WildcardSet> =
        if (tokens.isEmpty()) emptyList() else loadSets(tokens)

    private fun loadSets(tokens: Set<String>?): List<WildcardSet> {
        return runCatching { fileRepository.listFiles() }
            .getOrDefault(emptyList())
            .mapNotNull { file ->
                val token = WildcardFileParser.tokenFromFileName(file.fileName)
                    ?: return@mapNotNull null
                if (tokens != null && token !in tokens) return@mapNotNull null
                val text = runCatching { fileRepository.readFile(file) }.getOrDefault("")
                WildcardSet(
                    token = token,
                    fileName = file.fileName,
                    items = WildcardFileParser.parseItems(text)
                )
            }
    }
}
