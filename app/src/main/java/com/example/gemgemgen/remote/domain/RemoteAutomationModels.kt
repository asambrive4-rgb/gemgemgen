// 역할: 원격 제어 요청 데이터, 응답 패킷, 무인증 자동 연결 상태 모델 및 실행 인가 정책을 정의합니다.
package com.example.gemgemgen.remote.domain

import com.example.gemgemgen.automation.domain.AutomationMode
import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.wildcard.domain.WildcardSet
import com.example.gemgemgen.core.AppDefaults

data class RemoteAutomationRequest(
    val requestId: String,
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp,
    val flowImageCount: Int = AppDefaults.DEFAULT_FLOW_IMAGE_COUNT,
    val wildcards: List<WildcardSet> = emptyList()
)

data class RemoteAutomationStatus(
    val mode: AutomationMode = AutomationMode.NORMAL,
    val isReceiverRunning: Boolean = false,
    val discoveredDeviceName: String = "",
    val isPaired: Boolean = false,
    val connectionMessage: String = "",
    val message: String = "",
    val automationState: AutomationRunState = AutomationRunState.Idle
) {
    val canSend: Boolean
        get() = mode == AutomationMode.SENDER &&
            discoveredDeviceName.isNotBlank() &&
            isPaired
}

data class RemoteExecutionConditions(
    val isWifiConnected: Boolean,
    val isScreenInteractive: Boolean,
    val isDeviceLocked: Boolean,
    val isTargetAppInstalled: Boolean,
    val isAccessibilityServiceEnabled: Boolean,
    val hasWriteSecureSettingsPermission: Boolean,
    val isWildcardDirectoryAccessible: Boolean,
    val hasOverlayPermission: Boolean,
    val isAutomationBusy: Boolean
) {
    fun decide(requiresWildcardDirectory: Boolean = true): RemoteExecutionDecision {
        return when {
            !isWifiConnected -> RemoteExecutionDecision.Rejected("S25 FE가 Wi-Fi에 연결되어 있지 않습니다.")
            !isScreenInteractive || isDeviceLocked ->
                RemoteExecutionDecision.Rejected("S25 FE의 화면이 꺼져 있거나 잠겨 있습니다.")
            !isTargetAppInstalled -> RemoteExecutionDecision.Rejected("S25 FE에 대상 앱이 설치되어 있지 않습니다.")
            !isAccessibilityServiceEnabled ->
                RemoteExecutionDecision.Rejected("S25 FE의 접근성 서비스를 먼저 켜주세요.")
            !hasWriteSecureSettingsPermission ->
                RemoteExecutionDecision.Rejected("S25 FE에 입력기 변경 권한이 없습니다.")
            requiresWildcardDirectory && !isWildcardDirectoryAccessible ->
                RemoteExecutionDecision.Rejected("S25 FE의 wildcard 폴더에 접근할 수 없습니다.")
            !hasOverlayPermission ->
                RemoteExecutionDecision.Rejected("S25 FE의 다른 앱 위에 표시 권한을 먼저 허용해주세요.")
            else -> RemoteExecutionDecision.Allowed
        }
    }
}

sealed interface RemoteExecutionDecision {
    data object Allowed : RemoteExecutionDecision
    data class Rejected(val message: String) : RemoteExecutionDecision
}
