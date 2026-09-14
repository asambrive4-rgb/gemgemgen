// 역할: Gemini 앱에서 접근성 노드를 탐색하고 조작하여 프로필 터치 -> 계정 목록 열기 -> 대상 계정 탐색(필요시 스크롤) -> 터치 전환 및 새 대화방 준비를 자동 수행합니다.
package com.example.gemgemgen.automation.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
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
    private val tapCoordinates: (Float, Float, (() -> Unit)?) -> Boolean,
    private val dispatchGesture: (GestureDescription, AccessibilityService.GestureResultCallback?, Handler?) -> Boolean,
    private val targetIdentifier: String,
    private val targetAlias: String,
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
    private var lastProfileTapMillis = 0L
    private var lastExpandTapMillis = 0L
    private var lastAccountTapMillis = 0L

    private fun reportProgress(phaseLabel: String, message: String) {
        Log.i(TAG, "[$phaseLabel] $message")
        onProgress?.invoke(phaseLabel, message)
    }

    fun start() {
        if (active) return
        active = true
        Log.i(TAG, "Automation started: targetIdentifier=$targetIdentifier, targetAlias=$targetAlias")
        phase = Phase.ENSURE_GEMINI_FOREGROUND
        phaseStartedAtMillis = SystemClock.uptimeMillis()
        scrollAttempts = 0
        sidebarOpened = false
        lastProfileTapMillis = 0L
        lastExpandTapMillis = 0L
        lastAccountTapMillis = 0L
        reportProgress("1/4", "Gemini 앱 실행 확인 중...")
        handler.post(::step)
    }

    fun cancel() {
        if (!active) return
        Log.i(TAG, "Automation cancelled")
        active = false
        phase = Phase.FINISHED
        handler.removeCallbacks(::step)
    }

    private fun step() {
        if (!active) return
        Log.d(TAG, "step(): phase=$phase, activePackage=${activePackageProvider()}")

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
            reportProgress("2/4", "Google 계정 및 프로필 창 여는 중...")
            phase = Phase.OPEN_PROFILE
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 300L)
            return
        }

        if (SystemClock.uptimeMillis() - phaseStartedAtMillis > TIMEOUT_LAUNCH_GEMINI_MS) {
            finishWith(GeminiAccountSwitchResult.Failure("Gemini 앱이 전면에 실행되지 않았습니다."))
            return
        }

        reportProgress("1/4", "Gemini 앱을 전면으로 실행하는 중...")
        launchGemini()
        handler.postDelayed(::step, 600L)
    }

    private fun getAllNodes(): List<AccessibilityNodeInfo> {
        val roots = allRootsProvider().toMutableList()
        val single = rootProvider()
        if (single != null && single !in roots) {
            roots.add(0, single)
        }
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        for (r in roots) {
            runCatching { r.refresh() }
            allNodes += flattenNodes(r)
        }
        Log.d(
            TAG,
            "getAllNodes: roots=${roots.size}, totalNodes=${allNodes.size}, isBento=${isAccountBentoDialogVisible(allNodes)}, isExpanded=${areAccountsExpanded(allNodes)}"
        )
        return allNodes
    }

    private fun handleOpenProfile() {
        val nodes = getAllNodes()
        if (nodes.size <= 1) {
            Log.w(TAG, "handleOpenProfile: waiting for view hierarchy to load (nodes=${nodes.size})")
            retryOrFail(TIMEOUT_OPEN_PROFILE_MS, "화면 노드를 읽을 수 없습니다.")
            return
        }

        // 0. 이미 계정 목록이 펼쳐져 있다면 바로 계정 탐색 단계로 이동
        if (areAccountsExpanded(nodes)) {
            Log.i(TAG, "handleOpenProfile: Accounts list already expanded! Moving to FIND_ACCOUNT_AND_SCROLL")
            phase = Phase.FIND_ACCOUNT_AND_SCROLL
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 200L)
            return
        }

        // 1. 이미 구글 계정 팝업이 열려 있는 경우 바로 계정 목록 확장 단계로 이동
        if (isAccountBentoDialogVisible(nodes)) {
            Log.i(TAG, "handleOpenProfile: Bento dialog already visible, moving to EXPAND_ACCOUNTS")
            phase = Phase.EXPAND_ACCOUNTS
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 200L)
            return
        }

        // 프로필 탭을 방금 수행했다면 다이얼로그가 뜰 때까지 쿨다운(최소 1500ms) 대기하여 연타/토글 방지
        val now = SystemClock.uptimeMillis()
        if (now - lastProfileTapMillis < 1500L) {
            Log.d(TAG, "handleOpenProfile: waiting for Bento dialog to appear (elapsed: ${now - lastProfileTapMillis}ms)")
            handler.postDelayed(::step, POLL_INTERVAL_MS)
            return
        }

        // 2. 사이드바 하단 프로필 라디오 버튼/사용자 노드 탐색 (태블릿 가로 모드 등)
        val sidebarProfileNode = findSidebarProfileNode(nodes)
        if (sidebarProfileNode != null) {
            Log.i(TAG, "handleOpenProfile: clicking sidebar profile node: ${sidebarProfileNode.viewIdResourceName}")
            lastProfileTapMillis = now
            clickNodeOrCoordinates(sidebarProfileNode, preferTap = true)
            handler.postDelayed(::step, 600L)
            return
        }

        // 3. 상단 툴바 프로필 아이콘 탐색 (스마트폰 세로 모드 등)
        val toolbarProfileNode = findToolbarProfileNode(nodes)
        if (toolbarProfileNode != null) {
            Log.i(TAG, "handleOpenProfile: clicking toolbar profile node: ${toolbarProfileNode.viewIdResourceName}")
            lastProfileTapMillis = now
            clickNodeOrCoordinates(toolbarProfileNode, preferTap = true)
            handler.postDelayed(::step, 600L)
            return
        }

        // 4. 만약 사이드바가 닫혀있다면 '사이드바 열기' 먼저 클릭
        if (!sidebarOpened) {
            val openSidebarNode = findOpenSidebarNode(nodes)
            if (openSidebarNode != null) {
                Log.i(TAG, "handleOpenProfile: opening sidebar first")
                clickNodeOrCoordinates(openSidebarNode, preferTap = true)
                sidebarOpened = true
                handler.postDelayed(::step, 500L)
                return
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
            Log.d(TAG, "Target account is already active: $targetIdentifier")
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
            Log.d(TAG, "Accounts list is already expanded, proceeding to find account")
            reportProgress("3/4", "대상 계정 [${targetAlias.ifBlank { targetIdentifier }}] 찾는 중...")
            phase = Phase.FIND_ACCOUNT_AND_SCROLL
            phaseStartedAtMillis = SystemClock.uptimeMillis()
            handler.postDelayed(::step, 200L)
            return
        }

        // 펼치기 탭 후 쿨다운 대기 (애니메이션 대기)
        val now = SystemClock.uptimeMillis()
        if (now - lastExpandTapMillis < 1500L) {
            Log.d(TAG, "handleExpandAccounts: waiting for accounts to expand (elapsed: ${now - lastExpandTapMillis}ms)")
            handler.postDelayed(::step, POLL_INTERVAL_MS)
            return
        }

        reportProgress("2/4", "Google 계정 목록을 펼치는 중...")

        // 계정 목록 펼침 버튼(헤더 또는 꺾쇠 화살표) 클릭
        val expandButton = findAccountExpandButton(nodes)
        if (expandButton != null) {
            val rect = Rect()
            expandButton.getBoundsInScreen(rect)
            Log.d(TAG, "Found expand button: ${expandButton.viewIdResourceName}, bounds=$rect")

            // 사용자가 직접 누르는 '이름/이메일' 헤더 영역(좌측 42% 부근)을 탭 좌표로 지정
            val customTapX = if (rect.width() > 150) {
                (rect.left + rect.width() * 0.42f).toFloat()
            } else {
                rect.centerX().toFloat()
            }
            val customTapY = rect.centerY().toFloat()

            lastExpandTapMillis = now
            // Bento 헤더는 ACTION_CLICK이 무시되므로 preferTap=true 물리 제스처로 100% 확실히 펼침
            clickNodeOrCoordinates(expandButton, preferTap = true, customTapX = customTapX, customTapY = customTapY)
            handler.postDelayed(::step, 600L)
            return
        }

        // Fallback: 버튼 노드가 비동기로 안 잡히더라도 Bento 컨테이너가 식별된 경우, 헤더 중앙 텍스트 영역 탭 시도
        val bentoContainer = nodes.firstOrNull {
            val id = it.viewIdResourceName ?: ""
            id.contains("og_bento") || id.contains("account_management")
        }
        if (bentoContainer != null) {
            val rect = Rect()
            bentoContainer.getBoundsInScreen(rect)
            if (!rect.isEmpty && rect.width() > 200 && rect.height() > 200) {
                val fallbackX = (rect.left + rect.width() * 0.4f).toFloat()
                val fallbackY = (rect.top + 90).toFloat()
                Log.w(TAG, "handleExpandAccounts: Fallback tapping bento header at ($fallbackX, $fallbackY)")
                lastExpandTapMillis = now
                tapCoordinates(fallbackX, fallbackY, null)
                handler.postDelayed(::step, 700L)
                return
            }
        }

        retryOrFail(TIMEOUT_EXPAND_ACCOUNTS_MS, "계정 목록 펼치기 버튼을 찾을 수 없습니다.")
    }

    private fun areAccountsExpanded(nodes: List<AccessibilityNodeInfo>): Boolean {
        val hasAvailableAccount = nodes.any { it.viewIdResourceName?.contains("og_bento_available_account_root") == true }
        val hasAccountsRecyclerChildren = nodes.any { it.viewIdResourceName?.endsWith(":id/accounts") == true && it.childCount > 0 }
        val secondaryCount = nodes.count { it.viewIdResourceName?.contains("og_secondary_account_information") == true }
        val hasExpandedText = nodes.any {
            val desc = it.contentDescription?.toString() ?: ""
            desc.contains("펼쳤습니다") || desc.contains("접습니다")
        }
        return hasAvailableAccount || hasAccountsRecyclerChildren || secondaryCount >= 2 || hasExpandedText
    }

    private fun handleFindAccountAndScroll() {
        val nodes = getAllNodes()
        if (nodes.isEmpty()) {
            retryOrFail(TIMEOUT_FIND_ACCOUNT_MS, "화면 노드를 읽을 수 없습니다.")
            return
        }

        // 현재 화면에서 대상 계정 노드 탐색
        val matchingNode = findMatchingAccountNode(nodes)
        if (matchingNode != null) {
            val now = SystemClock.uptimeMillis()
            if (now - lastAccountTapMillis < 1000L) {
                // 이미 탭했으면 dismiss 대기로 이동
                reportProgress("4/4", "계정 전환 완료 대기 및 새 대화방 준비 중...")
                phase = Phase.WAIT_FOR_DISMISS_AND_NEW_CHAT
                phaseStartedAtMillis = SystemClock.uptimeMillis()
                handler.postDelayed(::step, 600L)
                return
            }
            lastAccountTapMillis = now
            reportProgress("3/4", "대상 계정 [${targetAlias.ifBlank { targetIdentifier }}] 터치 중...")
            val clicked = clickNodeOrCoordinates(matchingNode, preferTap = true)
            if (clicked) {
                Log.d(TAG, "handleFindAccountAndScroll: clicked matching account node")
                reportProgress("4/4", "계정 전환 완료 대기 및 새 대화방 준비 중...")
                phase = Phase.WAIT_FOR_DISMISS_AND_NEW_CHAT
                phaseStartedAtMillis = SystemClock.uptimeMillis()
                handler.postDelayed(::step, 1000L)
                return
            }
        }

        // 화면에 보이지 않는 경우 아래로 스크롤하여 계속 탐색
        if (scrollAttempts < MAX_SCROLL_ATTEMPTS) {
            val root = rootProvider()
            if (root != null) {
                reportProgress("3/4", "계정 목록 스크롤 중 (${scrollAttempts + 1}/$MAX_SCROLL_ATTEMPTS)...")
                scrollAccountsList(nodes, root)
            }
            scrollAttempts++
            handler.postDelayed(::step, 500L)
            return
        }

        finishWith(
            GeminiAccountSwitchResult.Failure(
                "계정 목록에서 [${targetIdentifier.ifBlank { targetAlias }}]을(를) 찾지 못했습니다."
            )
        )
    }

    private fun handleWaitForDismissAndNewChat() {
        val nodes = getAllNodes()

        // 팝업이 닫히고 메인 화면으로 돌아왔는지 확인
        val isDialogGone = nodes.isEmpty() || !isAccountBentoDialogVisible(nodes)
        if (isDialogGone) {
            Log.d(TAG, "handleWaitForDismissAndNewChat: dialog dismissed, searching new chat button")
            // 새 채팅 버튼 클릭 시도
            val newChatNode = findNewChatNode(nodes)
            if (newChatNode != null) {
                Log.d(TAG, "handleWaitForDismissAndNewChat: clicking new chat button")
                clickNodeOrCoordinates(newChatNode, preferTap = true)
            }

            bringAppToForeground?.invoke()
            finishWith(
                GeminiAccountSwitchResult.Success(
                    "Gemini 계정을 [${targetAlias.ifBlank { targetIdentifier }}]로 전환했습니다."
                )
            )
            return
        }

        if (SystemClock.uptimeMillis() - phaseStartedAtMillis > TIMEOUT_DISMISS_MS) {
            // 타임아웃되어도 계정 클릭 자체는 수행되었으므로 성공 간주
            Log.i(TAG, "handleWaitForDismissAndNewChat: timeout waiting for dismiss, considering success")
            bringAppToForeground?.invoke()
            finishWith(
                GeminiAccountSwitchResult.Success(
                    "Gemini 계정을 [${targetAlias.ifBlank { targetIdentifier }}]로 전환했습니다."
                )
            )
            return
        }

        handler.postDelayed(::step, POLL_INTERVAL_MS)
    }



    private fun isAccountBentoDialogVisible(nodes: List<AccessibilityNodeInfo>): Boolean {
        return nodes.any { node ->
            val id = node.viewIdResourceName ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            id.contains("og_bento") ||
                id.contains("og_compact_header") ||
                id.contains("og_collapsed_chevron") ||
                id.contains("account_management") ||
                id.endsWith(":id/accounts") ||
                desc.contains("계정 목록을") ||
                desc.contains("Google 계정")
        }
    }

    private fun isTargetAccountAlreadyActive(nodes: List<AccessibilityNodeInfo>): Boolean {
        val cleanTargetId = targetIdentifier.trim().lowercase()
        if (cleanTargetId.isBlank()) return false

        val activeEmailNode = nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_compact_header_secondary_text") == true
        }
        val currentActiveEmail = activeEmailNode?.text?.toString()?.trim()?.lowercase() ?: ""
        return currentActiveEmail.isNotBlank() && currentActiveEmail == cleanTargetId
    }

    private fun findSidebarProfileNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        // 라디오 버튼을 가진 사이드바 프로필 노드 찾기
        val radioNode = nodes.firstOrNull { it.className?.toString()?.contains("RadioButton") == true }
        if (radioNode != null) {
            return findClickableAncestor(radioNode) ?: radioNode
        }

        // 사이드바 하단 프로필 텍스트로 찾기 (PRO 배지 또는 별칭)
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            (desc.contains("프로필") || desc.contains("계정") || text == "PRO") &&
                (node.isClickable || node.parent?.isClickable == true)
        }
    }

    private fun findToolbarProfileNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            val id = node.viewIdResourceName ?: ""
            (desc.contains("Google 계정") || desc.contains("계정 및 설정") || id.contains("avatar")) &&
                (node.isClickable || node.parent?.isClickable == true)
        }
    }

    private fun findOpenSidebarNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val candidates = listOf("사이드바 열기", "메뉴", "Open navigation drawer")
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            candidates.any { desc.contains(it) || text.contains(it) }
        }
    }

    private fun findAccountsRecyclerView(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            node.viewIdResourceName?.endsWith(":id/accounts") == true ||
                node.viewIdResourceName?.contains("og_bento_available_account_root") == true
        }
    }

    private fun findAccountExpandButton(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_bento_account_management_header_container") == true
        } ?: nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_compact_header_secondary_text") == true
        } ?: nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_compact_header_primary_text") == true
        } ?: nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_compact_header") == true
        } ?: nodes.firstOrNull { node ->
            val desc = node.contentDescription?.toString() ?: ""
            desc.contains("계정 목록을 펼칩니다") || desc.contains("펼칩니다")
        } ?: nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_collapsed_chevron") == true
        } ?: nodes.firstOrNull { node ->
            node.viewIdResourceName?.contains("og_compact_header_chevron_background") == true
        }
    }

    private fun findMatchingAccountNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val targetId = targetIdentifier.trim().lowercase()
        val targetAl = targetAlias.trim().lowercase()

        // 1. 이메일(식별자) 우선 일치 검사
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
                val rootNode = findClickableAccountRoot(emailNode)
                Log.d(TAG, "Found target account node by email: $targetId, node=${rootNode.viewIdResourceName}")
                return rootNode
            }
        }

        // 2. 별칭 일치 검사 (기본 별칭 '서브N' 제외)
        if (targetAl.isNotBlank() && !targetAl.startsWith("서브")) {
            val aliasNode = nodes.firstOrNull { node ->
                val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                text.contains(targetAl) || desc.contains(targetAl)
            }
            if (aliasNode != null) {
                val rootNode = findClickableAccountRoot(aliasNode)
                Log.d(TAG, "Found target account node by alias: $targetAl, node=${rootNode.viewIdResourceName}")
                return rootNode
            }
        }

        return null
    }

    private fun findNewChatNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val candidates = listOf("새 채팅", "새 대화", "New chat")
        return nodes.firstOrNull { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            candidates.any { text == it || desc == it }
        }
    }

    private fun scrollAccountsList(nodes: List<AccessibilityNodeInfo>, fallbackRoot: AccessibilityNodeInfo): Boolean {
        val accountsView = nodes.firstOrNull { node ->
            node.viewIdResourceName?.endsWith(":id/accounts") == true ||
                node.viewIdResourceName?.contains("og_bento_scroll_container") == true
        }

        // 1. Accessibility ACTION_SCROLL_FORWARD 시도
        if (accountsView != null && accountsView.isScrollable) {
            val actionResult = accountsView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (actionResult) return true
        }

        // 2. 제스처 스와이프 (위로 쓸어올려 아래 내용 노출)
        val bounds = Rect()
        if (accountsView != null) {
            accountsView.getBoundsInScreen(bounds)
        } else {
            fallbackRoot.getBoundsInScreen(bounds)
        }

        val centerX = bounds.centerX().toFloat().coerceAtLeast(100f)
        val startY = (bounds.bottom - 200).toFloat().coerceAtLeast(400f)
        val endY = (bounds.top + 200).toFloat().coerceAtLeast(100f)

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 250L))
            .build()

        return dispatchGesture(gesture, null, handler)
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

    private fun clickNodeOrCoordinates(
        node: AccessibilityNodeInfo,
        preferTap: Boolean = false,
        customTapX: Float? = null,
        customTapY: Float? = null
    ): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val tapX = customTapX ?: rect.centerX().toFloat()
        val tapY = customTapY ?: rect.centerY().toFloat()

        // 1. preferTap인 경우 물리 터치(제스처)만 1회 전송하여 접근성 클릭과 겹치는 더블탭(토글 즉시 취소) 원천 차단
        if (preferTap) {
            if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
                Log.d(TAG, "clickNodeOrCoordinates: preferTap=true, single tap at ($tapX, $tapY) for ${node.viewIdResourceName}")
                return tapCoordinates(tapX, tapY, null)
            }
        }

        // 2. Accessibility ACTION_CLICK 시도
        val clickable = findClickableAncestor(node) ?: node
        var a11yClicked = false
        if (clickable.isClickable) {
            a11yClicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "clickNodeOrCoordinates: ACTION_CLICK result=$a11yClicked on ${clickable.viewIdResourceName}")
            if (a11yClicked) {
                // 접근성 클릭이 성공했으면 추가 물리 탭을 전송하지 않음 (연타/재접힘 방지)
                return true
            }
        }

        // 3. ACTION_CLICK이 실패하거나 clickable 노드가 없는 경우에만 물리 터치 Fallback 전송
        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
            Log.d(TAG, "clickNodeOrCoordinates: Fallback tap at ($tapX, $tapY) for ${node.viewIdResourceName}")
            return tapCoordinates(tapX, tapY, null)
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
        fun visit(node: AccessibilityNodeInfo) {
            nodes += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::visit)
            }
        }
        visit(root)
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
