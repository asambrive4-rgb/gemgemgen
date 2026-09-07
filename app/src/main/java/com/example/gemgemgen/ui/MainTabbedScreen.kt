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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuInsetBed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class MainTabPage(
    val tab: MainTab,
    val content: @Composable () -> Unit
)

/** 순환 스와이프용 가상 페이지 배수 (실제 탭 수 × 이 값). */
private const val TabPagerLoopMultiplier = 400

@Composable
internal fun MainTabbedScreen(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onShowSettings: () -> Unit,
    tabs: List<MainTabPage>
) {
    if (tabs.isEmpty()) return

    val pageCount = tabs.size
    val loopPageCount = pageCount * TabPagerLoopMultiplier
    val selectedTabIndex = tabs.indexOfFirst { it.tab == selectedTab }
        .takeIf { it >= 0 }
        ?: 0

    fun tabIndexOf(page: Int): Int =
        ((page % pageCount) + pageCount) % pageCount

    fun pageForTabIndex(tabIndex: Int, nearPage: Int): Int {
        val base = nearPage - tabIndexOf(nearPage)
        return base + tabIndex
    }

    val initialPage = remember(pageCount) {
        val mid = (loopPageCount / 2 / pageCount) * pageCount
        mid + selectedTabIndex
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopPageCount }
    )
    val onSelectTabLatest = rememberUpdatedState(onSelectTab)
    val selectedTabLatest = rememberUpdatedState(selectedTab)
    // tabs 리스트는 매 리컴포즈마다 새로 생기므로 내용만 최신으로 구독한다.
    val tabsLatest = rememberUpdatedState(tabs)
    // 탭 클릭으로 스크롤 중일 때 settledPage → onSelectTab 피드백을 막아 왕복 튕김 방지
    var programmaticScroll by remember { mutableStateOf(false) }

    ProvideTabSwipeBlocker {
        val swipeBlocker = LocalTabSwipeBlocker.current

        // Pager가 멈춘 페이지 → 탭 선택 (스와이프·프로그램 스크롤 완료 후)
        // keys에 tabs를 넣지 않는다: 매 리컴포즈 재시작 시 이전 settled 탭으로 되돌아가는 버그 방지
        LaunchedEffect(pagerState, pageCount) {
            snapshotFlow { pagerState.settledPage }
                .map { page -> tabsLatest.value[tabIndexOf(page)].tab }
                .distinctUntilChanged()
                .collect { tab ->
                    if (programmaticScroll) return@collect
                    if (tab == selectedTabLatest.value) return@collect
                    onSelectTabLatest.value(tab)
                }
        }

        // 탭 클릭·핸드오프 등 외부 selectedTab 변경 → Pager 위치 동기화
        LaunchedEffect(selectedTab, pageCount) {
            val targetIndex = tabsLatest.value.indexOfFirst { it.tab == selectedTab }
                .takeIf { it >= 0 }
                ?: return@LaunchedEffect
            if (tabIndexOf(pagerState.settledPage) == targetIndex) return@LaunchedEffect

            programmaticScroll = true
            try {
                // 스크롤 중간 currentPage 기준이 아니라 settled 근처 블록에서 목표 페이지 계산
                val nearPage = if (pagerState.isScrollInProgress) {
                    pagerState.currentPage
                } else {
                    pagerState.settledPage
                }
                val targetPage = pageForTabIndex(targetIndex, nearPage)
                pagerState.animateScrollToPage(targetPage)
            } finally {
                programmaticScroll = false
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = AppTheme.colors.canvas,
                modifier = Modifier.fillMaxWidth()
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
                            .height(42.dp),
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

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                beyondViewportPageCount = 1,
                // 텍스트 입력·슬라이더 위에서는 가로 스와이프 비활성
                userScrollEnabled = !swipeBlocker.isBlocked
            ) { page ->
                val tabIndex = tabIndexOf(page)
                Box(modifier = Modifier.fillMaxSize()) {
                    tabsLatest.value[tabIndex].content()
                }
            }
        }
    }
}
