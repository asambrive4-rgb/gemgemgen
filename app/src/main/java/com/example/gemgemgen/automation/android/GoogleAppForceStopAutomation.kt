// 역할: Google 앱 상세 설정 화면에서 강제 중지 버튼과 확인 팝업을 클릭하여 메모리를 확보합니다.
package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult

internal data class ForceStopNodeLabels(
    val settingsPackageNames: List<String> = listOf("com.android.settings"),
    val forceStopButtonIds: List<String> = listOf(
        "com.android.settings:id/forcestop_button",
        "com.android.settings:id/button2_negative",
        "com.android.settings:id/force_stop_button",
        "com.android.settings:id/right_button"
    ),
    val forceStopButtonCandidates: List<String> = listOf(
        "강제 중지",
        "강제 종료",
        "Force stop"
    ),
    val confirmDialogButtonIds: List<String> = listOf(
        "android:id/button1",
        "com.android.settings:id/button1"
    ),
    val confirmDialogCandidates: List<String> = listOf(
        "강제 중지",
        "강제 종료",
        "확인",
        "Force stop",
        "OK"
    )
)

internal fun isForceStopButton(
    viewIdResourceName: String?,
    nodeLabel: String?,
    labels: ForceStopNodeLabels
): Boolean {
    val idMatches = viewIdResourceName != null && labels.forceStopButtonIds.any { it == viewIdResourceName }
    val labelMatches = nodeLabel != null && labels.forceStopButtonCandidates.any { candidate ->
        candidate.isNotBlank() && nodeLabel.contains(candidate, ignoreCase = true)
    }
    return idMatches || labelMatches
}

internal fun isConfirmDialogButton(
    viewIdResourceName: String?,
    nodeLabel: String?,
    labels: ForceStopNodeLabels
): Boolean {
    val idMatches = viewIdResourceName != null && labels.confirmDialogButtonIds.any { it == viewIdResourceName }
    val labelMatches = nodeLabel != null && labels.confirmDialogCandidates.any { candidate ->
        candidate.isNotBlank() && nodeLabel.contains(candidate, ignoreCase = true)
    }
    return idMatches || labelMatches
}

internal class GoogleAppForceStopAutomation(
    private val handler: Handler,
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val allRootsProvider: () -> List<AccessibilityNodeInfo> = { listOfNotNull(rootProvider()) },
    private val currentPackageProvider: () -> String?,
    private val performBack: () -> Boolean,
    private val launchDetails: () -> Boolean,
    private val onFinished: (MemoryCleanupResult) -> Unit,
    private val labels: ForceStopNodeLabels = ForceStopNodeLabels()
) {
    private enum class Phase {
        LAUNCH_SETTINGS,
        FIND_FORCE_STOP,
        CLICK_FORCE_STOP,
        WAIT_CONFIRM_DIALOG,
        CLOSE_SETTINGS
    }

    private var active = false
    private var phase = Phase.LAUNCH_SETTINGS
    private var phaseStartedAtMillis = 0L
    private var backCount = 0

    fun start() {
        if (active) return
        active = true
        if (!launchDetails()) {
            finish(MemoryCleanupResult.Failure("Google 앱 설정 화면 실행에 실패했습니다."))
            return
        }
        enter(Phase.LAUNCH_SETTINGS)
    }

    fun cancel() {
        active = false
    }

    private fun enter(nextPhase: Phase) {
        if (!active) return
        phase = nextPhase
        phaseStartedAtMillis = SystemClock.uptimeMillis()
        if (nextPhase == Phase.CLOSE_SETTINGS) {
            backCount = 0
        }
        checkPhase()
    }

    private fun checkPhase() {
        if (!active) return

        when (phase) {
            Phase.LAUNCH_SETTINGS -> {
                if (isSettingsForeground()) {
                    enter(Phase.FIND_FORCE_STOP)
                } else {
                    retryOrFail("설정 화면을 찾지 못했습니다.") { checkPhase() }
                }
            }

            Phase.FIND_FORCE_STOP -> {
                val nodes = allNodes()
                val forceStopNode = findForceStopNode(nodes)
                if (forceStopNode != null) {
                    if (!forceStopNode.isEnabled) {
                        // 이미 비활성화(회색)된 상태면 프로세스가 종료되어 있는 상태이므로 성공 처리 후 복귀
                        enter(Phase.CLOSE_SETTINGS)
                    } else {
                        enter(Phase.CLICK_FORCE_STOP)
                    }
                } else {
                    retryOrFail("강제 중지 버튼을 찾지 못했습니다.") { checkPhase() }
                }
            }

            Phase.CLICK_FORCE_STOP -> {
                val nodes = allNodes()
                val forceStopNode = findForceStopNode(nodes)
                if (forceStopNode != null) {
                    if (!forceStopNode.isEnabled) {
                        enter(Phase.CLOSE_SETTINGS)
                    } else if (clickNodeOrParent(forceStopNode)) {
                        enter(Phase.WAIT_CONFIRM_DIALOG)
                    } else {
                        retryOrFail("강제 중지 버튼을 누르지 못했습니다.") { checkPhase() }
                    }
                } else {
                    retryOrFail("강제 중지 버튼을 찾지 못했습니다.") { checkPhase() }
                }
            }

            Phase.WAIT_CONFIRM_DIALOG -> {
                val nodes = allNodes()
                val confirmNode = findConfirmDialogNode(nodes)
                if (confirmNode != null) {
                    if (clickNodeOrParent(confirmNode)) {
                        enter(Phase.CLOSE_SETTINGS)
                    } else {
                        retryOrFail("강제 중지 확인 버튼을 누르지 못했습니다.") { checkPhase() }
                    }
                } else {
                    // 확인 다이얼로그가 없는 특수 기기 대비 Fallback:
                    // 충분한 대기 시간(1.5초) 후에도 팝업이 없고 강제 중지 버튼이 비활성화된 경우에만 완료 처리
                    val elapsedMillis = SystemClock.uptimeMillis() - phaseStartedAtMillis
                    val forceStopNode = findForceStopNode(nodes)
                    if (elapsedMillis >= NO_DIALOG_FALLBACK_WAIT_MS && forceStopNode != null && !forceStopNode.isEnabled) {
                        enter(Phase.CLOSE_SETTINGS)
                    } else {
                        retryOrFail("강제 중지 확인 팝업을 찾지 못했습니다.") { checkPhase() }
                    }
                }
            }

            Phase.CLOSE_SETTINGS -> {
                if (!isSettingsForeground()) {
                    finish(MemoryCleanupResult.Success)
                } else if (backCount >= MAX_SETTINGS_BACKS) {
                    finish(MemoryCleanupResult.Success)
                } else if (performBack()) {
                    backCount += 1
                    retryOrFail("설정 화면을 닫지 못했습니다.") { checkPhase() }
                } else {
                    retryOrFail("설정 화면 뒤로 가기에 실패했습니다.") { checkPhase() }
                }
            }
        }
    }

    private fun retryOrFail(failureMessage: String, retry: () -> Unit) {
        if (!active) return

        val elapsedMillis = SystemClock.uptimeMillis() - phaseStartedAtMillis
        val retryWaitMillis = AutomationRetryWaitPolicy.nextDelayMillis(elapsedMillis)
        if (retryWaitMillis == null) {
            finish(MemoryCleanupResult.Failure(failureMessage))
        } else {
            handler.postDelayed(
                {
                    if (active) retry()
                },
                retryWaitMillis
            )
        }
    }

    private fun isSettingsForeground(): Boolean {
        val currentPackage = currentPackageProvider() ?: return false
        return labels.settingsPackageNames.any { it.equals(currentPackage, ignoreCase = true) }
    }

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val roots = allRootsProvider().ifEmpty { listOfNotNull(rootProvider()) }
        return roots.flatMap(::flattenNodes)
    }

    private fun findForceStopNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        // 1순위: ID와 라벨 일치
        val exactMatch = nodes.firstOrNull { node ->
            val idMatches = node.viewIdResourceName != null && labels.forceStopButtonIds.contains(node.viewIdResourceName)
            idMatches && hasLabel(node, labels.forceStopButtonCandidates)
        }
        if (exactMatch != null) return exactMatch

        // 2순위: 라벨 일치 및 버튼 형태
        val labelMatch = nodes.firstOrNull { node ->
            hasLabel(node, labels.forceStopButtonCandidates) && isButtonLike(node)
        }
        if (labelMatch != null) return labelMatch

        // 3순위: ID만 일치
        return nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.forceStopButtonIds.contains(node.viewIdResourceName)
        }
    }

    private fun findConfirmDialogNode(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        // 1순위: 팝업 확인 버튼 ID + 라벨 일치
        val exactMatch = nodes.firstOrNull { node ->
            val idMatches = node.viewIdResourceName != null && labels.confirmDialogButtonIds.contains(node.viewIdResourceName)
            idMatches && hasLabel(node, labels.confirmDialogCandidates)
        }
        if (exactMatch != null) return exactMatch

        // 2순위: AlertDialog positive button ID 일치
        val idMatch = nodes.firstOrNull { node ->
            node.viewIdResourceName != null && labels.confirmDialogButtonIds.contains(node.viewIdResourceName)
        }
        if (idMatch != null) return idMatch

        // 3순위: 라벨 일치 및 버튼 형태 (취소 버튼 제외)
        return nodes.firstOrNull { node ->
            val isCancel = node.viewIdResourceName == "android:id/button2" ||
                nodeValue(node)?.equals("취소", ignoreCase = true) == true
            !isCancel && hasLabel(node, labels.confirmDialogCandidates) && isButtonLike(node)
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(MAX_ANCESTOR_SEARCH_DEPTH) {
            if (current == null) return@repeat
            if (current?.isClickable == true) {
                return current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            current = current?.parent
        }
        return false
    }

    private fun hasLabel(node: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        val value = nodeValue(node) ?: return false
        return candidates.any { candidate ->
            candidate.isNotBlank() && value.contains(candidate, ignoreCase = true)
        }
    }

    private fun nodeValue(node: AccessibilityNodeInfo): String? {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString()
        ).firstOrNull { it.isNotBlank() }
    }

    private fun isButtonLike(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return className.endsWith("Button") || node.isClickable
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

    private fun finish(result: MemoryCleanupResult) {
        if (!active) return
        active = false
        onFinished(result)
    }

    private companion object {
        const val MAX_SETTINGS_BACKS = 3
        const val MAX_ANCESTOR_SEARCH_DEPTH = 8
        const val NO_DIALOG_FALLBACK_WAIT_MS = 1500L
    }
}
