package com.example.gemgemgen

import com.example.gemgemgen.ui.MainTab
import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabNavigationTest {

    private val order = listOf(
        MainTab.AUTOMATION,
        MainTab.ANALYSIS,
        MainTab.WILDCARD
    )

    @Test
    fun nextIn_cyclesThroughAllTabs() {
        assertEquals(MainTab.ANALYSIS, MainTab.AUTOMATION.nextIn(order))
        assertEquals(MainTab.WILDCARD, MainTab.ANALYSIS.nextIn(order))
        assertEquals(MainTab.AUTOMATION, MainTab.WILDCARD.nextIn(order))
    }

    @Test
    fun previousIn_cyclesThroughAllTabs() {
        assertEquals(MainTab.WILDCARD, MainTab.AUTOMATION.previousIn(order))
        assertEquals(MainTab.AUTOMATION, MainTab.ANALYSIS.previousIn(order))
        assertEquals(MainTab.ANALYSIS, MainTab.WILDCARD.previousIn(order))
    }
}
