// 역할: 자식 지연 취득, 부모 패키지 상속 기반 가상 노드 Pruning 및 역방향 순회로 Binder IPC를 최소화하는 DFS 엔진
package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo

internal object AccessibilityNodeTraversal {
    private val PRUNABLE_SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "com.sec.android.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )

    var totalGetChildCalls = 0L
        private set
    var totalPrunedSubtrees = 0L
        private set

    fun resetStats() {
        totalGetChildCalls = 0L
        totalPrunedSubtrees = 0L
    }

    private class DeferredChild(
        val parent: AccessibilityNodeInfo,
        val index: Int,
        val inheritedPackageName: String? = null
    )

    /**
     * root에서 시작하여 깊이 우선(DFS) 방식으로 노드를 순회하는 [Sequence]를 생성한다.
     * 비재귀 스택 기반 Sequence와 자식 노드 지연 취득(Lazy Child Resolution) 특성으로 인해,
     * [firstOrNull], [any], [take] 등의 조기 종료(Early-Exit) 연산자 사용 시
     * 스택에 적재된 나머지 미방문 자식 노드의 Binder IPC(getChild) 호출과 대량 리스트 객체 할당을 원천 방지한다.
     * 또한 Compose 가상 노드(packageName == null)는 부모 패키지명을 상속하여
     * 시스템 UI나 키보드 서브트리를 조기 스킵(Pruning)하고 대상 앱 가상 노드를 안전하게 보존한다.
     *
     * @param root 순회를 시작할 최상위 노드. null인 경우 빈 Sequence를 반환한다.
     * @param packageFilter 노드의 packageName이 조건에 맞을 때만 시퀀스에 포함시킬 필터.
     *                      null인 경우 모든 패키지 노드를 포함한다.
     */
    fun lazyTraverse(
        root: AccessibilityNodeInfo?,
        packageFilter: ((CharSequence?) -> Boolean)? = null
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
                totalGetChildCalls++
                val child = deferred.parent.getChild(deferred.index) ?: continue
                child to deferred.inheritedPackageName
            }

            val effectivePkg = node.packageName?.toString() ?: inheritedPkg
            if (effectivePkg != null) {
                if (effectivePkg in PRUNABLE_SYSTEM_PACKAGES) {
                    totalPrunedSubtrees++
                    continue
                }
                if (packageFilter != null && !packageFilter(effectivePkg)) {
                    totalPrunedSubtrees++
                    continue
                }
            } else if (packageFilter != null && !packageFilter(null)) {
                totalPrunedSubtrees++
                continue
            }

            if (packageFilter == null || packageFilter(effectivePkg)) {
                yield(node)
            }

            val childCount = node.childCount
            for (i in childCount - 1 downTo 0) {
                stack.add(DeferredChild(node, i, effectivePkg))
            }
        }
    }

    /**
     * root에서 시작하여 역방향 깊이 우선(Reverse DFS) 방식으로 노드를 순회하는 [Sequence]를 생성한다.
     * 화면 하단(트리의 마지막 자식 및 우측 서브트리)부터 우선 탐색하므로,
     * 하단 바, 바텀시트, 화면 아래쪽 버튼 등을 찾을 때 [firstOrNull]을 사용하면
     * 전체 트리를 순회하지 않고 단 몇 개의 노드만 검사한 뒤 즉시 조기 종료(Early-Exit)할 수 있다.
     */
    fun lazyTraverseReverse(
        root: AccessibilityNodeInfo?,
        packageFilter: ((CharSequence?) -> Boolean)? = null
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
                totalGetChildCalls++
                val child = deferred.parent.getChild(deferred.index) ?: continue
                child to deferred.inheritedPackageName
            }

            val effectivePkg = node.packageName?.toString() ?: inheritedPkg
            if (effectivePkg != null) {
                if (effectivePkg in PRUNABLE_SYSTEM_PACKAGES) {
                    totalPrunedSubtrees++
                    continue
                }
                if (packageFilter != null && !packageFilter(effectivePkg)) {
                    totalPrunedSubtrees++
                    continue
                }
            } else if (packageFilter != null && !packageFilter(null)) {
                totalPrunedSubtrees++
                continue
            }

            if (packageFilter == null || packageFilter(effectivePkg)) {
                yield(node)
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                stack.add(DeferredChild(node, i, effectivePkg))
            }
        }
    }
}
