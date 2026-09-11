// 역할: 외부 AI 앱별 프롬프트 자동 입력 게이트웨이 및 전용 설정 인터페이스를 정의합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationRunState

enum class NewChatMode {
    Initial,
    Subsequent
}

interface PromptAutomationGateway {
    fun sendPrompt(
        prompt: String,
        newChatMode: NewChatMode,
        onStateChange: (AutomationRunState) -> Unit,
        onDone: () -> Unit
    )

    fun cancelCurrentRun()
}

/**
 * Flow 앱 전용 이미지 생성 개수(1~4장) 등 상세 옵션 설정을 지원하는 게이트웨이 인터페이스.
 */
interface FlowConfigurableGateway {
    fun setFlowImageCount(count: Int)
}