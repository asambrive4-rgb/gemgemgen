package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationUiTextTest {
    @Test
    fun statusText_showsStoppedState() {
        assertEquals(
            "자동화 중지됨",
            AutomationUiText.statusText(AutomationUiState.Stopped)
        )
    }
}
