// 역할: 다중 제미나이 계정의 메인/서브 탭 분리 목록, 수동 소진/복구 및 당분간 사용 금지 토글, 선택 탭 우선 새로고침, 실시간 초기화 만료 반영 및 목록↔로그인 화면을 한 모달 안에서 전환하는 계정 사용량 다이얼로그 호스트(Single Dialog Host)를 제공합니다. (로그인 WebView 자체는 GeminiAccountLoginView가 담당)
package com.example.gemgemgen.usage.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemgemgen.automation.ui.GeminiGradientLogo
import com.example.gemgemgen.ui.theme.AppTheme
import com.example.gemgemgen.ui.theme.NeuButton
import com.example.gemgemgen.ui.theme.NeuInsetBed
import com.example.gemgemgen.ui.theme.NeuModalDialogFrame
import com.example.gemgemgen.usage.android.GeminiAccountLoginSessionHandler
import com.example.gemgemgen.usage.android.MultiProfileGeminiUsageFetcher
import com.example.gemgemgen.usage.domain.GeminiAccountGroup
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.MultiGeminiUsageState
import kotlinx.coroutines.delay

/**
 * 모달 내부 화면 단계 (Single Dialog Host 원칙에 따라 단일 모달 창 안에서 크로스페이드 전환).
 */
private sealed interface MultiUsageModalStage {
    data object AccountList : MultiUsageModalStage
    data class AccountLoginOrSetup(
        val targetProfile: GeminiAccountProfile,
        val isNewAccount: Boolean
    ) : MultiUsageModalStage
}

/**
 * 다중 Google Gemini 계정(5개 이상)의 사용량을 메인/서브 탭 분리 수직 스크롤 목록으로 확인·감시하는 모달 다이얼로그.
 *
 * - 상단 헤더: "Gemini 계정 사용량" 타이틀 + [+ 계정 추가], [전체 새로고침(🔄)], 닫기(✕) 버튼
 * - 탭 바: [메인 (N개)] / [서브 (N개)] 세그먼트 전환
 * - 수직 스크롤 목록: 등록된 계정 카드([GeminiAccountRowCard]) 나열
 * - 빈 상태(Empty State): 전체 빈 상태 및 탭별 빈 상태 안내 화면 제공
 * - 인앱 [계정 추가] / [로그인]: [AccountIsolatedLoginContent]로 크로스페이드 전환하여 독립 세션 로그인 제공
 */
@Composable
fun MultiGeminiUsageDialog(
    multiUsageState: MultiGeminiUsageState,
    onAddAccount: (alias: String, email: String, accountId: String, group: GeminiAccountGroup) -> Unit,
    onUpdateAccount: (accountId: String, alias: String, email: String) -> Unit,
    onRemoveAccount: (accountId: String) -> Unit,
    onSelectRepresentativeAccount: (accountId: String) -> Unit,
    onRefreshAccount: (accountId: String) -> Unit,
    onRefreshAll: (GeminiAccountGroup?) -> Unit,
    onSwitchAccount: ((accountId: String) -> Unit)? = null,
    onMoveAccountGroup: ((accountId: String, targetGroup: GeminiAccountGroup) -> Unit)? = null,
    onToggleManualExhaustion: ((accountId: String, isExhausted: Boolean) -> Unit)? = null,
    onToggleAccountSuspension: ((accountId: String, isSuspended: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    sessionHandler: GeminiAccountLoginSessionHandler = MultiProfileGeminiUsageFetcher.loginSessionHandler()
) {
    var activeStage by remember {
        mutableStateOf<MultiUsageModalStage>(MultiUsageModalStage.AccountList)
    }
    var selectedTab by remember(multiUsageState.representativeAccount?.profile?.id) {
        mutableStateOf(
            multiUsageState.representativeAccount?.profile?.group ?: GeminiAccountGroup.MAIN
        )
    }

    val openNewAccountStage = {
        val currentGroupCount = multiUsageState.accountCountForGroup(selectedTab)
        val defaultAlias = if (selectedTab == GeminiAccountGroup.MAIN) {
            if (currentGroupCount == 0) "개인 본계정" else "작업용 $currentGroupCount"
        } else {
            "서브 계정 ${currentGroupCount + 1}"
        }
        val freshId = GeminiAccountProfile.generateAccountId()
        val draftProfile = GeminiAccountProfile(
            id = freshId,
            alias = defaultAlias,
            email = "",
            sortOrder = multiUsageState.accountCount,
            webViewProfileName = GeminiAccountProfile.buildWebViewProfileName(freshId),
            createdAt = System.currentTimeMillis(),
            group = selectedTab
        )
        activeStage = MultiUsageModalStage.AccountLoginOrSetup(
            targetProfile = draftProfile,
            isNewAccount = true
        )
    }

    val openExistingAccountStage: (GeminiAccountProfile) -> Unit = { profile ->
        activeStage = MultiUsageModalStage.AccountLoginOrSetup(
            targetProfile = profile,
            isNewAccount = false
        )
    }

    NeuModalDialogFrame(
        onDismiss = {
            if (activeStage is MultiUsageModalStage.AccountLoginOrSetup) {
                activeStage = MultiUsageModalStage.AccountList
            } else {
                onDismiss()
            }
        },
        widthFraction = 0.95f
    ) {
        AnimatedContent(
            targetState = activeStage,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
            },
            contentKey = { stage ->
                when (stage) {
                    MultiUsageModalStage.AccountList -> "AccountList"
                    is MultiUsageModalStage.AccountLoginOrSetup -> "Login_${stage.targetProfile.id}"
                }
            },
            label = "MultiGeminiUsageModalCrossfade"
        ) { stage ->
            when (stage) {
                MultiUsageModalStage.AccountList -> {
                    MultiAccountListContent(
                        multiUsageState = multiUsageState,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        onAddAccountClick = openNewAccountStage,
                        onRefreshAll = { onRefreshAll(selectedTab) },
                        onSelectRepresentativeAccount = onSelectRepresentativeAccount,
                        onRefreshAccount = onRefreshAccount,
                        onOpenAccountLogin = openExistingAccountStage,
                        onEditAccount = openExistingAccountStage,
                        onRemoveAccount = onRemoveAccount,
                        onSwitchAccount = onSwitchAccount,
                        onMoveAccountGroup = onMoveAccountGroup,
                        onToggleManualExhaustion = onToggleManualExhaustion,
                        onToggleAccountSuspension = onToggleAccountSuspension,
                        onDismiss = onDismiss
                    )
                }
                is MultiUsageModalStage.AccountLoginOrSetup -> {
                    AccountIsolatedLoginContent(
                        targetProfile = stage.targetProfile,
                        isNewAccount = stage.isNewAccount,
                        sessionHandler = sessionHandler,
                        onSaveAndFinish = { alias, email ->
                            if (stage.isNewAccount) {
                                onAddAccount(alias, email, stage.targetProfile.id, stage.targetProfile.group)
                            } else {
                                onUpdateAccount(stage.targetProfile.id, alias, email)
                                onRefreshAccount(stage.targetProfile.id)
                            }
                            activeStage = MultiUsageModalStage.AccountList
                        },
                        onBackToList = {
                            activeStage = MultiUsageModalStage.AccountList
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiAccountListContent(
    multiUsageState: MultiGeminiUsageState,
    selectedTab: GeminiAccountGroup,
    onTabSelected: (GeminiAccountGroup) -> Unit,
    onAddAccountClick: () -> Unit,
    onRefreshAll: () -> Unit,
    onSelectRepresentativeAccount: (String) -> Unit,
    onRefreshAccount: (String) -> Unit,
    onOpenAccountLogin: (GeminiAccountProfile) -> Unit,
    onEditAccount: (GeminiAccountProfile) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onSwitchAccount: ((String) -> Unit)? = null,
    onMoveAccountGroup: ((String, GeminiAccountGroup) -> Unit)? = null,
    onToggleManualExhaustion: ((accountId: String, isExhausted: Boolean) -> Unit)? = null,
    onToggleAccountSuspension: ((accountId: String, isSuspended: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val representativeId = multiUsageState.representativeAccount?.profile?.id
    val brandHighlightColor = resolveGeminiUsageNormalHighlightColor()

    var nowMillis by remember(multiUsageState) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(multiUsageState.accounts) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 모달 상단 헤더: 타이틀 + [+ 계정 추가], [전체 새로고침(🔄)], 닫기(✕)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    imageVector = GeminiGradientLogo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "Gemini 계정 사용량",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = if (multiUsageState.isEmpty) {
                            "5개 이상의 계정을 독립 세션으로 등록해 잔여 한도를 감시하세요"
                        } else {
                            "등록된 계정 ${multiUsageState.accountCount}개 · 메인 ${multiUsageState.mainAccountCount}개 / 서브 ${multiUsageState.subAccountCount}개"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // 닫기(✕) 버튼
            Surface(
                shape = CircleShape,
                color = AppTheme.colors.insetBed,
                border = BorderStroke(1.dp, AppTheme.colors.insetBorder),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "모달 닫기" }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 상단 액션 툴바: [계정 추가] 버튼 & [전체 새로고침(🔄)] 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuButton(
                onClick = onAddAccountClick,
                isPrimary = true,
                shape = RoundedCornerShape(11.dp),
                contentModifier = Modifier.padding(PaddingValues(0.dp)),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .semantics { contentDescription = "계정 추가" }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${selectedTab.label} 계정 추가",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            NeuButton(
                onClick = onRefreshAll,
                enabled = !multiUsageState.isEmpty && !multiUsageState.isAnyRefreshing,
                isPrimary = false,
                shape = RoundedCornerShape(11.dp),
                contentModifier = Modifier.padding(PaddingValues(0.dp)),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .semantics { contentDescription = "전체 새로고침" }
            ) {
                if (multiUsageState.isRefreshingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = brandHighlightColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = brandHighlightColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (multiUsageState.isRefreshingAll) "전체 갱신 중…" else "전체 새로고침",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }
        }

        // 메인 / 서브 탭 세그먼트 전환 바
        MultiAccountGroupTabBar(
            selectedGroup = selectedTab,
            onGroupSelected = onTabSelected,
            multiUsageState = multiUsageState
        )

        // 본문: 빈 상태(Empty State) 또는 5개+ 수직 스크롤 목록(LazyColumn)
        if (multiUsageState.isEmpty) {
            MultiAccountEmptyStateView(onAddAccountClick = onAddAccountClick)
        } else if (multiUsageState.isGroupEmpty(selectedTab)) {
            MultiAccountGroupEmptyStateView(
                group = selectedTab,
                onAddAccountClick = onAddAccountClick
            )
        } else {
            val usableOrPendingAccounts = remember(multiUsageState, selectedTab, nowMillis) {
                multiUsageState.availableAccountsSorted(group = selectedTab, nowMillis = nowMillis)
            }
            val exhaustedAccounts = remember(multiUsageState, selectedTab, nowMillis) {
                multiUsageState.exhaustedAccountsSorted(group = selectedTab, nowMillis = nowMillis)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                if (usableOrPendingAccounts.isNotEmpty()) {
                    item(key = "header_usable_${selectedTab.name}") {
                        AccountSectionHeader(
                            title = "사용 가능 ${usableOrPendingAccounts.size}개",
                            hint = "5시간 초기화 임박 순",
                            indicatorColor = brandHighlightColor
                        )
                    }
                    items(
                        items = usableOrPendingAccounts,
                        key = { it.profile.id }
                    ) { accountEntry ->
                        GeminiAccountRowCard(
                            entry = accountEntry,
                            isRepresentative = accountEntry.profile.id == representativeId,
                            onSelectRepresentative = {
                                onSelectRepresentativeAccount(accountEntry.profile.id)
                            },
                            onRefresh = {
                                onRefreshAccount(accountEntry.profile.id)
                            },
                            onOpenLogin = {
                                onOpenAccountLogin(accountEntry.profile)
                            },
                            onEditAccount = {
                                onEditAccount(accountEntry.profile)
                            },
                            onDeleteAccount = {
                                onRemoveAccount(accountEntry.profile.id)
                            },
                            onSwitchAccount = onSwitchAccount?.let { switchFn ->
                                { switchFn(accountEntry.profile.id) }
                            },
                            onMoveAccountGroup = onMoveAccountGroup?.let { moveFn ->
                                { targetGroup -> moveFn(accountEntry.profile.id, targetGroup) }
                            },
                            onToggleManualExhaustion = onToggleManualExhaustion?.let { fn ->
                                { isExhausted -> fn(accountEntry.profile.id, isExhausted) }
                            },
                            onToggleAccountSuspension = onToggleAccountSuspension?.let { fn ->
                                { isSuspended -> fn(accountEntry.profile.id, isSuspended) }
                            },
                            nowMillis = nowMillis
                        )
                    }
                }

                if (exhaustedAccounts.isNotEmpty()) {
                    item(key = "header_exhausted_${selectedTab.name}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(AppTheme.colors.cardBorder.copy(alpha = 0.5f))
                            )
                            AccountSectionHeader(
                                title = "사용 불가 ${exhaustedAccounts.size}개",
                                hint = "해제 임박 순 · 탭하여 선택 가능",
                                indicatorColor = MultiUsageWarningRed
                            )
                        }
                    }
                    items(
                        items = exhaustedAccounts,
                        key = { it.profile.id }
                    ) { accountEntry ->
                        GeminiAccountRowCard(
                            entry = accountEntry,
                            isRepresentative = accountEntry.profile.id == representativeId,
                            onSelectRepresentative = {
                                onSelectRepresentativeAccount(accountEntry.profile.id)
                            },
                            onRefresh = {
                                onRefreshAccount(accountEntry.profile.id)
                            },
                            onOpenLogin = {
                                onOpenAccountLogin(accountEntry.profile)
                            },
                            onEditAccount = {
                                onEditAccount(accountEntry.profile)
                            },
                            onDeleteAccount = {
                                onRemoveAccount(accountEntry.profile.id)
                            },
                            onSwitchAccount = onSwitchAccount?.let { switchFn ->
                                { switchFn(accountEntry.profile.id) }
                            },
                            onMoveAccountGroup = onMoveAccountGroup?.let { moveFn ->
                                { targetGroup -> moveFn(accountEntry.profile.id, targetGroup) }
                            },
                            onToggleManualExhaustion = onToggleManualExhaustion?.let { fn ->
                                { isExhausted -> fn(accountEntry.profile.id, isExhausted) }
                            },
                            onToggleAccountSuspension = onToggleAccountSuspension?.let { fn ->
                                { isSuspended -> fn(accountEntry.profile.id, isSuspended) }
                            },
                            nowMillis = nowMillis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 다이얼로그 내부 '메인' / '서브' 세그먼트 탭 바.
 */
@Composable
private fun MultiAccountGroupTabBar(
    selectedGroup: GeminiAccountGroup,
    onGroupSelected: (GeminiAccountGroup) -> Unit,
    multiUsageState: MultiGeminiUsageState,
    modifier: Modifier = Modifier
) {
    NeuInsetBed(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AccountTabSegmentItem(
                title = "메인 (${multiUsageState.mainAccountCount})",
                isSelected = selectedGroup == GeminiAccountGroup.MAIN,
                onClick = { onGroupSelected(GeminiAccountGroup.MAIN) },
                modifier = Modifier.weight(1f)
            )

            AccountTabSegmentItem(
                title = "서브 (${multiUsageState.subAccountCount})",
                isSelected = selectedGroup == GeminiAccountGroup.SUB,
                onClick = { onGroupSelected(GeminiAccountGroup.SUB) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AccountTabSegmentItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brandHighlightColor = resolveGeminiUsageNormalHighlightColor()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) AppTheme.colors.card else androidx.compose.ui.graphics.Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, AppTheme.colors.cardBorder) else null,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) brandHighlightColor else AppTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 특정 탭(메인 또는 서브)에 계정이 등록되어 있지 않을 때 표시하는 가이드 뷰.
 */
@Composable
private fun MultiAccountGroupEmptyStateView(
    group: GeminiAccountGroup,
    onAddAccountClick: () -> Unit
) {
    val brandHighlightColor = resolveGeminiUsageNormalHighlightColor()
    NeuInsetBed(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "등록된 ${group.label} 계정이 없습니다",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = if (group == GeminiAccountGroup.MAIN) {
                    "주로 사용하는 계정을 메인 탭에 등록해 잔여 한도를 집중 모니터링하세요."
                } else {
                    "자주 사용하지 않는 보조 계정을 서브 탭에 추가해 관리하세요."
                },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            NeuButton(
                onClick = onAddAccountClick,
                isPrimary = true,
                shape = RoundedCornerShape(10.dp),
                contentModifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${group.label} 계정 추가하기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AccountSectionHeader(
    title: String,
    hint: String,
    indicatorColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                fontSize = 11.sp
            )
        }

        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.textSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MultiAccountEmptyStateView(
    onAddAccountClick: () -> Unit
) {
    val brandHighlightColor = resolveGeminiUsageNormalHighlightColor()
    NeuInsetBed(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = brandHighlightColor.copy(alpha = 0.14f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = brandHighlightColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "등록된 Gemini Pro 계정이 없습니다",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "본계정과 서브 계정(5개 이상)을 추가하면 쿠키·세션 덮어쓰기 없이\n각 계정의 5시간 한도와 주간 한도를 한눈에 감시할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    color = AppTheme.colors.textSecondary
                )
            }

            NeuButton(
                onClick = onAddAccountClick,
                isPrimary = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "첫 번째 Google 계정 추가하기",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

