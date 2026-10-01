// 역할: 와일드카드 텍스트 파일 파싱, 토큰 생성 및 토큰 정규식 규칙을 제공합니다.
package com.example.gemgemgen.wildcard.domain

object WildcardFileParser {
    val TOKEN_REGEX = Regex("__[^\\s]+?__")
    val COMPLETE_TOKEN_REGEX = Regex("^__[^\\s]+__$")

    fun tokenFromFileName(fileName: String): String? {
        if (!fileName.endsWith(".txt", ignoreCase = true)) return null

        val tokenName = fileName.dropLast(4).filterNot { it.isWhitespace() }
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

    fun hasAnyItem(text: String): Boolean {
        return text.lineSequence().any { it.isNotBlank() }
    }
}
