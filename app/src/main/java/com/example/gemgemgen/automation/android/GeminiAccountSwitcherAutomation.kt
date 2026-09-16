// 역할: Gemini 앱에서 접근성 노드를 탐색하고 조작하여 프로필 열기 -> 계정 목록 열기 -> 대상 계정 탐색(필요시 스크롤) -> 터치 전환 및 우리 앱 복귀를 자동 수행합니다.
package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.GeminiAccountSwitchProgressPolicy
import com.example.gemgemgen.core.AppDefaults

sealed interface GeminiAccountSwitchResult {
    data class Success(val message: String) : GeminiAccountSwitchResult
    data class Failure(val message: String) : GeminiAccountSwitchResult
    data object Unavailable : GeminiAccountSwitchResult
}

internal class GeminiAccountSwitcherAutomation(
    private val handler: Handler,
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val allRootsProvider: () -> List<AccessibilityNodeInfo> = { listOfNotNull(rootProvider()) },
    private val activePackageProvider: () -> String?,
    private val launchGemini: () -> Boolean,
    private val targetIdentifier: String,
    private val targetAlias: String,
    private val tapAtCoordinates: ((Float, Float) -> Boolean)? = null,
    private val onProgress: ((phase: String, message: String) -> Unit)? = null,
    private val bringAppToForeground: (() -> Unit)? = null,
    private val onFinished: (GeminiAccountSwitchResult) -> Unit
) {
    private enum class Phase {
        ENSURE_GEMINI_FOREGROUND,
        OPEN_PROFILE,
        EXPAND_ACCOUNTS,
        FIND_ACCOUNT_AND_SCROLL,
        WAIT_FOR_DISMISS_AND_NEW_CHAT,
        FINISHED
    }

    private var active = false
    private var phase = Phase.ENSURE_GEMINI_FOREGROUND
    private var phaseStartedAtMillis = 0L
    private var scrollAttempts = 0
    private var sidebarOpened = false
    private var lastProfileClickMillis = 0L
    private var lastExpandClickMillis = 0L

    private fun reportProgress(phaseLabel: String, message: String) {
        Log.i(TAG, "[$phaseLabel] $message")
        onProgress?.invoke(phaseLabel, message)
    }

    fun start() {
        if (active) return
        active = true
        Log.i(TAG, "Gemini account switch started: targetId=$targetIdentifier, targetAlias=$targetAlias")
        phase = Phase.ENSURE_GEMINI_FOREGROUND
        phaseStartedAtMillis = SystemClock.uptimeMillis()
        scrollAttempts = 0
        sidebarOpened = false
        lastProfileClickMillis = 0L
        lastExpandClickMillis = 0L
        reportProgress(
            GeminiAccountSwitchProgressPolicy.PHASE_1,
            GeminiAccountSwitchProgressPolicy.step1CheckForeground()
        )
        handler.post(::step)
    }

    fun cancel() {
        if (!active) return
        Log.i(TAG, "Gemini account switch cancelled")
        active = false
        phase = Phase.FINISHED
        handler.removeCallbacks(::step)
    }

    private fun step() {
        if (!active) return

        when (phase) {
            Phase.ENSURE_GEMINI_FOREGROUND -> handleEnsureGeminiForeground()
            Phase.OPEN_PROFILE -> handleOpenProfile()
            Phase.EXPAND_ACCOUNTS -> handleExpandAccounts()
            Phase.FIND_ACCOUNT_AND_SCROLL -> handleFindAccountAndScroll()
            Phase.WAIT_FOR_DISMISS_AND_NEW_CHAT -> handleWaitForDismissAndNewChat()
            Phase.FINISHED -> Unit
        }
    }

    private fun handleEnsureGeminiForeground() {
        val activePkg = activePackageProvider()
        val isGeminiInForeground = activePkg == AppDefaults.GEMINI_PACKAGE_NAME ||
            activePkg == AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME

        if (isGeminiInForeground) {
            reportProgress(
                GeminiAccountSwitchProgressPolicy.PHASE_2,
                GeminiAccountSwitchProgressPolicy.step2OpenProfile()
            )
            phase = Phase.OPEN_PROFILE
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 300L)
            return
        }

        if (SystemClock.uptimeMillis() - phaseStartedAtMillis > TIMEOUT_LAUNCH_GEMINI_MS) {
            finishWith(GeminiAccountSwitchResult.Failure("Gemini 앱이 전면에 실행되지 않았습니다."))
            return
        }

        reportProgress(
            GeminiAccountSwitchProgressPolicy.PHASE_1,
            GeminiAccountSwitchProgressPolicy.step1LaunchGemini()
        )
        launchGemini()
        handler.postDelayed(::step, 600L)
    }

    private fun getAllNodes(): List<AccessibilityNodeInfo> {
        val roots = (allRootsProvider() + listOfNotNull(rootProvider())).distinct()
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        for ((idx, r) in roots.withIndex()) {
            if (r.childCount == 0) {
                runCatching { r.refresh() }
            }
            val flat = flattenNodes(r)
            Log.d(TAG, "root[$idx]: pkg=${r.packageName}, windowId=${r.windowId}, flatCount=${flat.size}")
            allNodes += flat

            val directQueryIds = listOf(
                "com.google.android.googlequicksearchbox:id/og_compact_header",
                "com.google.android.googlequicksearchbox:id/og_bento_toolbar_close_button",
                "com.google.android.googlequicksearchbox:id/og_collapsed_chevron",
                "com.google.android.googlequicksearchbox:id/og_bento_account_management_header_container",
                "com.google.android.googlequicksearchbox:id/accounts",
                "com.google.android.googlequicksearchbox:id/og_bento_available_account_root",
                "com.google.android.googlequicksearchbox:id/og_secondary_account_information",
                "com.google.android.googlequicksearchbox:id/og_primary_account_information",
                "com.google.android.googlequicksearchbox:id/assistant_robin_side_nav_profile"
            )
            for (viewId in directQueryIds) {
                val directNodes = runCatching { r.findAccessibilityNodeInfosByViewId(viewId) }.getOrNull().orEmpty()
                for (dn in directNodes) {
                    allNodes += flattenNodes(dn)
                }
            }
        }
        val distinctNodes = allNodes.distinct()
        for (n in distinctNodes) {
            Log.d(TAG, "  node: [${n.packageName}] id=${n.viewIdResourceName}, text=${n.text}, desc=${n.contentDescription}")
        }
        val bentoVis = isAccountBentoDialogVisible(distinctNodes)
        val sbOpen = isSidebarOpen(distinctNodes)
        Log.i(TAG, "getAllNodes: rootsCount=${roots.size}, allNodesCount=${distinctNodes.size}, bento=$bentoVis, sbOpen=$sbOpen")
        return distinctNodes
    }

    private fun handleOpenProfile() {
        val nodes = getAllNodes()
        val bentoVis = isAccountBentoDialogVisible(nodes)
        val sbOpen = isSidebarOpen(nodes)
        Log.i(TAG, "handleOpenProfile: nodes=${nodes.size}, bentoVisible=$bentoVis, sidebarOpen=$sbOpen")
        if (nodes.isEmpty()) {
            retryOrFail(TIMEOUT_OPEN_PROFILE_MS, "화면 노드를 읽을 수 없습니다.")
            return
        }

        // 0. 이미 계정 목록이 펼쳐져 있다면 바로 계정 탐색 단계로 이동
        if (areAccountsExpanded(nodes)) {
            Log.i(TAG, "handleOpenProfile: Accounts list already expanded, moving to FIND_ACCOUNT_AND_SCROLL")
            reportProgress(
                GeminiAccountSwitchProgressPolicy.PHASE_4,
                GeminiAccountSwitchProgressPolicy.step4FindAccount(targetAlias.ifBlank { targetIdentifier })
            )
            phase = Phase.FIND_ACCOUNT_AND_SCROLL
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 200L)
            return
        }

        // 1. 이미 구글 계정 Bento 다이얼로그가 열려 있는 경우 바로 계정 목록 확장 단계로 이동
        if (isAccountBentoDialogVisible(nodes)) {
            Log.i(TAG, "handleOpenProfile: Bento dialog visible, moving to EXPAND_ACCOUNTS")
            reportProgress(
                GeminiAccountSwitchProgressPolicy.PHASE_3,
                GeminiAccountSwitchProgressPolicy.step3ExpandAccounts()
            )
            phase = Phase.EXPAND_ACCOUNTS
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 200L)
            return
        }

        // 프로필 클릭 후 다이얼로그 표시 대기 쿨다운 (최소 1200ms)
        val now = SystemClock.uptimeMillis()
        if (now - lastProfileClickMillis < 1200L) {
            handler.postDelayed(::step, POLL_INTERVAL_MS)
            return
        }

        // 2. 상단 툴바 프로필 아이콘 탐색 (태블릿 가로 모드 등)
        val toolbarProfileNode = findToolbarProfileNode(nodes)
        if (toolbarProfileNode != null) {
            Log.i(TAG, "handleOpenProfile: clicking toolbar profile node: ${toolbarProfileNode.viewIdResourceName}")
            lastProfileClickMillis = now
            if (clickNodeOrParent(toolbarProfileNode)) {
                handler.postDelayed(::step, 600L)
                return
            }
        }

        // 3. 사이드바가 이미 열려 있는 경우 -> 사이드바 내 프로필 노드 클릭
        if (isSidebarOpen(nodes)) {
            val sidebarProfileNode = findSidebarProfileNode(nodes)
            if (sidebarProfileNode != null) {
                Log.i(TAG, "handleOpenProfile: clicking sidebar profile node: ${sidebarProfileNode.viewIdResourceName}")
                lastProfileClickMillis = now
                if (clickNodeOrParent(sidebarProfileNode)) {
                    handler.postDelayed(::step, 600L)
                    return
                }
            }
        } else {
            // 4. 사이드바가 닫혀있다면 '사이드바 열기' 먼저 클릭
            val openSidebarNode = findOpenSidebarNode(nodes)
            if (openSidebarNode != null) {
                Log.i(TAG, "handleOpenProfile: opening sidebar first")
                if (clickNodeOrParent(openSidebarNode)) {
                    sidebarOpened = true
                    handler.postDelayed(::step, 600L)
                    return
                }
            }
        }

        retryOrFail(TIMEOUT_OPEN_PROFILE_MS, "Gemini 프로필 아이콘을 찾을 수 없습니다.")
    }

    private fun handleExpandAccounts() {
        val nodes = getAllNodes()
        if (nodes.size <= 1) {
            retryOrFail(TIMEOUT_EXPAND_ACCOUNTS_MS, "화면 노드를 읽을 수 없습니다.")
            return
        }

        // 현재 활성 계정이 이미 목표 계정과 같은지 확인
        if (isTargetAccountAlreadyActive(nodes)) {
            Log.i(TAG, "Target account is already active: $targetIdentifier")
            bringAppToForeground?.invoke()
            finishWith(
                GeminiAccountSwitchResult.Success(
                    "이미 [${targetAlias.ifBlank { targetIdentifier }}] 계정으로 로그인되어 있습니다."
                )
            )
            return
        }

        // 계정 목록 RecyclerView 가 이미 펼쳐져 있는지 확인
        if (areAccountsExpanded(nodes)) {
            Log.i(TAG, "Accounts list is already expanded, proceeding to find account")
            reportProgress(
                GeminiAccountSwitchProgressPolicy.PHASE_4,
                GeminiAccountSwitchProgressPolicy.step4FindAccount(targetAlias.ifBlank { targetIdentifier })
            )
            phase = Phase.FIND_ACCOUNT_AND_SCROLL
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 200L)
            return
        }

        // 펼치기 클릭 후 쿨다운 대기 (애니메이션 대기)
        val now = SystemClock.uptimeMillis()
        if (now - lastExpandClickMillis < 1200L) {
            handler.postDelayed(::step, POLL_INTERVAL_MS)
            return
        }

        val chevronNode = findChevronExpandNode(nodes)
        if (chevronNode != null) {
            Log.i(TAG, "handleExpandAccounts: clicking chevron node: ${chevronNode.viewIdResourceName}")
            lastExpandClickMillis = now
            if (clickNodeOrParent(chevronNode)) {
                handler.postDelayed(::step, 600L)
                return
            }
        }

        // 헤더 자체 클릭 시도 (Fallback)
        val headerNode = nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_compact_header") == true ||
                node.viewIdResourceName?.contains("account_management_expandable_content") == true
        }
        if (headerNode != null) {
            Log.i(TAG, "handleExpandAccounts: clicking header fallback node: ${headerNode.viewIdResourceName}")
            lastExpandClickMillis = now
            if (clickNodeOrParent(headerNode)) {
                handler.postDelayed(::step, 600L)
                return
            }
        }

        retryOrFail(TIMEOUT_EXPAND_ACCOUNTS_MS, "계정 목록 펼치기 버튼을 찾을 수 없습니다.")
    }

    private fun handleFindAccountAndScroll() {
        val nodes = getAllNodes()
        if (nodes.size <= 1) {
            retryOrFail(TIMEOUT_FIND_ACCOUNT_MS, "화면 노드를 읽을 수 없습니다.")
            return
        }

        // 1. 대상 계정 노드 탐색 (이메일 1순위, 별칭 2순위)
        val accountNode = findMatchingAccountNode(nodes)
        if (accountNode != null) {
            Log.i(TAG, "handleFindAccountAndScroll: Target account found! Clicking: ${accountNode.viewIdResourceName}")
            reportProgress(
                GeminiAccountSwitchProgressPolicy.PHASE_5,
                GeminiAccountSwitchProgressPolicy.step5SwitchingAccount(targetAlias.ifBlank { targetIdentifier })
            )
            val clicked = clickNodeOrParent(accountNode)
            if (clicked) {
                phase = Phase.WAIT_FOR_DISMISS_AND_NEW_CHAT
                phaseStartedAtMillis = SystemClock.uptimeMillis()
                handler.postDelayed(::step, 800L)
                return
            }
        }

        // 2. 현재 보이는 화면에 없으면 계정 목록 스크롤 시도 (최대 4회)
        if (scrollAttempts < MAX_SCROLL_ATTEMPTS) {
            scrollAttempts++
            Log.i(TAG, "handleFindAccountAndScroll: Account not in view, scrolling list (attempt $scrollAttempts/$MAX_SCROLL_ATTEMPTS)")
            reportProgress(
                GeminiAccountSwitchProgressPolicy.PHASE_4,
                GeminiAccountSwitchProgressPolicy.step4ScrollAccounts(scrollAttempts, MAX_SCROLL_ATTEMPTS)
            )
            val scrolled = scrollAccountsList(nodes)
            if (scrolled) {
                handler.postDelayed(::step, 700L)
                return
            }
        }

        retryOrFail(
            TIMEOUT_FIND_ACCOUNT_MS,
            "기기에서 [${targetAlias.ifBlank { targetIdentifier }}] 계정을 찾을 수 없습니다."
        )
    }

    private fun handleWaitForDismissAndNewChat() {
        val nodes = getAllNodes()

        // 1. Bento 다이얼로그가 아직 떠 있는지 확인 (닫히는 중)
        val bentoStillVisible = isAccountBentoDialogVisible(nodes)
        if (bentoStillVisible) {
            val elapsed = SystemClock.uptimeMillis() - phaseStartedAtMillis
            if (elapsed < TIMEOUT_DISMISS_MS) {
                Log.d(TAG, "handleWaitForDismissAndNewChat: Bento dialog still visible, waiting...")
                handler.postDelayed(::step, POLL_INTERVAL_MS)
                return
            }
        }

        // 2. 다이얼로그가 닫혔거나 타임아웃 경과 -> 계정 전환 성공 완료 처리!
        Log.i(TAG, "handleWaitForDismissAndNewChat: Account switched successfully. Returning to gemgemgen app.")
        bringAppToForeground?.invoke()
        finishWith(
            GeminiAccountSwitchResult.Success(
                "Gemini 계정을 [${targetAlias.ifBlank { targetIdentifier }}]로 전환했습니다."
            )
        )
    }

    // --- 노드 탐색 헬퍼 함수들 (100% 접근성 노드 시맨틱 탐색, 좌표 0%) ---

    private fun isAccountBentoDialogVisible(nodes: List<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val id = node.viewIdResourceName ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            id.contains("og_bento_toolbar_close_button") ||
                id.contains("og_compact_header") ||
                id.contains("account_management_card") ||
                desc.contains("계정 목록을 펼칩니다") ||
                desc.contains("계정 목록을 접었습니다")
        }
    }

    private fun areAccountsExpanded(nodes: List<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val id = node.viewIdResourceName ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            id.endsWith(":id/accounts") ||
                id.contains("og_bento_available_account_root") ||
                desc.contains("계정 목록을 접습니다")
        }
    }

    private fun isTargetAccountAlreadyActive(nodes: List<AccessibilityNodeInfo>): Boolean {
        val targetId = targetIdentifier.trim().lowercase()
        val targetAl = targetAlias.trim().lowercase()

        val headerNodes = nodes.filter { node ->
            node.viewIdResourceName?.contains("og_compact_header") == true
        }

        for (node in headerNodes) {
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (targetId.isNotBlank() && (text.contains(targetId) || desc.contains(targetId))) {
                return true
            }
            if (targetAl.isNotBlank() && !targetAl.startsWith("서브") && (text.contains(targetAl) || desc.contains(targetAl))) {
                return true
            }
        }
        return false
    }

    private fun findToolbarProfileNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        // 상단 툴바 내 아바타 / 프로필 아이콘 노드
        return nodes.firstOrNull { node ->
            val id = node.viewIdResourceName ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            (id.contains("avatar") || id.contains("profile") || id.contains("account_button")) ||
                desc.contains("Google 계정") || desc.contains("계정 및 프로필")
        }
    }

    private fun isSidebarOpen(nodes: List<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val desc = node.contentDescription?.toString() ?: ""
            desc == "사이드바 닫기" || desc.contains("사이드바 닫기")
        } || nodes.any { node ->
            node.text?.toString() == "채팅 검색"
        }
    }

    private fun findSidebarProfileNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        // 사이드바 하단 프로필 노드 (RadioButton 또는 사용자 이름/PRO 텍스트가 포함된 클릭 가능 View)
        val profileCandidate = nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            node.className?.toString()?.contains("RadioButton") == true ||
                desc.contains("계정") || desc.contains("프로필")
        } ?: nodes.firstOrNull { node ->
            val id = node.viewIdResourceName ?: ""
            id.contains("assistant_robin_side_nav_profile") || id.contains("user_profile")
        } ?: nodes.firstOrNull { node ->
            node.text?.toString() == "PRO" || node.text?.toString() == "Pro"
        }

        if (profileCandidate != null) {
            return findClickableAncestor(profileCandidate) ?: profileCandidate
        }

        // 사이드바가 열려있을 때 하단에 위치한 클릭 가능 컨테이너 탐색
        return nodes.filter { it.isClickable }.lastOrNull { node ->
            val id = node.viewIdResourceName ?: ""
            !id.contains("thread_item") && !id.contains("close") && !id.contains("사이드바")
        }
    }

    private fun findOpenSidebarNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            desc == "사이드바 열기" || desc == "탐색 창 열기" || desc == "메뉴"
        } ?: nodes.firstOrNull { node ->
            val id = node.viewIdResourceName ?: ""
            id.contains("menu_button") || id.contains("navigation_drawer")
        }
    }

    private fun findChevronExpandNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            val id = node.viewIdResourceName ?: ""
            id.endsWith(":id/og_collapsed_chevron") ||
                id.contains("og_compact_header_chevron_background") ||
                id.contains("og_bento_account_management_header_container")
        } ?: nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            desc.contains("계정 목록을 펼칩니다") || desc.contains("펼칩니다")
        }
    }

    private fun findMatchingAccountNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val targetId = targetIdentifier.trim().lowercase()
        val targetAl = targetAlias.trim().lowercase()

        // 1순위: 이메일(식별자) 일치 검사
        if (targetId.isNotBlank()) {
            val emailNode = nodes.firstOrNull { node ->
                val id = node.viewIdResourceName ?: ""
                val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                (id.contains("og_secondary_account_information") || id.contains("account")) &&
                    text.contains(targetId)
            } ?: nodes.firstOrNull { node ->
                val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                text.contains(targetId) || desc.contains(targetId)
            }

            if (emailNode != null) {
                val clickableRoot = findClickableAccountRoot(emailNode)
                Log.i(TAG, "Found target account by email: $targetId, node=${clickableRoot.viewIdResourceName}")
                return clickableRoot
            }
        }

        // 2순위: 별칭(DisplayName) 일치 검사 (기본 별칭 '서브' 제외)
        if (targetAl.isNotBlank() && !targetAl.startsWith("서브")) {
            val aliasNode = nodes.firstOrNull { node ->
                val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                text.contains(targetAl) || desc.contains(targetAl)
            }
            if (aliasNode != null) {
                val clickableRoot = findClickableAccountRoot(aliasNode)
                Log.i(TAG, "Found target account by alias: $targetAl, node=${clickableRoot.viewIdResourceName}")
                return clickableRoot
            }
        }

        return null
    }

    private fun scrollAccountsList(nodes: List<AccessibilityNodeInfo>): Boolean {
        // 순수 접근성 ACTION_SCROLL_FORWARD 수행 (좌표 고정 드래그 없음)
        val accountsView = nodes.firstOrNull { node ->
            node.viewIdResourceName?.endsWith(":id/accounts") == true ||
                node.viewIdResourceName?.contains("og_bento_scroll_container") == true
        }

        if (accountsView != null && accountsView.isScrollable) {
            val actionResult = accountsView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Log.i(TAG, "scrollAccountsList: ACTION_SCROLL_FORWARD result=$actionResult")
            if (actionResult) return true
        }

        // 스크롤 가능한 조상 뷰 탐색 Fallback
        val scrollableNode = nodes.firstOrNull { it.isScrollable }
        return scrollableNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    private fun findClickableAccountRoot(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            val id = current.viewIdResourceName ?: ""
            if (id.contains("og_bento_available_account_root") || current.isClickable) {
                return current
            }
            current = current.parent
        }
        return node
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        val clickable = findClickableAncestor(node) ?: node
        if (clickable.isClickable) {
            val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "clickNodeOrParent: ACTION_CLICK=$clicked on ${clickable.viewIdResourceName}")
            if (clicked) return true
        }

        // ComposeView 등 ACTION_CLICK이 소비되지 않는 경우 노드의 실제 화면 중심 좌표 탭 Fallback (동적 계산)
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
            val cx = rect.centerX().toFloat()
            val cy = rect.centerY().toFloat()
            Log.d(TAG, "clickNodeOrParent: dynamic tap fallback at ($cx, $cy) for ${node.viewIdResourceName}")
            return tapAtCoordinates?.invoke(cx, cy) == true
        }
        return false
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun retryOrFail(timeoutMs: Long, failureMessage: String) {
        if (SystemClock.uptimeMillis() - phaseStartedAtMillis > timeoutMs) {
            finishWith(GeminiAccountSwitchResult.Failure(failureMessage))
            return
        }
        handler.postDelayed(::step, POLL_INTERVAL_MS)
    }

    private fun finishWith(result: GeminiAccountSwitchResult) {
        if (!active) return
        Log.i(TAG, "finishWith: result=$result")
        active = false
        phase = Phase.FINISHED
        handler.removeCallbacks(::step)
        onFinished(result)
    }

    private fun flattenNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            nodes += node
            val initialCount = node.childCount
            if (initialCount == 0) {
                val refreshed = runCatching { node.refresh() }.getOrDefault(false)
                if (refreshed && node.childCount > 0) {
                    Log.d(TAG, "flattenNodes: refreshed node ${node.viewIdResourceName}, newCount=${node.childCount}")
                }
            }
            val count = node.childCount
            if (node.packageName?.toString()?.contains("googlequicksearchbox") == true) {
                Log.d(TAG, "  quicksearchNode[d=$depth]: id=${node.viewIdResourceName}, count=$count, text=${node.text}, desc=${node.contentDescription}")
            }
            for (index in 0 until count) {
                val child = runCatching { node.getChild(index) }.getOrNull()
                if (child == null) {
                    Log.w(TAG, "  getChild($index) is null for ${node.viewIdResourceName} (count=$count)")
                    continue
                }
                visit(child, depth + 1)
            }
        }
        visit(root, 0)
        return nodes
    }

    private companion object {
        const val TAG = "GeminiAccountSwitcher"
        const val POLL_INTERVAL_MS = 150L
        const val TIMEOUT_LAUNCH_GEMINI_MS = 4000L
        const val TIMEOUT_OPEN_PROFILE_MS = 5000L
        const val TIMEOUT_EXPAND_ACCOUNTS_MS = 5000L
        const val TIMEOUT_FIND_ACCOUNT_MS = 5000L
        const val TIMEOUT_DISMISS_MS = 2500L
        const val MAX_SCROLL_ATTEMPTS = 4
    }
}
