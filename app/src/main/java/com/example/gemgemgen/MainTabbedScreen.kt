package com.example.gemgemgen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal class MainTabPage(
    val tab: MainTab,
    val content: @Composable () -> Unit
)

@Composable
internal fun MainTabbedScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onShowSettings: () -> Unit,
    tabs: List<MainTabPage>
) {
    if (tabs.isEmpty()) return

    val selectedTabIndex = tabs.indexOfFirst { it.tab == selectedTab }
        .takeIf { it >= 0 }
        ?: 0
    val selectedPage = tabs[selectedTabIndex]

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEach { page ->
                        Tab(
                            selected = selectedTab == page.tab,
                            onClick = { onSelectTab(page.tab) },
                            text = { Text(page.tab.label) }
                        )
                    }
                }
            }
            IconButton(
                onClick = onShowSettings,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "설정"
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
