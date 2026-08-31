package com.example.gemgemgen.remote.usecase

import com.example.gemgemgen.remote.domain.RemoteExecutionConditions
import com.example.gemgemgen.remote.domain.RemoteExecutionDecision

class CheckRemoteExecutionUseCase {
    fun decide(conditions: RemoteExecutionConditions): RemoteExecutionDecision {
        return when {
            !conditions.isWifiConnected -> rejected("S25 FE가 Wi-Fi에 연결되어 있지 않습니다.")
            !conditions.isScreenInteractive || conditions.isDeviceLocked ->
                rejected("S25 FE의 화면이 꺼져 있거나 잠겨 있습니다.")
            conditions.isAutomationBusy -> rejected("S25 FE에서 다른 자동화를 실행 중입니다.")
            !conditions.isTargetAppInstalled -> rejected("S25 FE에 대상 앱이 설치되어 있지 않습니다.")
            !conditions.isAccessibilityServiceEnabled ->
                rejected("S25 FE의 접근성 서비스를 먼저 켜주세요.")
            !conditions.hasWriteSecureSettingsPermission ->
                rejected("S25 FE에 입력기 변경 권한이 없습니다.")
            !conditions.isWildcardDirectoryAccessible ->
                rejected("S25 FE의 wildcard 폴더에 접근할 수 없습니다.")
            !conditions.hasOverlayPermission ->
                rejected("S25 FE의 다른 앱 위에 표시 권한을 먼저 허용해주세요.")
            else -> RemoteExecutionDecision.Allowed
        }
    }

    private fun rejected(message: String) = RemoteExecutionDecision.Rejected(message)
}
