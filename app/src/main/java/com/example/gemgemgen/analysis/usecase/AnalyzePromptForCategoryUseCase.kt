package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisCategory
import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.domain.AnalysisPromptBuilder
import com.example.gemgemgen.analysis.domain.AnalysisReport
import com.example.gemgemgen.analysis.domain.AnalysisResponseParser
import com.example.gemgemgen.core.AppDispatchers
import kotlinx.coroutines.withContext

class AnalysisException(message: String) : RuntimeException(message)

class AnalyzePromptForCategoryUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val credentialResolver: AnalysisCredentialResolver,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun analyze(
        sourcePrompt: String,
        category: AnalysisCategory,
        role: AnalysisModelRole = AnalysisModelRole.MASKING
    ): AnalysisReport = withContext(dispatchers.io) {
        if (sourcePrompt.isBlank()) {
            throw AnalysisException("원본 프롬프트를 입력해주세요.")
        }
        val credential = credentialResolver.resolveForRole(role)

        val payload = AnalysisPromptBuilder.buildAnalysisPrompt(
            sourcePrompt = sourcePrompt,
            category = category
        )
        val responseText = aiGateway.analyze(
            apiKey = credential.accessTokenOrApiKey,
            modelId = credential.modelId,
            payload = payload
        )
        AnalysisResponseParser.parseReport(responseText, sourcePrompt)
    }
}
