// 역할: Gemini 앱에서 사이드바 또는 상단 메뉴를 통해 Google 프로필(OneGoogle Bento)을 열어 대상 구글 계정으로 자동 전환합니다.
package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.usecase.SwitchGeminiAccountResult
import com.example.gemgemgen.core.AppDefaults

internal data class GeminiAccountSwitchNodeLabels(
    val geminiPackageNames: List<String> = listOf(
        AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME,
        AppDefaults.GEMINI_PACKAGE_NAME
    ),
    val toolbarViewIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/assistant_robin_chat_compose_toolbar",
        "com.google.android.googlequicksearchbox:id/assistant_robin_chat_header_container"
    ),
    val profileButtonViewIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_apd_internal_image_view",
        "com.google.android.googlequicksearchbox:id/og_apd_ring_view",
        "com.google.android.googlequicksearchbox:id/account_particle_disc"
    ),
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
    val bentoMenuContainerIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_bento_menu_content_container",
        "com.google.android.googlequicksearchbox:id/og_bento_container"
    ),
    val bentoCloseButtonIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_bento_toolbar_close_button"
    ),
    val bentoCloseButtonCandidates: List<String> = listOf(
        "닫기",
        "Close"
    ),
    val accountHeaderContainerIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_bento_account_management_header_container",
        "com.google.android.googlequicksearchbox:id/og_compact_header",
        "com.google.android.googlequicksearchbox:id/og_bento_header_card_root"
    ),
    val accountHeaderChevronIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_compact_header_chevron_background",
        "com.google.android.googlequicksearchbox:id/og_collapsed_chevron"
    ),
    val compactHeaderPrimaryTextIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_compact_header_primary_text"
    ),
    val compactHeaderSecondaryTextIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_compact_header_secondary_text"
    ),
    val accountsListContainerIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/accounts"
    ),
    val bentoScrollContainerIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_bento_scroll_container"
    ),
    val availableAccountRowIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_bento_available_account_root"
    ),
    val accountPrimaryInfoIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_primary_account_information"
    ),
    val accountSecondaryInfoIds: List<String> = listOf(
        "com.google.android.googlequicksearchbox:id/og_secondary_account_information"
    )
)

internal data class GeminiAccountNodeCandidate(
    val primaryText: String,
    val secondaryText: String,
    val contentDescription: String = ""
)

internal object GeminiAccountMatcher {
    const val EXACT_MATCH_THRESHOLD = 900
    private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

    fun extractEmail(candidate: GeminiAccountNodeCandidate): String {
        val secondary = candidate.secondaryText.trim()
        if (secondary.contains("@")) return secondary
        EMAIL_REGEX.find(secondary)?.value?.let { return it }
        EMAIL_REGEX.find(candidate.contentDescription)?.value?.let { return it }
        EMAIL_REGEX.find(candidate.primaryText)?.value?.let { return it }
        return secondary
    }

    /**
     * 후보 계정이 사용자가 지정한 [targetEmail] 또는 [targetAlias]와 얼마나 일치하는지 점수를 계산합니다.
     * - 900점 이상: 이메일 전체 일치, 이메일 로컬 파트(@ 앞부분) 정확 일치, 또는 이메일 미지정 시 표시 이름 정확 일치
     * - 1~899점: 이메일 미지정 시 별칭 부분 일치 (유사 계정 중 가장 짧은 이메일 로컬 파트를 우선 선택)
     * - 0점 이하: 불일치
     */
    fun scoreCandidate(
        candidate: GeminiAccountNodeCandidate,
        targetEmail: String,
        targetAlias: String
    ): Int {
        val rawEmail = extractEmail(candidate).trim().lowercase()
        val localPart = rawEmail.substringBefore("@")
        val displayName = candidate.primaryText.trim().lowercase()
        val description = candidate.contentDescription.trim().lowercase()

        val normTargetEmail = targetEmail.trim().lowercase()
        val normTargetLocal = normTargetEmail.substringBefore("@")
        val normTargetAlias = targetAlias.trim().lowercase()

        if (normTargetEmail.isNotBlank()) {
            if (rawEmail.isNotBlank() && rawEmail == normTargetEmail) {
                return 1000
            }
            if (normTargetLocal.isNotBlank() && localPart.isNotBlank() && localPart == normTargetLocal) {
                return 950
            }
            if (!normTargetEmail.contains("@") && displayName.isNotBlank() && displayName == normTargetEmail) {
                return 900
            }
            return 0
        }

        if (normTargetAlias.isBlank()) return 0

        if (rawEmail.isNotBlank() && rawEmail == normTargetAlias) {
            return 1000
        }
        val aliasLocalQuery = normTargetAlias.substringBefore("@")
        if (aliasLocalQuery.isNotBlank() && localPart.isNotBlank() && localPart == aliasLocalQuery) {
            return 950
        }
        if (displayName.isNotBlank() && displayName == normTargetAlias) {
            return 900
        }
        if (aliasLocalQuery.isNotBlank() && localPart.isNotBlank() && localPart.contains(aliasLocalQuery)) {
            return (700 - localPart.length).coerceAtLeast(1)
        }
        if (displayName.isNotBlank() && displayName.contains(normTargetAlias)) {
            return (500 - displayName.length).coerceAtLeast(1)
        }
        if (description.isNotBlank() && description.contains(normTargetAlias)) {
            return 100
        }
        return 0
    }

    fun isExactMatch(
        candidate: GeminiAccountNodeCandidate,
        targetEmail: String,
        targetAlias: String
    ): Boolean = scoreCandidate(candidate, targetEmail, targetAlias) >= EXACT_MATCH_THRESHOLD
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
                val nodes = allNodes().toList()
                if (isGeminiForeground() || isGeminiScreenVisible(nodes)) {
                    enter(Phase.OPEN_PROFILE_MENU)
                } else {
                    retryOrFail("Gemini 화면을 열지 못했습니다.") { checkPhase() }
                }
            }

            Phase.OPEN_PROFILE_MENU -> {
                val nodes = allNodes().toList()
                if (isBentoMenuVisible(nodes)) {
                    enter(Phase.CHECK_OR_EXPAND_ACCOUNTS)
                    return
                }

                // 1순위: 사이드바가 이미 열려 있는 상태라면 사이드바 하단 프로필 카드 클릭
                val sidebarProfileNode = findSidebarProfileNode(nodes)
                if (sidebarProfileNode != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastProfileTapAtMillis >= PROFILE_TAP_COOLDOWN_MS) {
                        lastProfileTapAtMillis = now
                        val clicked = AccessibilityNodeTraversal.clickNodeOrParent(
                            sidebarProfileNode,
                            maxDepth = MAX_ANCESTOR_SEARCH_DEPTH
                        )
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

                // 2순위: 사이드바가 닫혀 있는 상태라면 좌측 상단 '사이드바 열기' 클릭
                val openSidebarNode = findOpenSidebarNode(nodes)
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
                val toolbarSidebarTapPoint = findToolbarSidebarTapPoint(nodes)
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
                val explicitProfileNode = findExplicitProfileButtonNode(nodes)
                if (explicitProfileNode != null) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastProfileTapAtMillis >= PROFILE_TAP_COOLDOWN_MS) {
                        lastProfileTapAtMillis = now
                        AccessibilityNodeTraversal.clickNodeOrParent(
                            explicitProfileNode,
                            maxDepth = MAX_ANCESTOR_SEARCH_DEPTH
                        )
                    }
                    retryOrFail("Google 프로필 메뉴를 열지 못했습니다.") { checkPhase() }
                    return
                }

                val tapPoint = findToolbarProfileTapPoint(nodes)
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
                val nodes = allNodes().toList()
                val headerCandidate = extractCurrentHeaderCandidate(nodes)
                if (headerCandidate != null &&
                    GeminiAccountMatcher.isExactMatch(headerCandidate, targetEmail, targetAlias)
                ) {
                    enter(Phase.CLOSE_MENU_AND_FINISH)
                    return
                }

                val accountRows = findAvailableAccountRows(nodes)
                if (accountRows.isNotEmpty() || isAccountListExpanded(nodes)) {
                    enter(Phase.SELECT_TARGET_ACCOUNT)
                    return
                }

                val headerContainer = findAccountHeaderContainerNode(nodes)
                val expandTapPoint = findExpandHeaderTapPoint(nodes, headerContainer)
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
                val nodes = allNodes().toList()
                val headerCandidate = extractCurrentHeaderCandidate(nodes)
                if (headerCandidate != null &&
                    GeminiAccountMatcher.isExactMatch(headerCandidate, targetEmail, targetAlias)
                ) {
                    enter(Phase.CLOSE_MENU_AND_FINISH)
                    return
                }

                val scoredRows = findAvailableAccountRows(nodes)
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

                val scrollableNode = findBentoScrollableNode(nodes)
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
                val nodes = allNodes().toList()
                val elapsed = SystemClock.uptimeMillis() - phaseStartedAtMillis
                if (!isBentoMenuVisible(nodes) && elapsed >= MIN_SWITCH_SETTLE_MS) {
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
                val nodes = allNodes().toList()
                if (!isBentoMenuVisible(nodes)) {
                    completeAndReturn()
                    return
                }
                val closeBtn = findBentoCloseButtonNode(nodes)
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

    private fun isGeminiScreenVisible(nodes: List<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val pkg = node.packageName?.toString()
            pkg != null && labels.geminiPackageNames.any { it.equals(pkg, ignoreCase = true) }
        }
    }

    private fun isBentoMenuVisible(nodes: List<AccessibilityNodeInfo>): Boolean {
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

    private fun isAccountListExpanded(nodes: List<AccessibilityNodeInfo>): Boolean {
        if (nodes.any { node ->
                val id = node.viewIdResourceName ?: return@any false
                labels.accountsListContainerIds.contains(id) || labels.availableAccountRowIds.contains(id)
            }
        ) {
            return true
        }
        val compactHeader = nodes.firstOrNull { it.viewIdResourceName in labels.accountHeaderContainerIds }
        val desc = compactHeader?.contentDescription?.toString().orEmpty()
        return desc.contains("계정 목록을 펼쳤습니다") || desc.contains("계정 목록을 접습니다")
    }

    private fun findExplicitProfileButtonNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val viewId = node.viewIdResourceName
            if (viewId != null && labels.profileButtonViewIds.contains(viewId)) {
                return node
            }
        }
        for (node in nodes) {
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (desc.isNotBlank() &&
                labels.profileButtonDescriptions.any { desc.contains(it, ignoreCase = true) } &&
                node.viewIdResourceName !in labels.accountHeaderContainerIds
            ) {
                return node
            }
        }
        return null
    }

    private fun findToolbarProfileTapPoint(nodes: List<AccessibilityNodeInfo>): Pair<Float, Float>? {
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
        val toolbarSubNodes = AccessibilityNodeTraversal.lazyTraverse(toolbarNode).toList()
        val rightContainerBounds = toolbarSubNodes
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

    private fun findSidebarProfileNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val radioButtonNode = nodes.firstOrNull { node ->
            node.className?.contains("RadioButton", ignoreCase = true) == true
        }
        if (radioButtonNode != null) {
            val parent = radioButtonNode.parent
            if (parent != null && (parent.isClickable || parent.isCheckable)) {
                return parent
            }
            return radioButtonNode
        }

        val isSidebarOpen = nodes.any { node ->
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            labels.closeSidebarCandidates.any { desc.equals(it, ignoreCase = true) }
        }
        if (!isSidebarOpen) return null

        val profileIndicators = listOf(
            "pro",
            "google 계정",
            "google account",
            "signed in",
            targetEmail.lowercase(),
            targetAlias.lowercase()
        ).filter { it.isNotBlank() }

        val clickableCandidates = nodes.filter { node ->
            val isClickableOrCheckable = node.isClickable || node.isCheckable
            val b = node.nodeBounds()
            isClickableOrCheckable && b != null && (b.bottom - b.top) in 50..300
        }

        for (node in clickableCandidates) {
            val subNodes = AccessibilityNodeTraversal.lazyTraverse(node).toList()
            val hasIndicator = subNodes.any { child ->
                val text = child.text?.toString()?.lowercase().orEmpty()
                val desc = child.contentDescription?.toString()?.lowercase().orEmpty()
                profileIndicators.any { ind -> text.contains(ind) || desc.contains(ind) }
            }
            if (hasIndicator) {
                return node
            }
        }

        val bottomNode = clickableCandidates.maxByOrNull { it.nodeBounds()?.bottom ?: 0 }
        if (bottomNode != null) {
            val b = bottomNode.nodeBounds()
            if (b != null && (b.right - b.left) > 250) {
                return bottomNode
            }
        }

        return null
    }

    private fun findSidebarProfileTapPoint(profileNode: AccessibilityNodeInfo): Pair<Float, Float>? {
        val bounds = profileNode.nodeBounds() ?: return null
        val tapX = bounds.left + ((bounds.right - bounds.left) * 0.35f)
        val tapY = bounds.centerY.toFloat()
        return Pair(tapX, tapY)
    }

    private fun findOpenSidebarNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (node in nodes) {
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (desc.isNotBlank() && labels.openSidebarCandidates.any { desc.equals(it, ignoreCase = true) }) {
                return node
            }
        }
        for (node in nodes) {
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && labels.openSidebarCandidates.any { text.equals(it, ignoreCase = true) }) {
                return node
            }
        }
        val toolbarNode = nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.toolbarViewIds.contains(node.viewIdResourceName)
        }
        if (toolbarNode != null) {
            val toolbarBounds = toolbarNode.nodeBounds()
            if (toolbarBounds != null) {
                val leftNodes = AccessibilityNodeTraversal.lazyTraverse(toolbarNode)
                    .filter { node ->
                        val b = node.nodeBounds()
                        b != null && b.left < toolbarBounds.left + 250 && (node.isClickable || node.isFocusable)
                    }
                    .toList()
                leftNodes.firstOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun findToolbarSidebarTapPoint(nodes: List<AccessibilityNodeInfo>): Pair<Float, Float>? {
        val toolbarNode = nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.toolbarViewIds.contains(node.viewIdResourceName)
        } ?: return null
        val toolbarBounds = toolbarNode.nodeBounds() ?: return null

        val toolbarHeight = (toolbarBounds.bottom - toolbarBounds.top).toFloat()
        val tapX = toolbarBounds.left + (toolbarHeight / 2f)
        val tapY = (toolbarBounds.top + toolbarBounds.bottom) / 2f
        return tapX to tapY
    }

    private fun findCloseSidebarNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            labels.closeSidebarCandidates.any { desc.equals(it, ignoreCase = true) }
        }
    }

    private fun extractCurrentHeaderCandidate(nodes: List<AccessibilityNodeInfo>): GeminiAccountNodeCandidate? {
        val primaryNode = nodes.firstOrNull {
            it.viewIdResourceName != null && labels.compactHeaderPrimaryTextIds.contains(it.viewIdResourceName)
        }
        val secondaryNode = nodes.firstOrNull {
            it.viewIdResourceName != null && labels.compactHeaderSecondaryTextIds.contains(it.viewIdResourceName)
        }
        val headerNode = nodes.firstOrNull {
            it.viewIdResourceName != null && labels.accountHeaderContainerIds.contains(it.viewIdResourceName)
        }
        if (primaryNode == null && secondaryNode == null && headerNode == null) return null

        return GeminiAccountNodeCandidate(
            primaryText = primaryNode?.text?.toString().orEmpty(),
            secondaryText = secondaryNode?.text?.toString().orEmpty(),
            contentDescription = headerNode?.contentDescription?.toString().orEmpty()
        )
    }

    private fun findAccountHeaderContainerNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (id in labels.accountHeaderContainerIds) {
            nodes.firstOrNull { it.viewIdResourceName == id }?.let { return it }
        }
        return null
    }

    private fun findExpandHeaderTapPoint(
        nodes: List<AccessibilityNodeInfo>,
        headerContainer: AccessibilityNodeInfo?
    ): Pair<Float, Float>? {
        for (id in labels.accountHeaderChevronIds) {
            val chevronBounds = nodes.firstOrNull { it.viewIdResourceName == id }?.nodeBounds()
            if (chevronBounds != null) {
                return chevronBounds.centerX.toFloat() to chevronBounds.centerY.toFloat()
            }
        }
        val headerBounds = headerContainer?.nodeBounds()
            ?: nodes.firstOrNull { it.viewIdResourceName in labels.compactHeaderSecondaryTextIds }?.nodeBounds()
        if (headerBounds != null) {
            return headerBounds.centerX.toFloat() to headerBounds.centerY.toFloat()
        }
        return null
    }

    private fun findAvailableAccountRows(
        nodes: List<AccessibilityNodeInfo>
    ): List<Pair<AccessibilityNodeInfo, GeminiAccountNodeCandidate>> {
        val rowNodes = nodes.filter { node ->
            node.viewIdResourceName != null && labels.availableAccountRowIds.contains(node.viewIdResourceName)
        }
        return rowNodes.map { rowNode ->
            val subNodes = AccessibilityNodeTraversal.lazyTraverse(rowNode).toList()
            val primaryText = subNodes.firstOrNull {
                it.viewIdResourceName != null && labels.accountPrimaryInfoIds.contains(it.viewIdResourceName)
            }?.text?.toString().orEmpty()
            val secondaryText = subNodes.firstOrNull {
                it.viewIdResourceName != null && labels.accountSecondaryInfoIds.contains(it.viewIdResourceName)
            }?.text?.toString().orEmpty()
            val fallbackTexts = if (primaryText.isBlank() && secondaryText.isBlank()) {
                subNodes.mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotBlank) }
            } else {
                emptyList()
            }
            val resolvedSecondary = secondaryText.ifBlank {
                fallbackTexts.firstOrNull { it.contains("@") }.orEmpty()
            }
            val resolvedPrimary = primaryText.ifBlank {
                fallbackTexts.firstOrNull { !it.contains("@") }.orEmpty()
            }
            rowNode to GeminiAccountNodeCandidate(
                primaryText = resolvedPrimary,
                secondaryText = resolvedSecondary,
                contentDescription = rowNode.contentDescription?.toString().orEmpty()
            )
        }
    }

    private fun findBentoScrollableNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (id in labels.bentoScrollContainerIds) {
            nodes.firstOrNull { it.viewIdResourceName == id && it.isScrollable }?.let { return it }
        }
        for (id in labels.accountsListContainerIds) {
            nodes.firstOrNull { it.viewIdResourceName == id && it.isScrollable }?.let { return it }
        }
        return nodes.firstOrNull { it.isScrollable }
    }

    private fun findBentoCloseButtonNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (id in labels.bentoCloseButtonIds) {
            nodes.firstOrNull { it.viewIdResourceName == id }?.let { return it }
        }
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            labels.bentoCloseButtonCandidates.any { desc.equals(it, ignoreCase = true) }
        }
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

    private fun allNodes(): Sequence<AccessibilityNodeInfo> {
        val roots = allRootsProvider().ifEmpty { listOfNotNull(rootProvider()) }
        return roots.asSequence().flatMap { root -> AccessibilityNodeTraversal.lazyTraverse(root) }
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
        const val POST_SIDEBAR_OPEN_SETTLE_MS = 450L
        const val PROFILE_TAP_COOLDOWN_MS = 600L
        const val EXPAND_TAP_COOLDOWN_MS = 650L
        const val POST_TAP_SETTLE_MS = 500L
        const val POST_EXPAND_SETTLE_MS = 480L
        const val POST_SCROLL_SETTLE_MS = 400L
        const val POST_CLOSE_SETTLE_MS = 280L
        const val POST_RETURN_SETTLE_MS = 320L
        const val MIN_SWITCH_SETTLE_MS = 700L
        const val MAX_SWITCH_SETTLE_MS = 2200L
        const val SWITCH_POLL_INTERVAL_MS = 250L
    }
}
