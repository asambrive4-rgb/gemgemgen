// 역할: 자동화 실행, 변주, 앱 제어 및 메모리 정리 등 전반적인 실행 인가 정책을 총괄하는 도메인 정책입니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus

/**
 * 자동화 화면의 모든 실행 인가 및 권한 정책을 집약한 도메인 정책 객체입니다.
 * UI State의 비즈니스 불변식 판정 책임을 온전히 도메인 레이어로 승격합니다.
 */
object AutomationExecutionPolicy {

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
    ): Boolean = AutomationStartPolicy.canRun(
        mode = mode,
        environmentStatus = environmentStatus,
        targetApp = targetApp,
        promptTemplate = promptTemplate,
        isRunning = isRunning,
        remoteAutomationStatus = remoteAutomationStatus,
        isVariationRunning = isVariationRunning,
        isMaintenanceBusy = isMaintenanceBusy
    )

    /**
     * 메인 자동화 필수 요구사항(환경 및 프롬프트 템플릿) 충족 여부를 판정합니다.
     */
    fun hasRunRequirements(
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String
    ): Boolean = AutomationStartPolicy.hasRunRequirements(
        environmentStatus = environmentStatus,
        targetApp = targetApp,
        promptTemplate = promptTemplate
    )

    /**
     * 변주(Variation) 자동화 실행 가능 여부를 판정합니다.
     */
    fun canRunVariation(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean = VariationStartPolicy.canRun(
        mode = mode,
        environmentStatus = environmentStatus,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isVariationRunning = isVariationRunning
    )

    /**
     * 변주(Variation) 버튼/인터랙션 활성화 가능 여부를 판정합니다.
     */
    fun canInteractWithVariation(
        mode: AutomationMode,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean = VariationStartPolicy.canInteract(
        mode = mode,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isVariationRunning = isVariationRunning
    )

    /**
     * 변주(Variation) 실행 불가 사유를 반환합니다. 실행 가능한 경우 null을 반환합니다.
     */
    fun variationUnavailableReason(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): String? = VariationStartPolicy.unavailableReason(
        mode = mode,
        environmentStatus = environmentStatus,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isVariationRunning = isVariationRunning
    )

    /**
     * Gemini 앱 재시작/종료 허용 여부를 판정합니다.
     */
    fun canCloseGemini(
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): Boolean = GeminiAppControlPolicy.canClose(
        isGeminiInstalled = isGeminiInstalled,
        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
        isAutomationRunning = isAutomationRunning,
        isClosingInProgress = isClosingInProgress
    )

    /**
     * 자기 앱(GemGemGen) 종료 허용 여부를 판정합니다.
     */
    fun canCloseSelfApp(
        isAccessibilityServiceEnabled: Boolean,
        isAutomationRunning: Boolean,
        isClosingInProgress: Boolean
    ): Boolean = SelfAppControlPolicy.canClose(
        isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
        isAutomationRunning = isAutomationRunning,
        isClosingInProgress = isClosingInProgress
    )

    /**
     * 디바이스 메모리 정리 버튼 활성화(실행 또는 예약) 가능 여부를 판정합니다.
     * 일반/송신 모드에서 접근성(또는 원격 전송 가능) 조건이 충족되고, 유지보수 작업이 비진행 상태일 때 허용됩니다.
     */
    fun canCleanMemory(
        mode: AutomationMode,
        isAccessibilityServiceEnabled: Boolean,
        canSendRemote: Boolean,
        isMaintenanceBusy: Boolean
    ): Boolean = when (mode) {
        AutomationMode.SENDER -> canSendRemote && !isMaintenanceBusy
        AutomationMode.RECEIVER -> false
        AutomationMode.NORMAL -> isAccessibilityServiceEnabled && !isMaintenanceBusy
    }

    /**
     * 디바이스 메모리 정리 버튼 활성화 가능 여부 (상태 객체 오버로딩).
     */
    fun canCleanMemory(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        remoteAutomationStatus: RemoteAutomationStatus,
        isMaintenanceBusy: Boolean
    ): Boolean = canCleanMemory(
        mode = mode,
        isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
        canSendRemote = remoteAutomationStatus.canSend,
        isMaintenanceBusy = isMaintenanceBusy
    )

    /**
     * 메모리 정리를 즉시 실행할 수 있는지 판정합니다 (자동화가 실행 중이 아닐 때).
     */
    fun canExecuteCleanMemoryImmediately(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        remoteAutomationStatus: RemoteAutomationStatus,
        isRunning: Boolean,
        isVariationRunning: Boolean,
        isMaintenanceBusy: Boolean
    ): Boolean = canCleanMemory(mode, environmentStatus, remoteAutomationStatus, isMaintenanceBusy) &&
        !isRunning &&
        !isVariationRunning

    /**
     * 메모리 정리를 예약(자동화 종료 후 자동 실행)할 수 있는지 판정합니다 (자동화가 실행 중일 때).
     */
    fun canScheduleCleanMemory(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        remoteAutomationStatus: RemoteAutomationStatus,
        isRunning: Boolean,
        isVariationRunning: Boolean,
        isMaintenanceBusy: Boolean
    ): Boolean = canCleanMemory(mode, environmentStatus, remoteAutomationStatus, isMaintenanceBusy) &&
        (isRunning || isVariationRunning)

    /**
     * 모든 실행 인가 상태를 한 번에 평가한 스냅샷을 반환합니다.
     */
    fun evaluatePermissions(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String,
        isRunning: Boolean,
        remoteAutomationStatus: RemoteAutomationStatus,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false
    ): AutomationPermissions = AutomationPermissions(
        canRun = canRun(
            mode = mode,
            environmentStatus = environmentStatus,
            targetApp = targetApp,
            promptTemplate = promptTemplate,
            isRunning = isRunning,
            remoteAutomationStatus = remoteAutomationStatus,
            isVariationRunning = isVariationRunning,
            isMaintenanceBusy = isMaintenanceBusy
        ),
        canRunVariation = canRunVariation(
            mode = mode,
            environmentStatus = environmentStatus,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        ),
        canInteractWithVariation = canInteractWithVariation(
            mode = mode,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        ),
        variationUnavailableReason = variationUnavailableReason(
            mode = mode,
            environmentStatus = environmentStatus,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isVariationRunning = isVariationRunning
        ),
        canCloseGemini = canCloseGemini(
            isGeminiInstalled = environmentStatus.isGeminiInstalled,
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning || isVariationRunning,
            isClosingInProgress = isMaintenanceBusy
        ),
        canCloseSelfApp = canCloseSelfApp(
            isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
            isAutomationRunning = isRunning || isVariationRunning,
            isClosingInProgress = isMaintenanceBusy
        ),
        canCleanMemory = canCleanMemory(
            mode = mode,
            environmentStatus = environmentStatus,
            remoteAutomationStatus = remoteAutomationStatus,
            isMaintenanceBusy = isMaintenanceBusy
        )
    )
}

/**
 * 화면에서 사용하는 제어 인가 평가 결과 데이터 클래스입니다.
 */
data class AutomationPermissions(
    val canRun: Boolean,
    val canRunVariation: Boolean,
    val canInteractWithVariation: Boolean,
    val variationUnavailableReason: String?,
    val canCloseGemini: Boolean,
    val canCloseSelfApp: Boolean,
    val canCleanMemory: Boolean
)
