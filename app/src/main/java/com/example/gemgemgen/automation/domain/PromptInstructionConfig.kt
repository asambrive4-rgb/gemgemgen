// 역할: 프롬프트 상단 및 하단에 삽입할 시스템 인스트럭션 설정 데이터와 기본값을 정의합니다.
package com.example.gemgemgen.automation.domain

enum class InstructionTab {
    TOP,
    BOTTOM
}

data class PromptInstructionConfig(
    val topInstruction: String = DEFAULT_TOP_INSTRUCTION,
    val bottomInstruction: String? = null
) {
    companion object {
        val DEFAULT_TOP_INSTRUCTION: String get() = SystemInstructionPrompt.text
        val DEFAULT = PromptInstructionConfig(
            topInstruction = SystemInstructionPrompt.text,
            bottomInstruction = null
        )
    }
}
