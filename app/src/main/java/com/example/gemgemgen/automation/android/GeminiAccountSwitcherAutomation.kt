// 역할: Gemini 앱에서 접근성 노드를 탐색하고 조작하여 프로필 열기 -> 계정 목록(ID 목록) 펼치기까지만 자동 수행합니다.
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
    private val tapAtCoordinates: ((Float, Float) -> Boolean)? = null,
    private val onProgress: ((phase: String, message: String) -> Unit)? = null,
    private val onFinished: (GeminiAccountSwitchResult) -> Unit
) {
    private enum class Phase {
        ENSURE_GEMINI_FOREGROUND,
        OPEN_PROFILE,
        EXPAND_ACCOUNTS,
        FINISHED
    }

    private var active = false
    private var phase = Phase.ENSURE_GEMINI_FOREGROUND
    private var phaseStartedAtMillis = 0L
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
        Log.i(TAG, "Gemini account picker automation started")
        phase = Phase.ENSURE_GEMINI_FOREGROUND
        phaseStartedAtMillis = SystemClock.uptimeMillis()
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
        Log.i(TAG, "Gemini account picker automation cancelled")
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
        for (r in roots) {
            val flat = flattenNodes(r)
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
        return allNodes.distinct()
    }

    private fun handleOpenProfile() {
        val nodes = getAllNodes()
        if (nodes.isEmpty()) {
            retryOrFail(TIMEOUT_OPEN_PROFILE_MS, "화면 노드를 읽을 수 없습니다.")
            return
        }

        // 0. 이미 계정 목록이 펼쳐져 있다면 바로 성공 종료
        if (areAccountsExpanded(nodes)) {
            Log.i(TAG, "handleOpenProfile: Accounts list already expanded")
            finishWith(GeminiAccountSwitchResult.Success("Gemini 계정 목록을 열었습니다."))
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

        // 계정 목록 RecyclerView 가 이미 펼쳐져 있는지 확인 -> 성공 종료!
        if (areAccountsExpanded(nodes)) {
            Log.i(TAG, "handleExpandAccounts: Accounts list is expanded, success!")
            finishWith(GeminiAccountSwitchResult.Success("Gemini 계정 목록을 열었습니다."))
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

    // --- 노드 탐색 헬퍼 함수들 ---

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

    private fun findToolbarProfileNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
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

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        val clickable = findClickableAncestor(node) ?: node
        if (clickable.isClickable) {
            val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "clickNodeOrParent: ACTION_CLICK=$clicked on ${clickable.viewIdResourceName}")
            if (clicked) return true
        }

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

    private fun findClickableAncestor(
        node: AccessibilityNodeInfo,
        maxDepth: Int = MAX_CLICKABLE_PARENT_DEPTH
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(maxDepth) {
            if (current == null) return null
            if (current?.isClickable == true) return current
            current = current?.parent
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
            for (index in 0 until count) {
                val child = runCatching { node.getChild(index) }.getOrNull()
                if (child == null) {
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
        const val MAX_CLICKABLE_PARENT_DEPTH = 8
        const val POLL_INTERVAL_MS = 150L
        const val TIMEOUT_LAUNCH_GEMINI_MS = 4000L
        const val TIMEOUT_OPEN_PROFILE_MS = 5000L
        const val TIMEOUT_EXPAND_ACCOUNTS_MS = 5000L
    }
}
