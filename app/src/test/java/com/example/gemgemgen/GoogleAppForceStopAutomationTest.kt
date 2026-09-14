// 역할: Google 앱 상세 설정 화면 강제 중지 및 확인 다이얼로그 노드 매칭을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.ForceStopNodeLabels
import com.example.gemgemgen.automation.android.isConfirmDialogButton
import com.example.gemgemgen.automation.android.isForceStopButton
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAppForceStopAutomationTest {
    private val labels = ForceStopNodeLabels()

    @Test
    fun forceStopButton_matchesResourceId() {
        assertTrue(
            isForceStopButton(
                viewIdResourceName = "com.android.settings:id/forcestop_button",
                nodeLabel = null,
                labels = labels
            )
        )
        assertTrue(
            isForceStopButton(
                viewIdResourceName = "com.android.settings:id/button2_negative",
                nodeLabel = null,
                labels = labels
            )
        )
        assertTrue(
            isForceStopButton(
                viewIdResourceName = "com.android.settings:id/force_stop_button",
                nodeLabel = null,
                labels = labels
            )
        )
    }

    @Test
    fun forceStopButton_matchesCandidateLabels() {
        assertTrue(
            isForceStopButton(
                viewIdResourceName = null,
                nodeLabel = "강제 중지",
                labels = labels
            )
        )
        assertTrue(
            isForceStopButton(
                viewIdResourceName = null,
                nodeLabel = "강제 종료",
                labels = labels
            )
        )
        assertTrue(
            isForceStopButton(
                viewIdResourceName = null,
                nodeLabel = "Force stop",
                labels = labels
            )
        )
    }

    @Test
    fun forceStopButton_nonMatchingIdAndLabel_returnsFalse() {
        assertFalse(
            isForceStopButton(
                viewIdResourceName = "com.android.settings:id/other_button",
                nodeLabel = "저장공간",
                labels = labels
            )
        )
    }

    @Test
    fun confirmDialogButton_matchesResourceId() {
        assertTrue(
            isConfirmDialogButton(
                viewIdResourceName = "android:id/button1",
                nodeLabel = null,
                labels = labels
            )
        )
        assertTrue(
            isConfirmDialogButton(
                viewIdResourceName = "com.android.settings:id/button1",
                nodeLabel = null,
                labels = labels
            )
        )
    }

    @Test
    fun confirmDialogButton_matchesCandidateLabels() {
        assertTrue(
            isConfirmDialogButton(
                viewIdResourceName = null,
                nodeLabel = "확인",
                labels = labels
            )
        )
        assertTrue(
            isConfirmDialogButton(
                viewIdResourceName = null,
                nodeLabel = "강제 중지",
                labels = labels
            )
        )
        assertTrue(
            isConfirmDialogButton(
                viewIdResourceName = null,
                nodeLabel = "OK",
                labels = labels
            )
        )
    }

    @Test
    fun confirmDialogButton_nonMatchingIdAndLabel_returnsFalse() {
        assertFalse(
            isConfirmDialogButton(
                viewIdResourceName = "android:id/button2",
                nodeLabel = "취소",
                labels = labels
            )
        )
    }
}
