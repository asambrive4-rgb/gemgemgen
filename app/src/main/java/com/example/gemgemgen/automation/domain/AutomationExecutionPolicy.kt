// 역할: 자동화 실행, 변주, 앱 제어 및 메모리 정리 등 전반적인 실행 인가 정책을 총괄하는 도메인 정책입니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

enum class GeminiAppControlBlockReason {
    AutomationRunning,
    AlreadyInProgress,
    GeminiNotInstalled,
    AccessibilityDisabled
}

enum class SelfAppControlBlockReason {
    AutomationRunning,
    AlreadyInProgress,
    AccessibilityDisabled
}

/**
 * 자동화 화면의 모든 실행 인가 및 권한 정책을 집약한 도메인 정책 객체입니다.
 * UI State의 비즈니스 불변식 판정 책임을 온전히 도메인 레이어로 승격합니다.
 */
object AutomationExecutionPolicy {

    fun hasPromptTemplate(promptTemplate: String): Boolean =
        promptTemplate.isNotBlank()

    /**
     * 메인 자동화 실행 가능 여부를 판정합니다.
     */
    fun canRun(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String,
        isRunning: Boolean,
        remoteAutomationStatus: RemoteAutomationStatus,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false
    ): Boolean = when (mode) {
        AutomationMode.NORMAL ->
            environmentStatus.isReadyFor(targetApp) &&
                hasPromptTemplate(promptTemplate) &&
                !isRunning &&
                !isVariationRunning &&
                !isMaintenanceBusy
        AutomationMode.SENDER ->
            hasPromptTemplate(promptTemplate) &&
                remoteAutomationStatus.canSend &&
                !isRunning &&
                !isVariationRunning &&
                !isMaintenanceBusy
        AutomationMode.RECEIVER -> false
    }

    /**
     * 변주(Variation) 자동화 실행 가능 여부를 판정합니다.
     */
    fun canRunVariation(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean =
        mode != AutomationMode.RECEIVER &&
            !isRunning &&
            !isVariationRunning &&
            !isMaintenanceBusy &&
            environmentStatus.isGeminiInstalled &&
            environmentStatus.isAccessibilityServiceEnabled

    /**
     * 변주(Variation) 버튼/인터랙션 활성화 가능 여부를 판정합니다.
     */
    fun canInteractWithVariation(
        mode: AutomationMode,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean =
        mode != AutomationMode.RECEIVER &&
            !isRunning &&
            !isMaintenanceBusy &&
            !isVariationRunning

    /**
     * 변주(Variation) 실행 불가 사유를 반환합니다. 실행 가능한 경우 null을 반환합니다.
     */
    fun variationUnavailableReason(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): String? = when {
        mode == AutomationMode.RECEIVER -> "수신 모드에서는 변주를 실행할 수 없습니다."
        isRunning -> "자동화 실행 중에는 변주를 실행할 수 없습니다."
        isVariationRunning -> "변주 자동화가 이미 실행 중입니다."
        isMaintenanceBusy -> "유지보수 작업이 진행 중입니다."
        !environmentStatus.isGeminiInstalled -> "Gemini 앱을 먼저 설치해주세요."
        !environmentStatus.isAccessibilityServiceEnabled -> "접근성 서비스를 먼저 켜주세요."
        else -> null
    }

    /**
     * Gemini 앱 재시작/종료 허용 여부를 판정합니다.
     */
    fun canCloseGemini(
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): Boolean = geminiBlockReason(
        isGeminiInstalled = isGeminiInstalled,
        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
        isAutomationRunning = isAutomationRunning,
        isClosingInProgress = isClosingInProgress
    ) == null

    fun geminiBlockReason(
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): GeminiAppControlBlockReason? = when {
        isAutomationRunning -> GeminiAppControlBlockReason.AutomationRunning
        isClosingInProgress -> GeminiAppControlBlockReason.AlreadyInProgress
        !isGeminiInstalled -> GeminiAppControlBlockReason.GeminiNotInstalled
        !isAccessibilityServiceEnabled -> GeminiAppControlBlockReason.AccessibilityDisabled
        else -> null
    }

    /**
     * 자기 앱(GemGemGen) 종료 허용 여부를 판정합니다.
     */
    fun canCloseSelfApp(
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): Boolean = selfAppBlockReason(
        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
        isAutomationRunning = isAutomationRunning,
        isClosingInProgress = isClosingInProgress
    ) == null

    fun selfAppBlockReason(
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): SelfAppControlBlockReason? = when {
        isAutomationRunning -> SelfAppControlBlockReason.AutomationRunning
        isClosingInProgress -> SelfAppControlBlockReason.AlreadyInProgress
        !isAccessibilityServiceEnabled -> SelfAppControlBlockReason.AccessibilityDisabled
        else -> null
    }

    /**
     * 디바이스 메모리 정리 버튼 활성화(실행 또는 예약) 가능 여부를 판정합니다.
     * 일반/송신 모드에서 접근성(또는 원격 전송 가능) 조건이 충족되고, 유지보수 작업이 비진행 상태일 때 허용됩니다.
     */
    fun canCleanMemory(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        remoteAutomationStatus: RemoteAutomationStatus,
        isMaintenanceBusy: Boolean
    ): Boolean = when (mode) {
        AutomationMode.SENDER -> remoteAutomationStatus.canSend && !isMaintenanceBusy
        AutomationMode.RECEIVER -> false
        AutomationMode.NORMAL -> environmentStatus.isAccessibilityServiceEnabled && !isMaintenanceBusy
    }
}

