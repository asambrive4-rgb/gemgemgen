package com.example.gemgemgen

class PromptPreviewUseCase(
    private val loadWildcards: () -> List<WildcardSet>,
    private val promptGenerator: PromptGenerator = PromptGenerator()
) {
    fun generate(
        promptTemplate: String,
        repeatCountText: String
    ): PromptPreviewResult {
        return try {
            val wildcards = loadWildcards()
            val repeatCount = RepeatCountParser.parse(repeatCountText)
            val prompts = promptGenerator.generate(
                basePrompt = promptTemplate,
                wildcardSets = wildcards,
                repeatCount = repeatCount
            )

            PromptPreviewResult(
                generatedPrompts = prompts,
                message = "와일드카드 파일 ${wildcards.size}개로 ${prompts.size}개를 생성했습니다."
            )
        } catch (error: Exception) {
            PromptPreviewResult(
                generatedPrompts = emptyList(),
                error = "생성 실패: ${error.message ?: "와일드카드 폴더를 다시 선택해주세요."}"
            )
        }
    }
}

data class PromptPreviewResult(
    val generatedPrompts: List<GeneratedPrompt>,
    val message: String = "",
    val error: String = ""
)
