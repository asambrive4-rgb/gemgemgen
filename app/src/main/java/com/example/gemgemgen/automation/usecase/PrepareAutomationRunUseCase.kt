// 역할: 자동화 실행 전 반복 횟수를 계산하고 와일드카드 세트를 로드하여 최종 실행 계획을 준비합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.wildcard.usecase.WildcardSetRepository
import kotlinx.coroutines.withContext

data class PreparedAutomationRun(
    val request: AutomationRunRequest,
    val repeatCount: Int,
    val wildcards: List<WildcardSet>,
    val promptPlan: PromptGenerator.CompiledPrompt
)

class PrepareAutomationRunUseCase(
    @Suppress("UNUSED_PARAMETER") automationHistoryRecorder: AutomationHistoryRecorder? = null,
    private val wildcardSetRepository: WildcardSetRepository,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val promptGenerator: PromptGenerator = PromptGenerator()
) {
    suspend fun prepare(request: AutomationRunRequest): PreparedAutomationRun {
        val repeatCount = RepeatCountParser.parse(request.repeatCountText)
        val wildcardTokens = promptGenerator.extractTokens(request.promptTemplate).toSet()

        return withContext(dispatchers.io) {
            val wildcards = if (request.initialWildcards != null) {
                request.initialWildcards
            } else if (wildcardTokens.isEmpty()) {
                emptyList()
            } else {
                wildcardSetRepository.load(wildcardTokens)
            }

            PreparedAutomationRun(
                request = request,
                repeatCount = repeatCount,
                wildcards = wildcards,
                promptPlan = promptGenerator.compile(request.promptTemplate, wildcards)
            )
        }
    }
}
