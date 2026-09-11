// 역할: 자동화 실행 전 대상 앱 상태와 생성된 프롬프트 목록을 준비하고 검증합니다.
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

class AutomationRunPreparer(
    private val automationStartRecorder: AutomationStartRecorder,
    private val wildcardSetRepository: WildcardSetRepository,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val promptGenerator: PromptGenerator = PromptGenerator()
) {
    suspend fun prepare(request: AutomationRunRequest): PreparedAutomationRun {
        val repeatCount = RepeatCountParser.parse(request.repeatCountText)
        val wildcardTokens = promptGenerator.extractTokens(request.promptTemplate).toSet()
        automationStartRecorder.record(request)

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
