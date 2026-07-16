package com.example.gemgemgen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.height(38.dp)
                    ) {
                        tabs.forEach { page ->
                            Tab(
                                selected = selectedTab == page.tab,
                                onClick = {
                                    if (page.tab != selectedTab) {
                                        // VM 로딩 등은 즉시, 페이지 애니메이션은 selectedTab 동기화
                                        onSelectTab(page.tab)
                                    }
                                },
                                modifier = Modifier.height(38.dp),
                                text = {
                                    Text(
                                        text = page.tab.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onShowSettings,
                    modifier = Modifier
                        .size(38.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "설정",
                        modifier = Modifier.size(22.dp)
                    )
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
