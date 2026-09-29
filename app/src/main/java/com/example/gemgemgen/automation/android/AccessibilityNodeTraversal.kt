// 역할: 접근성 트리 지연 순회(정방향/역방향), 클릭 가능한 상위 노드 탐색·클릭, 패키지별 NodeFinder 공통 기반을 제공하는 탐색 엔진
package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal object AccessibilityNodeTraversal {
    private val PRUNABLE_SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "com.sec.android.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )

    private class DeferredChild(
        val parent: AccessibilityNodeInfo,
        val index: Int,
        val inheritedPackageName: String? = null
    )

    fun findClickableNodeOrParent(
        node: AccessibilityNodeInfo?,
        maxDepth: Int = Int.MAX_VALUE
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < maxDepth) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    fun clickNodeOrParent(
        node: AccessibilityNodeInfo?,
        maxDepth: Int = Int.MAX_VALUE
    ): Boolean {
        return findClickableNodeOrParent(node, maxDepth)
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    /**
     * root에서 시작하여 깊이 우선(DFS) 방식으로 노드를 순회하는 [Sequence]를 생성한다.
     * 비재귀 스택 기반 Sequence와 자식 노드 지연 취득(Lazy Child Resolution) 특성으로 인해,
     * [firstOrNull], [any], [take] 등의 조기 종료(Early-Exit) 연산자 사용 시
     * 스택에 적재된 나머지 미방문 자식 노드의 Binder IPC(getChild) 호출과 대량 리스트 객체 할당을 원천 방지한다.
     * 또한 Compose 가상 노드(packageName == null)는 부모 패키지명을 상속하여
     * 시스템 UI나 키보드 서브트리를 조기 스킵(Pruning)하고 대상 앱 가상 노드를 안전하게 보존한다.
     */
    fun lazyTraverse(
        root: AccessibilityNodeInfo?,
        packageFilter: ((CharSequence?) -> Boolean)? = null
    ): Sequence<AccessibilityNodeInfo> = lazyTraverseInternal(
        root = root,
        reverseChildren = false,
        packageFilter = packageFilter
    )

    /**
     * root에서 시작하여 역방향 깊이 우선(Reverse DFS) 방식으로 노드를 순회하는 [Sequence]를 생성한다.
     * 화면 하단(트리의 마지막 자식 및 우측 서브트리)부터 우선 탐색하므로,
     * 하단 바, 바텀시트, 화면 아래쪽 버튼 등을 찾을 때 [firstOrNull]을 사용하면
     * 전체 트리를 순회하지 않고 단 몇 개의 노드만 검사한 뒤 즉시 조기 종료(Early-Exit)할 수 있다.
     */
    fun lazyTraverseReverse(
        root: AccessibilityNodeInfo?,
        packageFilter: ((CharSequence?) -> Boolean)? = null
    ): Sequence<AccessibilityNodeInfo> = lazyTraverseInternal(
        root = root,
        reverseChildren = true,
        packageFilter = packageFilter
    )

    private fun lazyTraverseInternal(
        root: AccessibilityNodeInfo?,
        reverseChildren: Boolean,
        packageFilter: ((CharSequence?) -> Boolean)?
    ): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        val stack = ArrayDeque<Any>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val item = stack.removeLast()
            val (node, inheritedPkg) = if (item is AccessibilityNodeInfo) {
                item to null
            } else {
                val deferred = item as DeferredChild
                val child = deferred.parent.getChild(deferred.index) ?: continue
                child to deferred.inheritedPackageName
            }

            val effectivePkg = node.packageName?.toString() ?: inheritedPkg
            if (effectivePkg != null) {
                if (effectivePkg in PRUNABLE_SYSTEM_PACKAGES) {
                    continue
                }
                if (packageFilter != null && !packageFilter(effectivePkg)) {
                    continue
                }
            } else if (packageFilter != null && !packageFilter(null)) {
                continue
            }

            if (packageFilter == null || packageFilter(effectivePkg)) {
                yield(node)
            }

            val childCount = node.childCount
            val indices = if (reverseChildren) 0 until childCount else (childCount - 1 downTo 0)
            for (i in indices) {
                stack.add(DeferredChild(node, i, effectivePkg))
            }
        }
    }
}

internal abstract class BaseAccessibilityNodeFinder(
    protected val rootProvider: () -> AccessibilityNodeInfo?,
    private val targetPackages: Set<String>
) {
    protected val snapshotCache = AccessibilityNodeSnapshotCache()

    fun invalidateCache() {
        snapshotCache.clear()
    }

    fun invalidateInputNode() {
        snapshotCache.invalidate("input")
    }

    protected inline fun cachedNode(
        key: String,
        crossinline find: (root: AccessibilityNodeInfo) -> AccessibilityNodeInfo?
    ): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind(key, root) { find(root) }
    }

    protected fun isTargetPackage(packageName: CharSequence?): Boolean {
        return packageName?.toString() in targetPackages
    }

    protected fun traverseTargetPackage(
        root: AccessibilityNodeInfo,
        reverse: Boolean = false
    ): Sequence<AccessibilityNodeInfo> {
        return if (reverse) {
            AccessibilityNodeTraversal.lazyTraverseReverse(root, ::isTargetPackage)
        } else {
            AccessibilityNodeTraversal.lazyTraverse(root, ::isTargetPackage)
        }
    }

    protected fun findNodeByViewId(
        root: AccessibilityNodeInfo,
        viewId: String
    ): AccessibilityNodeInfo? {
        return try {
            root.findAccessibilityNodeInfosByViewId(viewId)
                ?.firstOrNull { isTargetPackage(it.packageName) }
        } catch (_: RuntimeException) {
            null
        }
    }

    protected fun findNodesByText(
        root: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        return try {
            root.findAccessibilityNodeInfosByText(value)
                ?.filter { isTargetPackage(it.packageName) }
                .orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    protected fun findInputNodeBy(
        viewIds: List<String> = emptyList(),
        keywords: List<String> = emptyList()
    ): AccessibilityNodeInfo? = cachedNode("input") { root ->
        for (viewId in viewIds) {
            findNodeByViewId(root, viewId)?.let { return@cachedNode it }
        }
        for (keyword in keywords) {
            findNodesByText(root, keyword).firstOrNull { it.isInputLike() }?.let { return@cachedNode it }
        }
        traverseTargetPackage(root).firstOrNull { it.isInputLike() }
    }

    protected fun findFirstNodeContainingTextOrDescription(
        root: AccessibilityNodeInfo,
        value: String
    ): AccessibilityNodeInfo? {
        findNodesByText(root, value).firstOrNull { it.matchesTextOrDescription(value) }?.let { return it }
        return traverseTargetPackage(root).firstOrNull { it.matchesTextOrDescription(value) }
    }

    protected fun findFirstNodeByExactCandidates(
        root: AccessibilityNodeInfo,
        candidates: List<String>,
        nativePredicate: (AccessibilityNodeInfo, String) -> Boolean = { node, target ->
            node.matchesExactTextOrDescription(target)
        }
    ): AccessibilityNodeInfo? {
        for (candidate in candidates) {
            val trimmed = candidate.trim()
            findNodesByText(root, trimmed).firstOrNull { node ->
                nativePredicate(node, trimmed)
            }?.let { return it }
        }

        val candidateSet = candidates.map { it.trim().lowercase() }.toSet()
        return traverseTargetPackage(root).firstOrNull { node ->
            node.matchesExactCandidateSet(candidateSet)
        }
    }

    protected fun findFirstNodeByDescription(
        root: AccessibilityNodeInfo,
        value: String
    ): AccessibilityNodeInfo? {
        val trimmed = value.trim()
        findNodesByText(root, trimmed).firstOrNull { node ->
            node.matchesExactDescription(trimmed)
        }?.let { return it }

        return traverseTargetPackage(root).firstOrNull { node ->
            node.matchesExactDescription(trimmed)
        }
    }
}

internal fun AccessibilityNodeInfo.isInputLike(): Boolean {
    return isEditable || className?.toString()?.contains("EditText", ignoreCase = true) == true
}

internal fun AccessibilityNodeInfo.matchesTextOrDescription(value: String): Boolean {
    return text?.toString()?.contains(value, ignoreCase = true) == true ||
        contentDescription?.toString()?.contains(value, ignoreCase = true) == true
}

internal fun AccessibilityNodeInfo.matchesExactDescription(value: String): Boolean {
    return contentDescription?.toString()?.trim()?.equals(value, ignoreCase = true) == true
}

internal fun AccessibilityNodeInfo.matchesExactTextOrDescription(value: String): Boolean {
    return matchesExactDescription(value) ||
        text?.toString()?.trim()?.equals(value, ignoreCase = true) == true
}

internal fun AccessibilityNodeInfo.matchesExactCandidateSet(candidateSet: Set<String>): Boolean {
    val desc = contentDescription?.toString()?.trim()?.lowercase()
    val nodeText = text?.toString()?.trim()?.lowercase()
    return (desc != null && desc in candidateSet) || (nodeText != null && nodeText in candidateSet)
}

internal fun AccessibilityNodeInfo.nodeBounds(): NodeBounds? {
    val rect = Rect()
    getBoundsInScreen(rect)
    if (rect.isEmpty) return null

    return NodeBounds(
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom
    )
}

