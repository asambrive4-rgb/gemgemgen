package com.example.gemgemgen.remote.domain

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.domain.AutomationTargetApp

data class RemoteAutomationRequest(
    val requestId: String,
    val promptTemplate: String,
    val repeatCountText: String,
    val targetApp: AutomationTargetApp
)

data class RemoteAutomationStatus(
    val mode: AutomationMode = AutomationMode.NORMAL,
    val isReceiverRunning: Boolean = false,
    val receiverPairingCode: String = "",
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

sealed interface RemoteActionResult {
    data object Success : RemoteActionResult
    data class Failure(val message: String) : RemoteActionResult
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
)

sealed interface RemoteExecutionDecision {
    data object Allowed : RemoteExecutionDecision
    data class Rejected(val message: String) : RemoteExecutionDecision
}
