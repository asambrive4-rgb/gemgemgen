package com.example.gemgemgen.wildcard.domain

data class WildcardSet(
    val token: String,
    val fileName: String,
    val items: List<String>
)

data class WildcardTextFile(
    val id: String,
    val fileName: String,
    val documentUri: String
)

object WildcardFileName {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        return if (trimmed.endsWith(".txt", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.txt"
        }
    }
}

class WildcardFileException(message: String) : RuntimeException(message)
