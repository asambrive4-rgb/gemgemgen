// 역할: 디바이스 케어 앱을 통한 메모리 촜적화 자동화 동작을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.MemoryCleanupNodeLabels
import com.example.gemgemgen.automation.android.memoryTitleIdHasMemoryLabel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCareMemoryAutomationTest {
    private val labels = MemoryCleanupNodeLabels(
        memoryTitleId = "device-care:title_text",
        memoryTitleCandidates = listOf("메모리", "Memory")
    )

    @Test
    fun memoryTitleIdCandidate_requiresMemoryLabel() {
        assertFalse(
            memoryTitleIdHasMemoryLabel(
                viewIdResourceName = labels.memoryTitleId,
                nodeLabel = "71%",
                labels = labels
            )
        )
        assertTrue(
            memoryTitleIdHasMemoryLabel(
                viewIdResourceName = labels.memoryTitleId,
                nodeLabel = "Memory 71%",
                labels = labels
            )
        )
        assertFalse(
            memoryTitleIdHasMemoryLabel(
                viewIdResourceName = "other:title_text",
                nodeLabel = "Memory 71%",
                labels = labels
            )
        )
    }
}
