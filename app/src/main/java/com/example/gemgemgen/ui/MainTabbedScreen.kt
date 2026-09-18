// 역할: 상단 탭 전환과 키보드 반응형 키보드 닫기 바를 제공하고 화면 간 0ms 무지연 전환을 조율합니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuInsetBed

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

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val visitedTabs = rememberSaveable { mutableStateListOf<MainTab>() }
    LaunchedEffect(selectedTab) {
        if (!visitedTabs.contains(selectedTab)) {
            visitedTabs.add(selectedTab)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = AppTheme.colors.canvas,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer()
        ) {
            if (!isKeyboardVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuInsetBed(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .graphicsLayer(),
                        shape = RoundedCornerShape(19.dp),
                        backgroundColor = AppTheme.colors.insetBed,
                        borderColor = AppTheme.colors.insetBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { page ->
                                val isSelected = selectedTab == page.tab
                                val pillShape = RoundedCornerShape(16.dp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(
                                            if (isSelected) {
                                                Modifier.shadow(
                                                    elevation = 4.dp,
                                                    shape = pillShape,
                                                    ambientColor = AppTheme.colors.primary.copy(alpha = 0.35f),
                                                    spotColor = AppTheme.colors.primary.copy(alpha = 0.3f)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clip(pillShape)
                                        .background(
                                            if (isSelected) AppTheme.colors.primary else Color.Transparent
                                        )
                                        .clickable {
                                            if (page.tab != selectedTab) {
                                                onSelectTab(page.tab)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = page.tab.label,
                                        color = if (isSelected) AppTheme.colors.onPrimary else AppTheme.colors.textSecondary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = onShowSettings,
                        shape = CircleShape,
                        color = AppTheme.colors.card,
                        border = BorderStroke(1.dp, AppTheme.colors.cardBorder),
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(
                                elevation = 3.dp,
                                shape = CircleShape,
                                ambientColor = AppTheme.colors.shadowDark.copy(alpha = 0.4f),
                                spotColor = AppTheme.colors.shadowDark.copy(alpha = 0.3f)
                            )
                            .graphicsLayer()
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
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { focusManager.clearFocus(force = true) },
                        shape = RoundedCornerShape(14.dp),
                        color = AppTheme.colors.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, AppTheme.colors.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(14.dp),
                                ambientColor = AppTheme.colors.primary.copy(alpha = 0.35f),
                                spotColor = AppTheme.colors.primary.copy(alpha = 0.25f)
                            )
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
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
                    }
                }
            }
        }
    }
}
