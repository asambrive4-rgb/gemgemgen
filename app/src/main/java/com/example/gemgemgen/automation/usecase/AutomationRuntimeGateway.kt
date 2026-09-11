// 역할: 기기 화면에서 터치 및 입력 자동화를 수행하는 런타임 제어 인터페이스를 정의합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.AutomationTargetApp

fun interface PromptAutomationGatewayProvider {
    fun current(targetApp: AutomationTargetApp): PromptAutomationGateway?
}

fun interface TargetAppLauncher {
    fun launch(targetApp: AutomationTargetApp): Boolean
}
