package com.example.gemgemgen

data class WildcardSet(
    val token: String,
    val fileName: String,
    val items: List<String>
)

data class GeneratedPrompt(
    val index: Int,
    val basePrompt: String,
    val finalPrompt: String,
    val replacements: Map<String, String>
)
