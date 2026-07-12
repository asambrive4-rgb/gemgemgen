package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptGenerator
import com.example.gemgemgen.automation.domain.RepeatCountParser
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
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
    private val lastRunSnapshotStore: LastRunSnapshotStore,
    private val clipboardGateway: ClipboardGateway,
    private val wildcardSetRepository: WildcardSetRepository,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val promptGenerator: PromptGenerator = PromptGenerator()
) {
    suspend fun prepare(request: AutomationRunRequest): PreparedAutomationRun {
        val repeatCount = RepeatCountParser.parse(request.repeatCountText)
        val wildcardTokens = promptGenerator.extractTokens(request.promptTemplate).toSet()

        return withContext(dispatchers.io) {
            lastRunSnapshotStore.save(
                LastRunSnapshot(
                    promptTemplate = request.promptTemplate,
                    repeatCountText = request.repeatCountText,
                    targetApp = request.targetApp
                )
            )
            // 의도적 동작: 자동화 시작 시 원본 프롬프트 템플릿으로 시스템 클립보드를 덮어쓴다.
            // 전송은 Accessibility ACTION_SET_TEXT 경로를 쓰며, 클립보드 붙여넣기에 의존하지 않는다.
            // 실행 중/직후 수동 붙여넣기·백업 등 사용자 편의용으로 남겨 둔 것이며 제거하지 말 것.
            clipboardGateway.writeText(request.promptTemplate)

            val wildcards = if (wildcardTokens.isEmpty()) {
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
