// 역할: GPU 레이어 캐싱을 적용한 탭 바를 통해 기능별 메인 화면을 부드럽게 전환하는 레이아웃을 구성합니다.
package com.example.gemgemgen.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    val saveableStateHolder = rememberSaveableStateHolder()

    Column(modifier = Modifier.fillMaxSize()) {
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeuInsetBed(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .graphicsLayer(),
                    shape = RoundedCornerShape(21.dp),
                    backgroundColor = AppTheme.colors.insetBed,
                    borderColor = AppTheme.colors.insetBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { page ->
                            val isSelected = selectedTab == page.tab
                            val pillShape = RoundedCornerShape(18.dp)
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
                        .size(40.dp)
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
                            modifier = Modifier.size(20.dp)
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
            val currentPage = tabs.firstOrNull { it.tab == selectedTab } ?: tabs.firstOrNull()
            if (currentPage != null) {
                saveableStateHolder.SaveableStateProvider(currentPage.tab) {
                    currentPage.content()
                }
            }
        }
    }
}
