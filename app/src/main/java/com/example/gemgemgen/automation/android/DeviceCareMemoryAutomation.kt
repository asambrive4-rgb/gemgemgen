package com.example.gemgemgen.automation.android

import android.os.Handler
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.automation.domain.AutomationRetryWaitPolicy
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult

internal data class MemoryCleanupNodeLabels(
    val deviceCarePackageName: String = "com.samsung.android.lool",
    val memoryTitleId: String = "com.samsung.android.lool:id/title_text",
    val memoryContainerId: String = "com.samsung.android.lool:id/category_container",
    val cleanNowId: String = "com.samsung.android.lool:id/bt_fix_now",
    val completionDescriptionId: String =
        "com.samsung.android.lool:id/fix_now_description_tv",
    val memoryTitleCandidates: List<String> = listOf("메모리", "Memory"),
    val cleanNowCandidates: List<String> = listOf("지금 정리", "Clean now"),
    val completionCandidates: List<String> = listOf("삭제했습니다", "deleted")
)

internal fun memoryTitleIdHasMemoryLabel(
    viewIdResourceName: String?,
    nodeLabel: String?,
    labels: MemoryCleanupNodeLabels
): Boolean {
    return viewIdResourceName == labels.memoryTitleId &&
        nodeLabel != null &&
        labels.memoryTitleCandidates.any { candidate ->
            candidate.isNotBlank() && nodeLabel.contains(candidate, ignoreCase = true)
        }
}

internal class DeviceCareMemoryAutomation(
    private val handler: Handler,
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val currentPackageProvider: () -> String?,
    private val performBack: () -> Boolean,
    private val launchDashboard: () -> Boolean,
    private val onFinished: (MemoryCleanupResult) -> Unit,
    private val labels: MemoryCleanupNodeLabels = MemoryCleanupNodeLabels()
) {
    private enum class Phase {
        LAUNCH_DASHBOARD,
        FIND_MEMORY,
        OPEN_MEMORY,
        FIND_CLEAN,
        CLICK_CLEAN,
        WAIT_CONFIRM,
        VERIFY_COMPLETION,
        CLOSE_DEVICE_CARE
    }

    private var active = false
    private var phase = Phase.LAUNCH_DASHBOARD
    private var phaseStartedAtMillis = 0L
    private var completionTextBeforeClick = ""
    private var backCount = 0

    fun start() {
        if (active) return
        active = true
        if (!launchDashboard()) {
            finish(MemoryCleanupResult.Failure("Device Care 대시보드 실행에 실패했습니다."))
            return
        }
        enter(Phase.LAUNCH_DASHBOARD)
    }

    fun cancel() {
        active = false
    }

    private fun enter(nextPhase: Phase) {
        if (!active) return
        phase = nextPhase
        phaseStartedAtMillis = SystemClock.uptimeMillis()
        if (nextPhase == Phase.CLOSE_DEVICE_CARE) {
            backCount = 0
        }
        checkPhase()
    }

    private fun checkPhase() {
        if (!active) return

        when (phase) {
            Phase.LAUNCH_DASHBOARD -> {
                val root = rootProvider()
                if (isDeviceCareForeground() && root?.let(::findMemoryTarget) != null) {
                    enter(Phase.FIND_MEMORY)
                } else {
                    retryOrFail("Device Care 대시보드를 찾지 못했습니다.") { checkPhase() }
                }
            }

            Phase.FIND_MEMORY -> {
                val root = rootProvider()
                if (root != null && findMemoryTarget(root) != null) {
                    enter(Phase.OPEN_MEMORY)
                } else {
                    retryOrFail("Device Care 메모리 카드를 찾지 못했습니다.") { checkPhase() }
                }
            }

            Phase.OPEN_MEMORY -> {
                val target = rootProvider()?.let(::findMemoryTarget)
                if (target != null && clickNodeOrParent(target)) {
                    enter(Phase.FIND_CLEAN)
                } else {
                    retryOrFail("Device Care 메모리 카드를 열지 못했습니다.") { checkPhase() }
                }
            }

            Phase.FIND_CLEAN -> {
                val root = rootProvider()
                val cleanNode = root?.let(::findCleanNode)
                if (cleanNode != null) {
                    completionTextBeforeClick = root?.let(::findCompletionText).orEmpty()
                    enter(Phase.CLICK_CLEAN)
                } else {
                    retryOrFail("Device Care 지금 정리 버튼을 찾지 못했습니다.") { checkPhase() }
                }
            }

            Phase.CLICK_CLEAN -> {
                val cleanNode = rootProvider()?.let(::findCleanNode)
                if (cleanNode != null && clickNodeOrParent(cleanNode)) {
                    enter(Phase.WAIT_CONFIRM)
                } else {
                    retryOrFail("Device Care 지금 정리 버튼을 누르지 못했습니다.") { checkPhase() }
                }
            }

            Phase.WAIT_CONFIRM -> {
                handler.postDelayed(
                    {
                        if (active) enter(Phase.VERIFY_COMPLETION)
                    },
                    CLEAN_CONFIRM_WAIT_MS
                )
            }

            Phase.VERIFY_COMPLETION -> {
                val completionText = rootProvider()?.let(::findCompletionText).orEmpty()
                val isCompletionVisible = completionText.isNotBlank() && (
                    containsCandidate(completionText, labels.completionCandidates) ||
                        completionText != completionTextBeforeClick
                    )
                if (isCompletionVisible) {
                    enter(Phase.CLOSE_DEVICE_CARE)
                } else {
                    retryOrFail("Device Care 메모리 정리 완료 상태를 확인하지 못했습니다.") {
                        checkPhase()
                    }
                }
            }

            Phase.CLOSE_DEVICE_CARE -> {
                if (!isDeviceCareForeground()) {
                    finish(MemoryCleanupResult.Success)
                } else if (backCount >= MAX_DEVICE_CARE_BACKS) {
                    finish(MemoryCleanupResult.Failure("Device Care 화면을 닫지 못했습니다."))
                } else if (performBack()) {
                    backCount += 1
                    retryOrFail("Device Care 화면을 닫지 못했습니다.") { checkPhase() }
                } else {
                    retryOrFail("Device Care 뒤로 가기에 실패했습니다.") { checkPhase() }
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

    private fun isDeviceCareForeground(): Boolean {
        return currentPackageProvider() == labels.deviceCarePackageName
    }

    private fun findMemoryTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = flattenNodes(root)
        val titleNode = nodes.firstOrNull { node ->
            memoryTitleIdHasMemoryLabel(
                viewIdResourceName = node.viewIdResourceName,
                nodeLabel = nodeValue(node),
                labels = labels
            )
        } ?: nodes
            .filter { node -> hasLabel(node, labels.memoryTitleCandidates) }
            .sortedBy { if (isTextLike(it)) 0 else 1 }
            .firstOrNull()

        if (titleNode != null) {
            return findClickableParent(titleNode, labels.memoryContainerId)
        }

        return nodes.firstOrNull { node ->
            node.viewIdResourceName == labels.memoryContainerId &&
                containsLabelInSubtree(node, labels.memoryTitleCandidates)
        }
    }

    private fun findCleanNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val nodes = flattenNodes(root)
        return nodes.firstOrNull { node ->
            node.viewIdResourceName == labels.cleanNowId
        } ?: nodes
            .filter { node -> hasLabel(node, labels.cleanNowCandidates) }
            .sortedBy { if (isButtonLike(it)) 0 else 1 }
            .firstOrNull()
    }

    private fun findCompletionText(root: AccessibilityNodeInfo): String? {
        val nodes = flattenNodes(root)
        val completionNode = nodes.firstOrNull { node ->
            node.viewIdResourceName == labels.completionDescriptionId
        } ?: nodes.firstOrNull { node ->
            hasLabel(node, labels.completionCandidates)
        }
        return completionNode?.let(::nodeValue)
    }

    private fun findClickableParent(
        node: AccessibilityNodeInfo,
        preferredId: String
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var fallback: AccessibilityNodeInfo? = null
        repeat(MAX_ANCESTOR_SEARCH_DEPTH) {
            val candidate = current ?: return@repeat
            if (
                candidate.isClickable &&
                candidate.viewIdResourceName == preferredId &&
                containsLabelInSubtree(candidate, labels.memoryTitleCandidates)
            ) {
                return candidate
            }
            if (fallback == null && candidate.isClickable) {
                fallback = candidate
            }
            current = candidate.parent
        }
        return fallback
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

    private fun containsLabelInSubtree(
        node: AccessibilityNodeInfo,
        candidates: List<String>
    ): Boolean {
        if (hasLabel(node, candidates)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (containsLabelInSubtree(child, candidates)) return true
        }
        return false
    }

    private fun hasLabel(node: AccessibilityNodeInfo, candidates: List<String>): Boolean {
        val value = nodeValue(node) ?: return false
        return containsCandidate(value, candidates)
    }

    private fun nodeValue(node: AccessibilityNodeInfo): String? {
        return listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString()
        ).firstOrNull { it.isNotBlank() }
    }

    private fun containsCandidate(value: String, candidates: List<String>): Boolean {
        return candidates.any { candidate ->
            candidate.isNotBlank() && value.contains(candidate, ignoreCase = true)
        }
    }

    private fun isTextLike(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return className.endsWith("TextView") || className.endsWith("Button")
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
        const val CLEAN_CONFIRM_WAIT_MS = 1000L
        const val MAX_DEVICE_CARE_BACKS = 2
        const val MAX_ANCESTOR_SEARCH_DEPTH = 8
    }
}
