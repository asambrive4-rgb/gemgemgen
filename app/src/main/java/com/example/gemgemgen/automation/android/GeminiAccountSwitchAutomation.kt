// 역할: Gemini 앱 패키지 범위 내에서 사이드바 고정 하단 프로필 카드 또는 상단 메뉴를 통해 Google 프로필(OneGoogle Bento)을 열어 대상 구글 계정으로 안전하게 자동 전환합니다.
package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.domain.GeminiAccountMatcher
import com.example.gemgemgen.automation.domain.GeminiAccountNodeCandidate
import com.example.gemgemgen.automation.usecase.SwitchGeminiAccountResult
import com.example.gemgemgen.core.AppDefaults

internal data class GeminiAccountSwitchNodeLabels(
    val geminiPackageNames: List<String> = listOf(
        AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME,
        AppDefaults.GEMINI_PACKAGE_NAME
    ),
    val toolbarViewIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_TOOLBAR_IDS,
    val sidebarDrawerContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_DRAWER_CONTAINER_IDS,
    val sidebarThreadItemIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_THREAD_ITEM_IDS,
    val profileButtonViewIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_PROFILE_BUTTON_IDS,
    val profileButtonDescriptions: List<String> = listOf(
        "계정 및 설정",
        "Google 계정",
        "Google Account",
        "계정으로 로그인함",
        "Signed in as"
    ),
    val newChatCandidates: List<String> = listOf(
        "새 채팅",
        "New chat"
    ),
    val openSidebarCandidates: List<String> = listOf(
        "사이드바 열기",
        "Open navigation drawer",
        "탐색 메뉴 열기",
        "기본 탐색 열기"
    ),
    val closeSidebarCandidates: List<String> = listOf(
        "사이드바 닫기",
        "Close navigation drawer",
        "탐색 메뉴 닫기"
    ),
    val bentoMenuContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_BENTO_MENU_CONTAINER_IDS,
    val bentoCloseButtonIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_BENTO_CLOSE_BUTTON_IDS,
    val bentoCloseButtonCandidates: List<String> = listOf(
        "닫기",
        "Close"
    ),
    val accountHeaderContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_HEADER_CONTAINER_IDS,
    val accountHeaderChevronIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_HEADER_CHEVRON_IDS,
    val compactHeaderPrimaryTextIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_COMPACT_HEADER_PRIMARY_TEXT_IDS,
    val compactHeaderSecondaryTextIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_COMPACT_HEADER_SECONDARY_TEXT_IDS,
    val accountsListContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNTS_LIST_CONTAINER_IDS,
    val bentoScrollContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_BENTO_SCROLL_CONTAINER_IDS,
    val availableAccountRowIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_AVAILABLE_ACCOUNT_ROW_IDS,
    val accountPrimaryInfoIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_PRIMARY_INFO_IDS,
    val accountSecondaryInfoIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_SECONDARY_INFO_IDS
)

internal data class GeminiSidebarProfileCandidate(
    val className: String = "",
    val viewIdResourceName: String = "",
    val contentDescription: String = "",
    val childTexts: List<String> = emptyList(),
    val childDescriptions: List<String> = emptyList(),
    val isClickable: Boolean = false,
    val isCheckable: Boolean = false,
    val isInsideScrollable: Boolean = false,
    val bounds: NodeBounds? = null,
    val parent: GeminiSidebarProfileCandidate? = null
) {
    val isClickableOrCheckable: Boolean
        get() = isClickable || isCheckable
}

internal object GeminiSidebarProfileMatcher {
    private val BADGE_EXACT_TOKENS = setOf("pro", "plus", "ultra", "advanced")
    private val PROFILE_SUBSTRING_KEYWORDS = listOf(
        "google 계정",
        "google account",
        "계정 및 설정",
        "signed in"
    )
    private val NON_PROFILE_MENU_TEXTS = setOf(
        "새 채팅",
        "new chat",
        "채팅 검색",
        "search chats",
        "spark",
        "학생",
        "이미지",
        "동영상",
        "라이브러리",
        "notebooks",
        "최근"
    )

    fun isSidebarOpen(
        candidates: List<GeminiSidebarProfileCandidate>,
        closeSidebarCandidates: List<String>,
        sidebarDrawerContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_DRAWER_CONTAINER_IDS
    ): Boolean {
        return candidates.any { candidate ->
            val desc = candidate.contentDescription.trim()
            val viewId = candidate.viewIdResourceName.trim()
            (viewId.isNotEmpty() && sidebarDrawerContainerIds.any { it.equals(viewId, ignoreCase = true) }) ||
                closeSidebarCandidates.any { desc.equals(it, ignoreCase = true) }
        }
    }

    /**
     * 사이드바가 실제로 열려 있음이 확인된 경우에만 하단 고정 프로필 노드를 감지합니다.
     * - 최근 대화 항목(`thread_item`), 체크 가능(`isCheckable`) 메뉴, 스크롤 목록 내부 항목,
     *   그리고 '사이드바 닫기' 버튼을 철저히 배제하여 사이드바가 다시 닫히거나 엉뚱한 대화가 열리는 현상을 방지합니다.
     */
    fun findSidebarProfileCandidate(
        candidates: List<GeminiSidebarProfileCandidate>,
        closeSidebarCandidates: List<String>,
        sidebarDrawerContainerIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_DRAWER_CONTAINER_IDS,
        sidebarThreadItemIds: List<String> = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_THREAD_ITEM_IDS
    ): GeminiSidebarProfileCandidate? {
        val isSidebarOpen = isSidebarOpen(candidates, closeSidebarCandidates, sidebarDrawerContainerIds)
        if (!isSidebarOpen) return null

        // 1순위: 구형 RadioButton 기반 프로필 노드
        val radioButtonNode = candidates.firstOrNull { candidate ->
            candidate.className.contains("RadioButton", ignoreCase = true) &&
                !isExcludedSidebarCandidate(candidate, closeSidebarCandidates, sidebarThreadItemIds)
        }
        if (radioButtonNode != null) {
            val parent = radioButtonNode.parent
            if (parent != null && parent.isClickableOrCheckable &&
                !isExcludedSidebarCandidate(parent, closeSidebarCandidates, sidebarThreadItemIds)
            ) {
                return parent
            }
            return radioButtonNode
        }

        // 2순위: 스크롤 영역 바깥(하단 고정 푸터)의 클릭 가능한 프로필 후보
        val footerCandidates = candidates.filter { candidate ->
            candidate.isClickable &&
                !candidate.isCheckable &&
                !candidate.isInsideScrollable &&
                !isExcludedSidebarCandidate(candidate, closeSidebarCandidates, sidebarThreadItemIds) &&
                isValidProfileRowBounds(candidate.bounds)
        }

        // 2-A: 명확한 프로필 배지("PRO", "PLUS" 등 단독 토큰)나 계정 설명 키워드를 가진 하단 고정 행
        val badgeMatched = footerCandidates
            .filter { hasExplicitProfileBadgeOrKeyword(it) }
            .maxByOrNull { it.bounds?.bottom ?: 0 }
        if (badgeMatched != null) {
            return badgeMatched
        }

        // 2-B: 사이드바 하단 고정 영역에서 가장 아래에 위치한 넓은 클릭 가능 카드
        return footerCandidates
            .filter { (it.bounds?.let { b -> b.right - b.left } ?: 0) >= MIN_SIDEBAR_PROFILE_WIDTH_PX }
            .maxByOrNull { it.bounds?.bottom ?: 0 }
    }

    internal fun isExcludedSidebarCandidate(
        candidate: GeminiSidebarProfileCandidate,
        closeSidebarCandidates: List<String>,
        sidebarThreadItemIds: List<String>
    ): Boolean {
        val viewId = candidate.viewIdResourceName.trim()
        if (viewId.isNotEmpty() && sidebarThreadItemIds.any { viewId.equals(it, ignoreCase = true) || viewId.endsWith("/$it", ignoreCase = true) }) {
            return true
        }
        val selfDesc = candidate.contentDescription.trim()
        if (selfDesc.isNotEmpty() && closeSidebarCandidates.any { selfDesc.equals(it, ignoreCase = true) }) {
            return true
        }
        if (candidate.childDescriptions.any { childDesc ->
                val trimmed = childDesc.trim()
                closeSidebarCandidates.any { trimmed.equals(it, ignoreCase = true) }
            }
        ) {
            return true
        }
        if (candidate.childTexts.any { childText ->
                NON_PROFILE_MENU_TEXTS.contains(childText.trim().lowercase())
            }
        ) {
            return true
        }
        return false
    }

    internal fun hasExplicitProfileBadgeOrKeyword(candidate: GeminiSidebarProfileCandidate): Boolean {
        val hasBadgeToken = candidate.childTexts.any { text ->
            val normalized = text.trim().lowercase()
            normalized in BADGE_EXACT_TOKENS || normalized.contains("@")
        }
        if (hasBadgeToken) return true

        val allTextsAndDescs = (candidate.childTexts + candidate.childDescriptions + listOf(candidate.contentDescription))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        return allTextsAndDescs.any { item ->
            PROFILE_SUBSTRING_KEYWORDS.any { keyword -> item.contains(keyword) }
        }
    }

    private fun isValidProfileRowBounds(bounds: NodeBounds?): Boolean {
        if (bounds == null) return false
        val height = bounds.bottom - bounds.top
        return height in 48..320
    }

    private const val MIN_SIDEBAR_PROFILE_WIDTH_PX = 250
}

internal class GeminiAccountSwitchAutomation(
    private val targetEmail: String,
    private val targetAlias: String,
    private val returnToHostApp: Boolean,
    private val handler: Handler,
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val allRootsProvider: () -> List<AccessibilityNodeInfo> = { listOfNotNull(rootProvider()) },
    private val currentPackageProvider: () -> String?,
    private val performBack: () -> Boolean,
    private val tapCoordinates: (x: Float, y: Float, onCompleted: (() -> Unit)?) -> Boolean,
    private val launchGemini: () -> Boolean,
    private val launchHostApp: () -> Boolean,
    private val onFinished: (SwitchGeminiAccountResult) -> Unit,
    private val labels: GeminiAccountSwitchNodeLabels = GeminiAccountSwitchNodeLabels()
) {
    private enum class Phase {
        LAUNCH_GEMINI,
        OPEN_PROFILE_MENU,
        CHECK_OR_EXPAND_ACCOUNTS,
        SELECT_TARGET_ACCOUNT,
        WAIT_SWITCH_COMPLETE,
        CLOSE_MENU_AND_FINISH,
        RETURN_TO_HOST_APP
    }

    private var active = false
    private var phase = Phase.LAUNCH_GEMINI
    private var phaseStartedAtMillis = 0L
    private var lastSidebarTapAtMillis = 0L
    private var lastProfileTapAtMillis = 0L
    private var lastExpandTapAtMillis = 0L
    private var profileTapAttemptCount = 0
    private var expandAttemptCount = 0
    private var scrollCount = 0
    private var returnBackCount = 0
    private var hostLaunchRequested = false

    private val displayLabel: String
        get() = targetAlias.trim().ifBlank { targetEmail.trim().ifBlank { "선택한 계정" } }

    fun start() {
        if (active) return
        if (targetEmail.isBlank() && targetAlias.isBlank()) {
            onFinished(SwitchGeminiAccountResult.Failure("전환할 계정 정보가 비어 있습니다."))
            return
        }
        active = true
        if (!launchGemini()) {
            finish(SwitchGeminiAccountResult.Failure("Gemini 앱을 실행하지 못했습니다."))
            return
        }
        enter(Phase.LAUNCH_GEMINI)
    }

    fun cancel() {
        active = false
    }

    private fun enter(nextPhase: Phase) {
        if (!active) return
        phase = nextPhase
        phaseStartedAtMillis = SystemClock.uptimeMillis()
        if (nextPhase == Phase.OPEN_PROFILE_MENU) {
            profileTapAttemptCount = 0
        }
        if (nextPhase == Phase.CHECK_OR_EXPAND_ACCOUNTS) {
            expandAttemptCount = 0
        }
        if (nextPhase == Phase.SELECT_TARGET_ACCOUNT) {
            scrollCount = 0
        }
        if (nextPhase == Phase.RETURN_TO_HOST_APP) {
            returnBackCount = 0
            hostLaunchRequested = false
        }
        checkPhase()
    }

    private fun checkPhase() {
        if (!active) return

        when (phase) {
            Phase.LAUNCH_GEMINI -> {
                if (isGeminiForeground() || isGeminiScreenVisible(allNodes())) {
                    enter(Phase.OPEN_PROFILE_MENU)
                } else {
                    retryOrFail("Gemini 화면을 열지 못했습니다.") { checkPhase() }
                }
            }

            Phase.OPEN_PROFILE_MENU -> {
                if (isBentoMenuVisible(allNodes())) {
                    enter(Phase.CHECK_OR_EXPAND_ACCOUNTS)
                    return
                }

                // 1순위: 사이드바가 이미 열려 있는 상태라면 사이드바 하단 프로필 카드 클릭
                val sidebarProfileNode = findSidebarProfileNode(allNodes())
                if (sidebarProfileNode != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastProfileTapAtMillis >= PROFILE_TAP_COOLDOWN_MS) {
                        lastProfileTapAtMillis = now
                        val currentAttempt = profileTapAttemptCount
                        profileTapAttemptCount += 1

                        val clicked = if (currentAttempt == 0) {
                            AccessibilityNodeTraversal.clickNodeOrParent(
                                sidebarProfileNode,
                                maxDepth = MAX_ANCESTOR_SEARCH_DEPTH
                            )
                        } else {
                            false
                        }
                        if (!clicked) {
                            val tapPoint = findSidebarProfileTapPoint(sidebarProfileNode)
                            if (tapPoint != null) {
                                tapCoordinates(tapPoint.first, tapPoint.second) {
                                    if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                        handler.postDelayed({ checkPhase() }, POST_TAP_SETTLE_MS)
                                    }
                                }
                                return
                            }
                        } else {
                            handler.postDelayed(
                                {
                                    if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                        checkPhase()
                                    }
                                },
                                POST_TAP_SETTLE_MS
                            )
                            return
                        }
                    }
                    retryOrFail("사이드바 하단 Google 프로필 메뉴를 열지 못했습니다.") { checkPhase() }
                    return
                }

                // 사이드바가 이미 열려 있는 상태에서 하단 프로필 노드가 아직 렌더링 중이거나 찾지 못한 경우,
                // 하위 Fallback(툴바 탭 등)으로 내려가서 사이드바를 다시 닫아버리는 현상을 원천 차단합니다.
                if (isSidebarCurrentlyOpen(allNodes())) {
                    retryOrFail("사이드바 하단 Google 프로필 메뉴를 찾고 있습니다.") { checkPhase() }
                    return
                }

                // 2순위: 사이드바가 닫혀 있는 상태라면 좌측 상단 '사이드바 열기' 클릭
                val openSidebarNode = findOpenSidebarNode(allNodes())
                if (openSidebarNode != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastSidebarTapAtMillis >= SIDEBAR_TAP_COOLDOWN_MS) {
                        lastSidebarTapAtMillis = now
                        val clicked = AccessibilityNodeTraversal.clickNodeOrParent(
                            openSidebarNode,
                            maxDepth = MAX_ANCESTOR_SEARCH_DEPTH
                        )
                        if (!clicked) {
                            val bounds = openSidebarNode.nodeBounds()
                            if (bounds != null) {
                                tapCoordinates(bounds.centerX.toFloat(), bounds.centerY.toFloat()) {
                                    if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                        handler.postDelayed({ checkPhase() }, POST_SIDEBAR_OPEN_SETTLE_MS)
                                    }
                                }
                                return
                            }
                        } else {
                            handler.postDelayed(
                                {
                                    if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                        checkPhase()
                                    }
                                },
                                POST_SIDEBAR_OPEN_SETTLE_MS
                            )
                            return
                        }
                    }
                    retryOrFail("사이드바 열기 반응을 대기하고 있습니다.") { checkPhase() }
                    return
                }

                // 3순위 (Fallback): 툴바 좌측 상단 햄버거 영역 좌표 탭
                val toolbarSidebarTapPoint = findToolbarSidebarTapPoint(allNodes())
                if (toolbarSidebarTapPoint != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastSidebarTapAtMillis >= SIDEBAR_TAP_COOLDOWN_MS) {
                        lastSidebarTapAtMillis = now
                        tapCoordinates(toolbarSidebarTapPoint.first, toolbarSidebarTapPoint.second) {
                            if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                handler.postDelayed({ checkPhase() }, POST_SIDEBAR_OPEN_SETTLE_MS)
                            }
                        }
                        return
                    }
                    retryOrFail("사이드바 열기 좌표 터치 후 대기 중입니다.") { checkPhase() }
                    return
                }

                // 4순위 (Fallback): 새 채팅 홈 화면 등에서 우측 상단 프로필 버튼이 직접 노출된 경우
                val explicitProfileNode = findExplicitProfileButtonNode(allNodes())
                if (explicitProfileNode != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastProfileTapAtMillis >= PROFILE_TAP_COOLDOWN_MS) {
                        lastProfileTapAtMillis = now
                        val clicked = AccessibilityNodeTraversal.clickNodeOrParent(
                            explicitProfileNode,
                            maxDepth = MAX_ANCESTOR_SEARCH_DEPTH
                        )
                        if (clicked) {
                            handler.postDelayed(
                                {
                                    if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                        checkPhase()
                                    }
                                },
                                POST_TAP_SETTLE_MS
                            )
                            return
                        }
                    }
                    retryOrFail("Google 프로필 메뉴를 열지 못했습니다.") { checkPhase() }
                    return
                }

                val tapPoint = findToolbarProfileTapPoint(allNodes())
                if (tapPoint != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastProfileTapAtMillis >= PROFILE_TAP_COOLDOWN_MS) {
                        lastProfileTapAtMillis = now
                        tapCoordinates(tapPoint.first, tapPoint.second) {
                            if (active && phase == Phase.OPEN_PROFILE_MENU) {
                                handler.postDelayed({ checkPhase() }, POST_TAP_SETTLE_MS)
                            }
                        }
                        return
                    }
                    retryOrFail("Google 프로필 메뉴가 열리지 않았습니다.") { checkPhase() }
                    return
                }

                retryOrFail("Gemini 프로필 메뉴 또는 사이드바를 찾지 못했습니다.") { checkPhase() }
            }

            Phase.CHECK_OR_EXPAND_ACCOUNTS -> {
                val headerCandidate = extractCurrentHeaderCandidate(allNodes())
                if (headerCandidate != null &&
                    GeminiAccountMatcher.isExactMatch(headerCandidate, targetEmail, targetAlias)
                ) {
                    enter(Phase.CLOSE_MENU_AND_FINISH)
                    return
                }

                if (isAccountListExpanded(allNodes())) {
                    enter(Phase.SELECT_TARGET_ACCOUNT)
                    return
                }

                val headerContainer = findAccountHeaderContainerNode(allNodes())
                val expandTapPoint = findExpandHeaderTapPoint(allNodes(), headerContainer)
                if (headerContainer != null || expandTapPoint != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastExpandTapAtMillis >= EXPAND_TAP_COOLDOWN_MS) {
                        lastExpandTapAtMillis = now
                        val currentAttempt = expandAttemptCount
                        expandAttemptCount += 1

                        val nodeClicked = if (currentAttempt == 0 && headerContainer != null) {
                            AccessibilityNodeTraversal.clickNodeOrParent(
                                headerContainer,
                                maxDepth = MAX_ANCESTOR_SEARCH_DEPTH
                            )
                        } else {
                            false
                        }

                        if (nodeClicked) {
                            handler.postDelayed(
                                {
                                    if (active && phase == Phase.CHECK_OR_EXPAND_ACCOUNTS) {
                                        checkPhase()
                                    }
                                },
                                POST_EXPAND_SETTLE_MS
                            )
                            return
                        } else if (expandTapPoint != null) {
                            tapCoordinates(expandTapPoint.first, expandTapPoint.second) {
                                if (active && phase == Phase.CHECK_OR_EXPAND_ACCOUNTS) {
                                    handler.postDelayed({ checkPhase() }, POST_EXPAND_SETTLE_MS)
                                }
                            }
                            return
                        }
                    }
                }

                retryOrFail("Google 계정 목록을 펼치지 못했습니다.") { checkPhase() }
            }

            Phase.SELECT_TARGET_ACCOUNT -> {
                val headerCandidate = extractCurrentHeaderCandidate(allNodes())
                if (headerCandidate != null &&
                    GeminiAccountMatcher.isExactMatch(headerCandidate, targetEmail, targetAlias)
                ) {
                    enter(Phase.CLOSE_MENU_AND_FINISH)
                    return
                }

                val scoredRows = findAvailableAccountRows(allNodes())
                    .map { (rowNode, candidate) ->
                        Triple(
                            rowNode,
                            candidate,
                            GeminiAccountMatcher.scoreCandidate(candidate, targetEmail, targetAlias)
                        )
                    }

                val exactRow = scoredRows
                    .filter { (rowNode, _, score) ->
                        score >= GeminiAccountMatcher.EXACT_MATCH_THRESHOLD && isRowSufficientlyVisible(rowNode)
                    }
                    .maxByOrNull { it.third }

                if (exactRow != null) {
                    if (clickAccountRow(exactRow.first)) {
                        enter(Phase.WAIT_SWITCH_COMPLETE)
                        return
                    }
                }

                val scrollableNode = findBentoScrollableNode(allNodes())
                if (scrollableNode != null && scrollCount < MAX_ACCOUNT_LIST_SCROLLS) {
                    val scrolled = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ||
                        scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    scrollCount += 1
                    if (scrolled) {
                        phaseStartedAtMillis = SystemClock.uptimeMillis()
                        handler.postDelayed(
                            {
                                if (active && phase == Phase.SELECT_TARGET_ACCOUNT) {
                                    checkPhase()
                                }
                            },
                            POST_SCROLL_SETTLE_MS
                        )
                        return
                    }
                }

                val headerScore = headerCandidate?.let {
                    GeminiAccountMatcher.scoreCandidate(it, targetEmail, targetAlias)
                } ?: 0
                val bestPartialRow = scoredRows
                    .filter { (rowNode, _, score) -> score > 0 && isRowSufficientlyVisible(rowNode) }
                    .maxByOrNull { it.third }

                if (headerScore > 0 && (bestPartialRow == null || headerScore >= bestPartialRow.third)) {
                    enter(Phase.CLOSE_MENU_AND_FINISH)
                    return
                }

                if (bestPartialRow != null) {
                    if (clickAccountRow(bestPartialRow.first)) {
                        enter(Phase.WAIT_SWITCH_COMPLETE)
                        return
                    }
                }

                retryOrFail(
                    "Gemini에서 '$displayLabel' 계정을 찾지 못했습니다. 기기에 해당 Google 계정이 로그인되어 있는지 확인해주세요."
                ) { checkPhase() }
            }

            Phase.WAIT_SWITCH_COMPLETE -> {
                val elapsed = SystemClock.uptimeMillis() - phaseStartedAtMillis
                if (!isBentoMenuVisible(allNodes()) && elapsed >= MIN_SWITCH_SETTLE_MS) {
                    completeAndReturn()
                } else if (elapsed >= MAX_SWITCH_SETTLE_MS) {
                    completeAndReturn()
                } else {
                    handler.postDelayed(
                        {
                            if (active && phase == Phase.WAIT_SWITCH_COMPLETE) {
                                checkPhase()
                            }
                        },
                        SWITCH_POLL_INTERVAL_MS
                    )
                }
            }

            Phase.CLOSE_MENU_AND_FINISH -> {
                if (!isBentoMenuVisible(allNodes())) {
                    completeAndReturn()
                    return
                }
                val closeBtn = findBentoCloseButtonNode(allNodes())
                val closed = if (closeBtn != null) {
                    AccessibilityNodeTraversal.clickNodeOrParent(closeBtn, maxDepth = MAX_ANCESTOR_SEARCH_DEPTH)
                } else {
                    performBack()
                }
                if (closed) {
                    handler.postDelayed(
                        {
                            if (active) {
                                completeAndReturn()
                            }
                        },
                        POST_CLOSE_SETTLE_MS
                    )
                } else {
                    completeAndReturn()
                }
            }

            Phase.RETURN_TO_HOST_APP -> {
                if (!isGeminiForeground()) {
                    finish(SwitchGeminiAccountResult.Success)
                    return
                }
                if (!hostLaunchRequested) {
                    hostLaunchRequested = true
                    launchHostApp()
                    handler.postDelayed(
                        {
                            if (active && phase == Phase.RETURN_TO_HOST_APP) {
                                checkPhase()
                            }
                        },
                        POST_RETURN_SETTLE_MS
                    )
                    return
                }
                if (returnBackCount < MAX_RETURN_BACKS && performBack()) {
                    returnBackCount += 1
                    handler.postDelayed(
                        {
                            if (active && phase == Phase.RETURN_TO_HOST_APP) {
                                checkPhase()
                            }
                        },
                        POST_RETURN_SETTLE_MS
                    )
                    return
                }
                launchHostApp()
                finish(SwitchGeminiAccountResult.Success)
            }
        }
    }

    private fun completeAndReturn() {
        if (!active) return
        if (returnToHostApp) {
            enter(Phase.RETURN_TO_HOST_APP)
        } else {
            finish(SwitchGeminiAccountResult.Success)
        }
    }

    private fun clickAccountRow(rowNode: AccessibilityNodeInfo): Boolean {
        if (AccessibilityNodeTraversal.clickNodeOrParent(rowNode, maxDepth = MAX_ANCESTOR_SEARCH_DEPTH)) {
            return true
        }
        val bounds = rowNode.nodeBounds() ?: return false
        return tapCoordinates(bounds.centerX.toFloat(), bounds.centerY.toFloat(), null)
    }

    private fun isRowSufficientlyVisible(rowNode: AccessibilityNodeInfo): Boolean {
        val bounds = rowNode.nodeBounds() ?: return true
        return (bounds.bottom - bounds.top) >= MIN_VISIBLE_ROW_HEIGHT_PX
    }

    private fun isGeminiForeground(): Boolean {
        val pkg = currentPackageProvider() ?: return false
        return labels.geminiPackageNames.any { it.equals(pkg, ignoreCase = true) }
    }

    private fun isGeminiScreenVisible(nodes: Sequence<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val pkg = node.packageName?.toString()
            pkg != null && labels.geminiPackageNames.any { it.equals(pkg, ignoreCase = true) }
        }
    }

    private fun isBentoMenuVisible(nodes: Sequence<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val id = node.viewIdResourceName ?: return@any false
            labels.bentoMenuContainerIds.contains(id) ||
                labels.accountHeaderContainerIds.contains(id) ||
                labels.compactHeaderPrimaryTextIds.contains(id) ||
                labels.compactHeaderSecondaryTextIds.contains(id) ||
                labels.bentoCloseButtonIds.contains(id) ||
                labels.availableAccountRowIds.contains(id)
        }
    }

    private fun isAccountListExpanded(nodes: Sequence<AccessibilityNodeInfo>): Boolean {
        var compactHeaderDesc: String? = null
        for (node in nodes) {
            val id = node.viewIdResourceName
            if (id != null) {
                if (labels.accountsListContainerIds.contains(id) || labels.availableAccountRowIds.contains(id)) {
                    return true
                }
                if (compactHeaderDesc == null && id in labels.accountHeaderContainerIds) {
                    compactHeaderDesc = node.contentDescription?.toString().orEmpty()
                }
            }
        }
        val desc = compactHeaderDesc.orEmpty()
        return desc.contains("계정 목록을 펼쳤습니다") || desc.contains("계정 목록을 접습니다")
    }

    private fun findExplicitProfileButtonNode(nodes: Sequence<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        var descMatch: AccessibilityNodeInfo? = null
        for (node in nodes) {
            val viewId = node.viewIdResourceName
            if (viewId != null && labels.profileButtonViewIds.contains(viewId)) {
                return node
            }
            if (descMatch == null) {
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (desc.isNotBlank() &&
                    labels.profileButtonDescriptions.any { desc.contains(it, ignoreCase = true) } &&
                    node.viewIdResourceName !in labels.accountHeaderContainerIds
                ) {
                    descMatch = node
                }
            }
        }
        return descMatch
    }

    private fun findToolbarProfileTapPoint(nodes: Sequence<AccessibilityNodeInfo>): Pair<Float, Float>? {
        val toolbarNode = nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.toolbarViewIds.contains(node.viewIdResourceName)
        } ?: return null
        val toolbarBounds = toolbarNode.nodeBounds() ?: return null

        // 1순위: 툴바 내부의 '새 채팅' 버튼과 그 우측 그룹 컨테이너를 이용해 프로필 아바타 중심 좌표 산출
        val newChatNode = AccessibilityNodeTraversal.lazyTraverse(toolbarNode).firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            labels.newChatCandidates.any { candidate ->
                desc.equals(candidate, ignoreCase = true) || text.equals(candidate, ignoreCase = true)
            }
        }
        val newChatBounds = newChatNode?.nodeBounds()
        val parentBounds = newChatNode?.parent?.nodeBounds()
        if (newChatBounds != null && parentBounds != null &&
            parentBounds.right > newChatBounds.right + MIN_PROFILE_BUTTON_WIDTH_PX
        ) {
            val tapX = (newChatBounds.right + parentBounds.right) / 2f
            val tapY = ((newChatBounds.top + newChatBounds.bottom) / 2f)
            return tapX to tapY
        }

        // 2순위: 툴바 내부 우측 끝 액션 컨테이너의 오른쪽 정사각형 영역 중심 좌표 산출
        val rightContainerBounds = AccessibilityNodeTraversal.lazyTraverse(toolbarNode)
            .mapNotNull { it.nodeBounds() }
            .filter { bounds ->
                val height = bounds.bottom - bounds.top
                val width = bounds.right - bounds.left
                height in 60..220 &&
                    width >= height &&
                    bounds.right >= toolbarBounds.right - 80 &&
                    bounds.left > toolbarBounds.centerX
            }
            .maxByOrNull { it.right }

        if (rightContainerBounds != null) {
            val height = (rightContainerBounds.bottom - rightContainerBounds.top).toFloat()
            val tapX = rightContainerBounds.right - (height / 2f)
            val tapY = (rightContainerBounds.top + rightContainerBounds.bottom) / 2f
            return tapX to tapY
        }

        // 3순위: 툴바 자체의 우측 하단 영역 Fallback
        val toolbarHeight = (toolbarBounds.bottom - toolbarBounds.top).toFloat()
        val estimatedButtonSize = (toolbarHeight * 0.48f).coerceIn(80f, 150f)
        val tapX = toolbarBounds.right - (estimatedButtonSize * 0.75f)
        val tapY = toolbarBounds.bottom - (estimatedButtonSize * 0.65f)
        return tapX to tapY
    }

    private fun isSidebarCurrentlyOpen(nodes: Sequence<AccessibilityNodeInfo>): Boolean {
        for (node in nodes) {
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            if (viewId.isNotEmpty() && labels.sidebarDrawerContainerIds.any { it.equals(viewId, ignoreCase = true) }) {
                return true
            }
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (desc.isNotEmpty() && labels.closeSidebarCandidates.any { desc.equals(it, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    private fun findSidebarProfileNode(nodes: Sequence<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val nodeList = nodes.toList()
        if (nodeList.isEmpty()) return null

        var drawerNode: AccessibilityNodeInfo? = null
        var isSidebarOpen = false
        for (node in nodeList) {
            val viewId = node.viewIdResourceName?.trim().orEmpty()
            if (drawerNode == null && viewId.isNotEmpty() &&
                labels.sidebarDrawerContainerIds.any { it.equals(viewId, ignoreCase = true) }
            ) {
                drawerNode = node
                isSidebarOpen = true
            }
            if (!isSidebarOpen) {
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (desc.isNotEmpty() && labels.closeSidebarCandidates.any { desc.equals(it, ignoreCase = true) }) {
                    isSidebarOpen = true
                }
            }
        }
        if (!isSidebarOpen) return null

        // 사이드바 드로어 컨테이너가 식별된 경우 탐색 범위를 드로어 서브트리 내부로 엄격히 한정
        val searchNodes: List<AccessibilityNodeInfo> = if (drawerNode != null) {
            AccessibilityNodeTraversal.lazyTraverse(drawerNode, ::isGeminiPackage).toList()
        } else {
            nodeList
        }

        val scrollableBoundsList = searchNodes
            .filter { it.isScrollable }
            .mapNotNull { it.nodeBounds() }

        val candidateToNode = LinkedHashMap<GeminiSidebarProfileCandidate, AccessibilityNodeInfo>()
        val candidates = ArrayList<GeminiSidebarProfileCandidate>(searchNodes.size)

        for (node in searchNodes) {
            val isClickable = node.isClickable
            val isCheckable = node.isCheckable
            val isRadioButton = node.className?.toString()?.contains("RadioButton", ignoreCase = true) == true
            val bounds = if (isClickable || isCheckable || isRadioButton) node.nodeBounds() else null

            val isInsideScrollable = bounds != null && scrollableBoundsList.any { scrollBounds ->
                bounds.top >= scrollBounds.top - 4 && bounds.bottom <= scrollBounds.bottom + 4
            }

            val childTexts = mutableListOf<String>()
            val childDescs = mutableListOf<String>()
            if (isClickable || isCheckable) {
                for (child in AccessibilityNodeTraversal.lazyTraverse(node, ::isGeminiPackage)) {
                    if (child === node) continue
                    val t = child.text?.toString()?.trim()
                    if (!t.isNullOrEmpty()) childTexts.add(t)
                    val d = child.contentDescription?.toString()?.trim()
                    if (!d.isNullOrEmpty()) childDescs.add(d)
                }
            }

            val parentCandidate = if (isRadioButton) {
                val p = node.parent
                if (p != null) {
                    GeminiSidebarProfileCandidate(
                        className = p.className?.toString().orEmpty(),
                        viewIdResourceName = p.viewIdResourceName?.trim().orEmpty(),
                        contentDescription = p.contentDescription?.toString()?.trim().orEmpty(),
                        isClickable = p.isClickable,
                        isCheckable = p.isCheckable,
                        bounds = p.nodeBounds()
                    ).also { candidateToNode[it] = p }
                } else {
                    null
                }
            } else {
                null
            }

            val candidate = GeminiSidebarProfileCandidate(
                className = node.className?.toString().orEmpty(),
                viewIdResourceName = node.viewIdResourceName?.trim().orEmpty(),
                contentDescription = node.contentDescription?.toString()?.trim().orEmpty(),
                childTexts = childTexts,
                childDescriptions = childDescs,
                isClickable = isClickable,
                isCheckable = isCheckable,
                isInsideScrollable = isInsideScrollable,
                bounds = bounds,
                parent = parentCandidate
            )
            candidates.add(candidate)
            candidateToNode[candidate] = node
        }

        val matchedCandidate = GeminiSidebarProfileMatcher.findSidebarProfileCandidate(
            candidates = candidates,
            closeSidebarCandidates = labels.closeSidebarCandidates,
            sidebarDrawerContainerIds = labels.sidebarDrawerContainerIds,
            sidebarThreadItemIds = labels.sidebarThreadItemIds
        ) ?: return null

        return candidateToNode[matchedCandidate]
    }

    private fun findSidebarProfileTapPoint(profileNode: AccessibilityNodeInfo): Pair<Float, Float>? {
        val bounds = profileNode.nodeBounds() ?: return null
        val tapX = bounds.left + ((bounds.right - bounds.left) * 0.35f)
        val tapY = bounds.centerY.toFloat()
        return Pair(tapX, tapY)
    }

    private fun findOpenSidebarNode(nodes: Sequence<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        var toolbarNode: AccessibilityNodeInfo? = null
        for (node in nodes) {
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (desc.isNotBlank() && labels.openSidebarCandidates.any { desc.equals(it, ignoreCase = true) }) {
                return node
            }
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && labels.openSidebarCandidates.any { text.equals(it, ignoreCase = true) }) {
                return node
            }
            if (toolbarNode == null && node.viewIdResourceName != null && labels.toolbarViewIds.contains(node.viewIdResourceName)) {
                toolbarNode = node
            }
        }
        if (toolbarNode != null) {
            val toolbarBounds = toolbarNode.nodeBounds()
            if (toolbarBounds != null) {
                return AccessibilityNodeTraversal.lazyTraverse(toolbarNode).firstOrNull { node ->
                    val b = node.nodeBounds()
                    b != null && b.left < toolbarBounds.left + 250 && (node.isClickable || node.isFocusable)
                }
            }
        }
        return null
    }

    private fun findToolbarSidebarTapPoint(nodes: Sequence<AccessibilityNodeInfo>): Pair<Float, Float>? {
        val toolbarNode = nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.toolbarViewIds.contains(node.viewIdResourceName)
        } ?: return null
        val toolbarBounds = toolbarNode.nodeBounds() ?: return null

        val toolbarHeight = (toolbarBounds.bottom - toolbarBounds.top).toFloat()
        val tapX = toolbarBounds.left + (toolbarHeight / 2f)
        val tapY = (toolbarBounds.top + toolbarBounds.bottom) / 2f
        return tapX to tapY
    }

    private fun extractCurrentHeaderCandidate(nodes: Sequence<AccessibilityNodeInfo>): GeminiAccountNodeCandidate? {
        var primaryNode: AccessibilityNodeInfo? = null
        var secondaryNode: AccessibilityNodeInfo? = null
        var headerNode: AccessibilityNodeInfo? = null

        for (node in nodes) {
            val id = node.viewIdResourceName ?: continue
            if (primaryNode == null && labels.compactHeaderPrimaryTextIds.contains(id)) {
                primaryNode = node
            }
            if (secondaryNode == null && labels.compactHeaderSecondaryTextIds.contains(id)) {
                secondaryNode = node
            }
            if (headerNode == null && labels.accountHeaderContainerIds.contains(id)) {
                headerNode = node
            }
            if (primaryNode != null && secondaryNode != null && headerNode != null) {
                break
            }
        }
        if (primaryNode == null && secondaryNode == null && headerNode == null) return null

        return GeminiAccountNodeCandidate(
            primaryText = primaryNode?.text?.toString().orEmpty(),
            secondaryText = secondaryNode?.text?.toString().orEmpty(),
            contentDescription = headerNode?.contentDescription?.toString().orEmpty()
        )
    }

    private fun findAccountHeaderContainerNode(nodes: Sequence<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.accountHeaderContainerIds.contains(node.viewIdResourceName)
        }
    }

    private fun findExpandHeaderTapPoint(
        nodes: Sequence<AccessibilityNodeInfo>,
        headerContainer: AccessibilityNodeInfo?
    ): Pair<Float, Float>? {
        var chevronBounds: NodeBounds? = null
        var secondaryTextBounds: NodeBounds? = null

        for (node in nodes) {
            val id = node.viewIdResourceName ?: continue
            if (chevronBounds == null && labels.accountHeaderChevronIds.contains(id)) {
                chevronBounds = node.nodeBounds()
                if (chevronBounds != null) {
                    return chevronBounds.centerX.toFloat() to chevronBounds.centerY.toFloat()
                }
            }
            if (secondaryTextBounds == null && labels.compactHeaderSecondaryTextIds.contains(id)) {
                secondaryTextBounds = node.nodeBounds()
            }
        }

        val headerBounds = headerContainer?.nodeBounds() ?: secondaryTextBounds
        if (headerBounds != null) {
            return headerBounds.centerX.toFloat() to headerBounds.centerY.toFloat()
        }
        return null
    }

    private fun findAvailableAccountRows(
        nodes: Sequence<AccessibilityNodeInfo>
    ): List<Pair<AccessibilityNodeInfo, GeminiAccountNodeCandidate>> {
        val rowNodes = nodes.filter { node ->
            node.viewIdResourceName != null && labels.availableAccountRowIds.contains(node.viewIdResourceName)
        }.toList()
        return rowNodes.map { rowNode ->
            var primaryText: String? = null
            var secondaryText: String? = null
            val fallbackTexts = mutableListOf<String>()

            for (child in AccessibilityNodeTraversal.lazyTraverse(rowNode)) {
                val id = child.viewIdResourceName
                if (id != null) {
                    if (primaryText == null && labels.accountPrimaryInfoIds.contains(id)) {
                        primaryText = child.text?.toString()
                    }
                    if (secondaryText == null && labels.accountSecondaryInfoIds.contains(id)) {
                        secondaryText = child.text?.toString()
                    }
                }
                val t = child.text?.toString()?.trim()
                if (!t.isNullOrBlank()) {
                    fallbackTexts.add(t)
                }
            }

            val resolvedSecondary = secondaryText?.ifBlank { null }
                ?: fallbackTexts.firstOrNull { it.contains("@") }.orEmpty()
            val resolvedPrimary = primaryText?.ifBlank { null }
                ?: fallbackTexts.firstOrNull { !it.contains("@") }.orEmpty()

            rowNode to GeminiAccountNodeCandidate(
                primaryText = resolvedPrimary,
                secondaryText = resolvedSecondary,
                contentDescription = rowNode.contentDescription?.toString().orEmpty()
            )
        }
    }

    private fun findBentoScrollableNode(nodes: Sequence<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        var generalScrollable: AccessibilityNodeInfo? = null
        for (node in nodes) {
            if (!node.isScrollable) continue
            val id = node.viewIdResourceName
            if (id != null) {
                if (labels.bentoScrollContainerIds.contains(id) || labels.accountsListContainerIds.contains(id)) {
                    return node
                }
            }
            if (generalScrollable == null) {
                generalScrollable = node
            }
        }
        return generalScrollable
    }

    private fun findBentoCloseButtonNode(nodes: Sequence<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        var fallbackClose: AccessibilityNodeInfo? = null
        for (node in nodes) {
            val id = node.viewIdResourceName
            if (id != null && labels.bentoCloseButtonIds.contains(id)) {
                return node
            }
            if (fallbackClose == null) {
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (labels.bentoCloseButtonCandidates.any { desc.equals(it, ignoreCase = true) }) {
                    fallbackClose = node
                }
            }
        }
        return fallbackClose
    }

    private fun retryOrFail(failureMessage: String, retry: () -> Unit) {
        if (!active) return
        val elapsedMillis = SystemClock.uptimeMillis() - phaseStartedAtMillis
        val retryWaitMillis = AutomationRetryWaitPolicy.nextDelayMillis(elapsedMillis)
        if (retryWaitMillis == null) {
            finish(SwitchGeminiAccountResult.Failure(failureMessage))
        } else {
            handler.postDelayed(
                {
                    if (active) retry()
                },
                retryWaitMillis
            )
        }
    }

    private fun isGeminiPackage(packageName: CharSequence?): Boolean {
        val pkg = packageName?.toString() ?: return false
        return labels.geminiPackageNames.any { it.equals(pkg, ignoreCase = true) }
    }

    private fun allNodes(): Sequence<AccessibilityNodeInfo> {
        val roots = allRootsProvider().ifEmpty { listOfNotNull(rootProvider()) }
        val geminiRoots = roots.filter { isGeminiPackage(it.packageName) }
        val targetRoots = geminiRoots.ifEmpty { roots }
        return targetRoots.asSequence().flatMap { root ->
            AccessibilityNodeTraversal.lazyTraverse(root, ::isGeminiPackage)
        }
    }

    private fun finish(result: SwitchGeminiAccountResult) {
        if (!active) return
        active = false
        onFinished(result)
    }

    private companion object {
        const val MAX_ANCESTOR_SEARCH_DEPTH = 5
        const val MAX_ACCOUNT_LIST_SCROLLS = 4
        const val MAX_RETURN_BACKS = 2
        const val MIN_PROFILE_BUTTON_WIDTH_PX = 40
        const val MIN_VISIBLE_ROW_HEIGHT_PX = 24
        const val SIDEBAR_TAP_COOLDOWN_MS = 600L
        const val POST_SIDEBAR_OPEN_SETTLE_MS = 200L
        const val PROFILE_TAP_COOLDOWN_MS = 600L
        const val EXPAND_TAP_COOLDOWN_MS = 650L
        const val POST_TAP_SETTLE_MS = 200L
        const val POST_EXPAND_SETTLE_MS = 200L
        const val POST_SCROLL_SETTLE_MS = 200L
        const val POST_CLOSE_SETTLE_MS = 150L
        const val POST_RETURN_SETTLE_MS = 150L
        const val MIN_SWITCH_SETTLE_MS = 300L
        const val MAX_SWITCH_SETTLE_MS = 2200L
        const val SWITCH_POLL_INTERVAL_MS = 120L
    }
}
