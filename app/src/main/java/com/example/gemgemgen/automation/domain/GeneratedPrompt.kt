package com.example.gemgemgen.automation.domain

data class GeneratedPrompt(
    val index: Int,
    val basePrompt: String,
    val finalPrompt: String,
    val replacements: Map<String, String>
)
