// 역할: 자동화 실행(프롬프트 실험 모드 조건 검증 포함), 변주, 앱 제어, 메모리 정리, Gemini 계정 전환 및 송신 기기 대기·수신 기기 예약 생성 등 전반적인 실행 인가 정책을 총괄하는 도메인 정책입니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import com.example.gemgemgen.usage.domain.GeminiQuotaExhaustionReason

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
sealed interface AutomationStartDecision {
    data object Started : AutomationStartDecision
    data object RemoteStarted : AutomationStartDecision
    data object PermissionRequired : AutomationStartDecision
    data object Rejected : AutomationStartDecision
}

object AutomationExecutionPolicy {

    /**
     * 비즈니스 컨텍스트와 오버레이 권한 상태를 전달받아 자동화 시작 결정을 내리는 순수 판별 함수입니다.
     */
    fun decideStart(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        targetApp: AutomationTargetApp,
        promptTemplate: String,
        isRunning: Boolean = false,
        remoteAutomationStatus: RemoteAutomationStatus = RemoteAutomationStatus(),
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false,
        isStartInProgress: Boolean = false,
        isExperimentMode: Boolean = false,
        selectedExperimentItem: PromptExperimentItem? = null,
        isOverlayGranted: Boolean = true
    ): AutomationStartDecision = when (mode) {
        AutomationMode.RECEIVER -> AutomationStartDecision.Rejected
        AutomationMode.SENDER -> {
            val canRunSender = canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = environmentStatus,
                targetApp = targetApp,
                promptTemplate = promptTemplate,
                isRunning = isRunning,
                remoteAutomationStatus = remoteAutomationStatus,
                isVariationRunning = isVariationRunning,
                isMaintenanceBusy = isMaintenanceBusy,
                isExperimentMode = isExperimentMode,
                selectedExperimentItem = selectedExperimentItem
            )
            if (canRunSender && !isStartInProgress) {
                AutomationStartDecision.RemoteStarted
            } else {
                AutomationStartDecision.Rejected
            }
        }
        AutomationMode.NORMAL -> {
            if (!isOverlayGranted) {
                AutomationStartDecision.PermissionRequired
            } else {
                val canRunNormal = canRun(
                    mode = AutomationMode.NORMAL,
                    environmentStatus = environmentStatus,
                    targetApp = targetApp,
                    promptTemplate = promptTemplate,
                    isRunning = isRunning,
                    remoteAutomationStatus = RemoteAutomationStatus(),
                    isVariationRunning = isVariationRunning,
                    isMaintenanceBusy = isMaintenanceBusy,
                    isExperimentMode = isExperimentMode,
                    selectedExperimentItem = selectedExperimentItem
                )
                if (canRunNormal && !isStartInProgress) {
                    AutomationStartDecision.Started
                } else {
                    AutomationStartDecision.Rejected
                }
            }
        }
    }

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
        isMaintenanceBusy: Boolean = false,
        isExperimentMode: Boolean = false,
        selectedExperimentItem: PromptExperimentItem? = null
    ): Boolean {
        if (PromptExperimentPolicy.validateExperimentReady(
                isExperimentMode = isExperimentMode,
                promptTemplate = promptTemplate,
                selectedItem = selectedExperimentItem
            ) != null
        ) {
            return false
        }

        return when (mode) {
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

    /**
     * Gemini 구글 계정 자동 전환 실행 가능 여부를 판정합니다.
     */
    fun canSwitchGeminiAccount(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        remoteAutomationStatus: RemoteAutomationStatus,
        isRunning: Boolean,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false
    ): Boolean {
        if (isRunning || isVariationRunning || isMaintenanceBusy) return false
        return when (mode) {
            AutomationMode.SENDER ->
                (environmentStatus.isGeminiInstalled && environmentStatus.isAccessibilityServiceEnabled) ||
                    remoteAutomationStatus.canSend
            AutomationMode.NORMAL,
            AutomationMode.RECEIVER ->
                environmentStatus.isGeminiInstalled && environmentStatus.isAccessibilityServiceEnabled
        }
    }

    /**
     * 송신 기기(SENDER)에서 다른 계정으로 수신 기기 선행(예약) 생성을 실행할 수 없는 사유를 반환합니다.
     * 실행 가능한 경우 null을 반환합니다.
     */
    fun reservePreGenerationUnavailableReason(
        mode: AutomationMode,
        targetApp: AutomationTargetApp,
        remoteAutomationStatus: RemoteAutomationStatus,
        promptTemplate: String,
        isRunning: Boolean,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false,
        isStartInProgress: Boolean = false,
        isExperimentMode: Boolean = false,
        selectedExperimentItem: PromptExperimentItem? = null,
        exhaustionReason: GeminiQuotaExhaustionReason? = null
    ): String? {
        if (mode != AutomationMode.SENDER) {
            return "송신(SENDER) 모드에서만 예약 생성을 사용할 수 있습니다."
        }
        if (targetApp != AutomationTargetApp.GEMINI) {
            return "대상 앱이 Gemini로 선택된 경우에만 예약할 수 있습니다."
        }
        when (exhaustionReason) {
            GeminiQuotaExhaustionReason.SUSPENDED ->
                return "당분간 사용 금지 상태인 계정은 예약할 수 없습니다."
            GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED ->
                return "수동 소진 처리된 계정은 예약할 수 없습니다."
            GeminiQuotaExhaustionReason.FIVE_HOUR_EXHAUSTED,
            GeminiQuotaExhaustionReason.WEEKLY_EXHAUSTED ->
                return "사용량이 소진된 계정은 예약할 수 없습니다."
            null -> Unit
        }
        if (!remoteAutomationStatus.canSend) {
            return "연결된 수신 기기가 없습니다. 송신·수신 연결 상태를 확인해주세요."
        }
        if (isRunning || isStartInProgress) {
            return "자동화가 이미 실행 중입니다."
        }
        if (isVariationRunning) {
            return "변주 자동화 실행 중에는 예약할 수 없습니다."
        }
        if (isMaintenanceBusy) {
            return "유지보수 작업이 진행 중입니다."
        }
        PromptExperimentPolicy.validateExperimentReady(
            isExperimentMode = isExperimentMode,
            promptTemplate = promptTemplate,
            selectedItem = selectedExperimentItem
        )?.let { return it }
        if (!hasPromptTemplate(promptTemplate)) {
            return "프롬프트를 먼저 입력해주세요."
        }
        return null
    }

    /**
     * 송신 기기(SENDER)에서 수신 기기 선행(예약) 이미지 생성 실행 가능 여부를 판정합니다.
     */
    fun canReservePreGeneration(
        mode: AutomationMode,
        targetApp: AutomationTargetApp,
        remoteAutomationStatus: RemoteAutomationStatus,
        promptTemplate: String,
        isRunning: Boolean,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false,
        isStartInProgress: Boolean = false,
        isExperimentMode: Boolean = false,
        selectedExperimentItem: PromptExperimentItem? = null,
        exhaustionReason: GeminiQuotaExhaustionReason? = null
    ): Boolean = reservePreGenerationUnavailableReason(
        mode = mode,
        targetApp = targetApp,
        remoteAutomationStatus = remoteAutomationStatus,
        promptTemplate = promptTemplate,
        isRunning = isRunning,
        isVariationRunning = isVariationRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isStartInProgress = isStartInProgress,
        isExperimentMode = isExperimentMode,
        selectedExperimentItem = selectedExperimentItem,
        exhaustionReason = exhaustionReason
    ) == null

    /**
     * 송신 기기(SENDER) 단독 로컬 Gemini 계정 전환(수신 기기가 이미 생성 중이거나 전환된 경우 포함) 불가 사유를 반환합니다.
     */
    fun senderLocalSwitchUnavailableReason(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false
    ): String? = when {
        mode != AutomationMode.SENDER ->
            "송신(SENDER) 모드에서만 송신 기기 계정 전환을 사용할 수 있습니다."
        isVariationRunning ->
            "변주 자동화 실행 중에는 계정을 전환할 수 없습니다."
        isMaintenanceBusy ->
            "유지보수 작업이 진행 중입니다."
        !environmentStatus.isGeminiInstalled ->
            "Gemini 앱이 설치되어 있지 않습니다."
        !environmentStatus.isAccessibilityServiceEnabled ->
            "접근성 서비스를 먼저 켜주세요."
        else -> null
    }

    /**
     * 송신 기기(SENDER) 단독 로컬 Gemini 계정 전환 가능 여부를 판정합니다.
     * 수신 기기 원격 자동화 실행 중(`isRunning == true`)에도 송신 기기 단독 전환을 허용합니다.
     */
    fun canSwitchSenderLocalAccount(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isVariationRunning: Boolean = false,
        isMaintenanceBusy: Boolean = false
    ): Boolean = senderLocalSwitchUnavailableReason(
        mode = mode,
        environmentStatus = environmentStatus,
        isVariationRunning = isVariationRunning,
        isMaintenanceBusy = isMaintenanceBusy
    ) == null
}

