package com.example.gemgemgen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun MainTabbedScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    automationContent: @Composable () -> Unit,
    wildcardContent: @Composable () -> Unit
) {
    val tabs = MainTab.values()

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(tab.label) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                MainTab.AUTOMATION -> automationContent()
                MainTab.WILDCARD -> wildcardContent()
            }
        }
    }
}
