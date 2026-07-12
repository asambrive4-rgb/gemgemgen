package com.example.gemgemgen

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisProvider
import com.example.gemgemgen.analysis.domain.DEFAULT_ANALYSIS_MODEL
import com.example.gemgemgen.analysis.domain.MODEL_GEMINI_3_1_FLASH_LITE
import com.example.gemgemgen.analysis.domain.MODEL_GROK_4_5
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRecord
import com.example.gemgemgen.analysis.usecase.GeminiApiKeyRepository
import com.example.gemgemgen.analysis.usecase.ManageGeminiApiKeysUseCase
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisModelMemoryTest {
    @Test
    fun roleSettings_areIndependent() = runBlocking {
        val repository = MemorySettingsRepository()
        val manager = ManageGeminiApiKeysUseCase(
            repository = repository,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )

        manager.setRoleProvider(AnalysisModelRole.MASKING, AnalysisProvider.GEMINI)
        manager.setRoleModel(AnalysisModelRole.MASKING, MODEL_GEMINI_3_1_FLASH_LITE)
        manager.setRoleProvider(AnalysisModelRole.GENERATION, AnalysisProvider.GROK)
        manager.setRoleModel(AnalysisModelRole.GENERATION, MODEL_GROK_4_5)

        val masking = manager.getRoleSetting(AnalysisModelRole.MASKING)
        val generation = manager.getRoleSetting(AnalysisModelRole.GENERATION)

        assertEquals(AnalysisProvider.GEMINI, masking.provider)
        assertEquals(MODEL_GEMINI_3_1_FLASH_LITE, masking.modelId)
        assertEquals(AnalysisProvider.GROK, generation.provider)
        assertEquals(MODEL_GROK_4_5, generation.modelId)
    }

    @Test
    fun rememberLastUsed_updatesOnlyThatRole() = runBlocking {
        val repository = MemorySettingsRepository()
        val manager = ManageGeminiApiKeysUseCase(
            repository = repository,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined)
        )

        manager.rememberLastUsed(
            role = AnalysisModelRole.GENERATION,
            provider = AnalysisProvider.GROK,
            modelId = MODEL_GROK_4_5
        )

        val generation = manager.getRoleSetting(AnalysisModelRole.GENERATION)
        val masking = manager.getRoleSetting(AnalysisModelRole.MASKING)
        assertEquals(AnalysisProvider.GROK, generation.provider)
        assertEquals(MODEL_GROK_4_5, generation.modelId)
        assertEquals(AnalysisProvider.GEMINI, masking.provider)
    }

    private class MemorySettingsRepository : GeminiApiKeyRepository {
        private val providers = mutableMapOf(
            "masking" to "gemini",
            "generation" to "gemini"
        )
        private val models = mutableMapOf(
            "masking" to DEFAULT_ANALYSIS_MODEL,
            "generation" to DEFAULT_ANALYSIS_MODEL
        )

        override fun listKeys(): List<GeminiApiKeyRecord> = emptyList()
        override fun addKey(label: String, rawKey: String, createdAtMillis: Long) = error("unused")
        override fun deleteKey(id: String) = Unit
        override fun activateKey(id: String) = Unit
        override fun activeKeyValue(): String? = null
        override fun updateKeyLabel(id: String, newLabel: String) = Unit
        override fun getRoleProvider(role: String): String = providers[role] ?: "gemini"
        override fun setRoleProvider(role: String, providerId: String) {
            providers[role] = providerId
        }
        override fun getRoleModel(role: String): String =
            models[role] ?: DEFAULT_ANALYSIS_MODEL
        override fun setRoleModel(role: String, modelId: String) {
            models[role] = modelId
        }
    }
}
