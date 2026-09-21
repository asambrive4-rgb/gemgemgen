// 역할: 변주 자동화의 실행 가능 여부 및 불가능 사유를 판별하는 도메인 정책입니다.
package com.example.gemgemgen.automation.domain

import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode

object VariationStartPolicy {

    sealed interface Evaluation {
        data object Executable : Evaluation
        data class Rejected(val reason: String) : Evaluation
    }

    fun canRun(
        isReceiverMode: Boolean,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean {
        return !isReceiverMode &&
            !isRunning &&
            !isVariationRunning &&
            !isMaintenanceBusy &&
            isGeminiInstalled &&
            isAccessibilityServiceEnabled
    }

    fun canRun(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean = canRun(
        isReceiverMode = mode == AutomationMode.RECEIVER,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isGeminiInstalled = environmentStatus.isGeminiInstalled,
        isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
        isVariationRunning = isVariationRunning
    )

    fun canInteract(
        isReceiverMode: Boolean,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean {
        return !isReceiverMode &&
            !isRunning &&
            !isMaintenanceBusy &&
            !isVariationRunning
    }

    fun canInteract(
        mode: AutomationMode,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Boolean = canInteract(
        isReceiverMode = mode == AutomationMode.RECEIVER,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isVariationRunning = isVariationRunning
    )

    fun unavailableReason(
        isReceiverMode: Boolean,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isVariationRunning: Boolean = false
    ): String? = when {
        isReceiverMode -> "수신 모드에서는 변주를 실행할 수 없습니다."
        isRunning -> "자동화 실행 중에는 변주를 실행할 수 없습니다."
        isVariationRunning -> "변주 자동화가 이미 실행 중입니다."
        isMaintenanceBusy -> "유지보수 작업이 진행 중입니다."
        !isGeminiInstalled -> "Gemini 앱을 먼저 설치해주세요."
        !isAccessibilityServiceEnabled -> "접근성 서비스를 먼저 켜주세요."
        else -> null
    }

    fun unavailableReason(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): String? = unavailableReason(
        isReceiverMode = mode == AutomationMode.RECEIVER,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isGeminiInstalled = environmentStatus.isGeminiInstalled,
        isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
        isVariationRunning = isVariationRunning
    )

    fun evaluate(
        isReceiverMode: Boolean,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isGeminiInstalled: Boolean,
        isAccessibilityServiceEnabled: Boolean,
        isVariationRunning: Boolean = false
    ): Evaluation {
        val reason = unavailableReason(
            isReceiverMode = isReceiverMode,
            isRunning = isRunning,
            isMaintenanceBusy = isMaintenanceBusy,
            isGeminiInstalled = isGeminiInstalled,
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled,
            isVariationRunning = isVariationRunning
        )
        return if (reason != null) {
            Evaluation.Rejected(reason)
        } else {
            Evaluation.Executable
        }
    }

    fun evaluate(
        mode: AutomationMode,
        environmentStatus: EnvironmentStatus,
        isRunning: Boolean,
        isMaintenanceBusy: Boolean,
        isVariationRunning: Boolean = false
    ): Evaluation = evaluate(
        isReceiverMode = mode == AutomationMode.RECEIVER,
        isRunning = isRunning,
        isMaintenanceBusy = isMaintenanceBusy,
        isGeminiInstalled = environmentStatus.isGeminiInstalled,
        isAccessibilityServiceEnabled = environmentStatus.isAccessibilityServiceEnabled,
        isVariationRunning = isVariationRunning
    )
}
