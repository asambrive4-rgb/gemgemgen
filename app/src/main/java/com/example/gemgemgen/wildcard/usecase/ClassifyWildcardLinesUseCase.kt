package com.example.gemgemgen.wildcard.usecase

import com.example.gemgemgen.analysis.domain.AnalysisModelRole
import com.example.gemgemgen.analysis.usecase.AnalysisAiGateway
import com.example.gemgemgen.analysis.usecase.AnalysisCredentialResolver
import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardClassifyPromptBuilder
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResult
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResultPolicy
import com.example.gemgemgen.wildcard.domain.WildcardClassifyResponseParser
import com.example.gemgemgen.wildcard.domain.WildcardDynamicPromptComposer
import kotlinx.coroutines.withContext

class ClassifyWildcardLinesUseCase(
    private val aiGateway: AnalysisAiGateway,
    private val credentialResolver: AnalysisCredentialResolver,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun classify(
        editingText: String,
        criteria: String
    ): WildcardClassifyResult = withContext(dispatchers.io) {
        val trimmedCriteria = criteria.trim()
        if (trimmedCriteria.isEmpty()) {
            throw AnalysisException("분류 기준을 입력해주세요.")
        }

        val sourceLines = WildcardDynamicPromptComposer.selectableLines(editingText)
        if (sourceLines.isEmpty()) {
            throw AnalysisException("분류할 줄이 없습니다.")
        }

        val credential = credentialResolver.resolveForRole(AnalysisModelRole.GENERATION)
        val payload = WildcardClassifyPromptBuilder.build(
            criteria = trimmedCriteria,
            lines = sourceLines
        )
        val responseText = aiGateway.generateTxt(
            apiKey = credential.accessTokenOrApiKey,
            modelId = credential.modelId,
            payload = payload
        )
        val rawGroups = try {
            WildcardClassifyResponseParser.parseGroups(responseText)
        } catch (error: RuntimeException) {
            throw AnalysisException(error.message ?: "분류 결과를 해석하지 못했습니다.")
        }

        val (groups, droppedLines) = WildcardClassifyResultPolicy.reconcile(
            sourceLines = sourceLines,
            rawGroups = rawGroups
        )
        if (groups.isEmpty()) {
            throw AnalysisException("분류 결과가 비어 있습니다. 기준을 바꿔 다시 시도해주세요.")
        }

        WildcardClassifyResult(
            criteria = trimmedCriteria,
            sourceLines = sourceLines,
            groups = groups,
            droppedLines = droppedLines
        )
    }
}
