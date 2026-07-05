package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationRunState
import com.example.gemgemgen.automation.ui.AutomationUiText
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationUiTextTest {
    @Test
    fun statusText_showsStoppedState() {
        assertEquals(
            "자동화 중지",
            AutomationUiText.statusText(AutomationRunState.Stopped)
        )
    }
}
