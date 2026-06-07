package com.example.gemgemgen

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
