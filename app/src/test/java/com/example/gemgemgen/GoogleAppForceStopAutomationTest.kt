// 역할: Google 앱 상세 설정 화면 강제 중지 및 확인 다이얼로그 노드 식별자 기본값을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.ForceStopNodeLabels
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAppForceStopAutomationTest {
    private val labels = ForceStopNodeLabels()

    @Test
    fun forceStopNodeLabels_containsExpectedButtonIdsAndCandidates() {
        assertTrue(labels.forceStopButtonIds.contains("com.android.settings:id/forcestop_button"))
        assertTrue(labels.forceStopButtonIds.contains("com.android.settings:id/button2_negative"))
        assertTrue(labels.forceStopButtonIds.contains("com.android.settings:id/force_stop_button"))
        assertTrue(labels.forceStopButtonCandidates.containsAll(listOf("강제 중지", "강제 종료", "Force stop")))
    }

    @Test
    fun forceStopNodeLabels_containsExpectedConfirmDialogIdsAndCandidates() {
        assertTrue(labels.confirmDialogButtonIds.contains("android:id/button1"))
        assertTrue(labels.confirmDialogButtonIds.contains("com.android.settings:id/button1"))
        assertTrue(labels.confirmDialogCandidates.containsAll(listOf("강제 중지", "강제 종료", "확인", "Force stop", "OK")))
    }
}
