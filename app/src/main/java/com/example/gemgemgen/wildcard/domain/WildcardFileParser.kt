// 역할: 와일드카드 텍스트 파일에서 주석과 빈 줄을 제외하고 유효한 키워드 목록만 추출합니다.
package com.example.gemgemgen.wildcard.domain

object WildcardFileParser {
    fun tokenFromFileName(fileName: String): String? {
        if (!fileName.endsWith(".txt", ignoreCase = true)) return null

        val tokenName = fileName.dropLast(4).trim()
        if (tokenName.isBlank()) return null

        return "__${tokenName}__"
    }

    fun parseItems(text: String): List<String> {
        return text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}

