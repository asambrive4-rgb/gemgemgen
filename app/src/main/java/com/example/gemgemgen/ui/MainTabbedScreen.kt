// 역할: 상단 고정 탭 전환과 하단 키보드 반응형 키보드 닫기 바를 제공하고 화면 간 0ms 무지연 전환을 조율합니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuSegmentedTabBar

@Immutable
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

    val tabItems = remember(tabs) { tabs.map { it.tab } }
    val isKeyboardVisible = rememberIsImeVisible()
    val focusManager = LocalFocusManager.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val visitedTabs = rememberSaveable { mutableStateListOf<MainTab>() }
    LaunchedEffect(selectedTab) {
        if (!visitedTabs.contains(selectedTab)) {
            visitedTabs.add(selectedTab)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // 상단 탭바는 키보드 개폐와 무관하게 고정 유지하여 상단 높이 변화로 인한 레이아웃 점프를 원천 차단한다.
        Surface(
            color = AppTheme.colors.canvas,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuSegmentedTabBar(
                    items = tabItems,
                    selectedItem = selectedTab,
                    onItemSelected = onSelectTab,
                    itemLabel = { it.label },
                    height = 38.dp,
                    shape = RoundedCornerShape(16.dp),
                    pillShape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer()
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onShowSettings,
                    shape = CircleShape,
                    color = AppTheme.colors.card,
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(
                            elevation = 2.dp,
                            shape = CircleShape,
                            ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.25f),
                            spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.18f)
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            tabs.forEach { page ->
                val isSelected = page.tab == selectedTab
                val hasVisited = isSelected || visitedTabs.contains(page.tab)
                if (hasVisited) {
                    key(page.tab) {
                        saveableStateHolder.SaveableStateProvider(page.tab) {
                            TabPageContent(
                                isSelected = isSelected,
                                page = page
                            )
                        }
                    }
                }
            }
        }

        if (isKeyboardVisible) {
            Surface(
                color = AppTheme.colors.canvas,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { focusManager.clearFocus(force = true) },
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.primary.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .semantics { contentDescription = "가상 키보드 닫기" }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "키보드 닫기",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPageContent(
    isSelected: Boolean,
    page: MainTabPage
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isSelected) 1f else 0f)
            .graphicsLayer {
                alpha = if (isSelected) 1f else 0f
            }
    ) {
        page.content()
    }
}
