package com.example.gemgemgen.analysis.domain

enum class AnalysisProvider(val storageValue: String) {
    GEMINI("gemini"),
    GROK("grok");

    companion object {
        fun fromStorage(value: String?): AnalysisProvider {
            return entries.firstOrNull { it.storageValue == value } ?: GEMINI
        }

        fun defaultModel(provider: AnalysisProvider): String {
            return when (provider) {
                GEMINI -> DEFAULT_ANALYSIS_MODEL
                GROK -> MODEL_GROK_4_5
            }
        }

        fun isModelForProvider(modelId: String, provider: AnalysisProvider): Boolean {
            return when (provider) {
                GEMINI -> modelId.startsWith("gemini-")
                GROK -> modelId.startsWith("grok-")
            }
        }
    }
}
