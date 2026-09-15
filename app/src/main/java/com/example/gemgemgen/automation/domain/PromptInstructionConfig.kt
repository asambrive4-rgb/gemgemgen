// 역할: 프롬프트 상단 및 하단에 삽입할 시스템 인스트럭션 설정 데이터와 텍스트 결합 규칙을 제공합니다.
package com.example.gemgemgen.automation.domain

enum class InstructionTab {
    TOP,
    BOTTOM
}

data class PromptInstructionConfig(
    val topInstruction: String = DEFAULT_TOP_INSTRUCTION,
    val bottomInstruction: String? = null
) {
    /**
     * 프롬프트 맨 앞에 상단 인스트럭션을 붙입니다.
     * 본문이 비어 있으면 상단 문구만 반환하고, 본문이 있으면 '\n\n'으로 구분합니다.
     */
    fun prependTopTo(currentPrompt: String): String {
        if (topInstruction.isBlank()) return currentPrompt
        if (currentPrompt.isEmpty()) return topInstruction
        return topInstruction + "\n\n" + currentPrompt
    }

    /**
     * 프롬프트 맨 뒤에 하단 인스트럭션을 붙입니다.
     * 본문이 비어 있으면 하단 문구만 반환하고, 본문이 있으면 '\n\n'으로 구분합니다.
     */
    fun appendBottomTo(currentPrompt: String): String {
        val bottom = bottomInstruction?.takeIf { it.isNotBlank() } ?: return currentPrompt
        if (currentPrompt.isEmpty()) return bottom
        return currentPrompt + "\n\n" + bottom
    }

    companion object {
        val DEFAULT_TOP_INSTRUCTION: String get() = SystemInstructionPrompt.text
        val DEFAULT = PromptInstructionConfig(
            topInstruction = SystemInstructionPrompt.text,
            bottomInstruction = null
        )
    }
}
