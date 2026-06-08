package com.example.gemgemgen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal class MainTabPage(
    val tab: MainTab,
    val content: @Composable () -> Unit
)

@Composable
internal fun MainTabbedScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    tabs: List<MainTabPage>
) {
    if (tabs.isEmpty()) return

    val selectedTabIndex = tabs.indexOfFirst { it.tab == selectedTab }
        .takeIf { it >= 0 }
        ?: 0
    val selectedPage = tabs[selectedTabIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEach { page ->
                Tab(
                    selected = selectedTab == page.tab,
                    onClick = { onSelectTab(page.tab) },
                    text = { Text(page.tab.label) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            selectedPage.content()
        }
    }
}
