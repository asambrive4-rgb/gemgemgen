// 역할: 화면 노드 트리를 지연 평가 방식으로 순회하며 무관한 시스템 뷰 서브트리를 조기 스킵하여 Binder IPC를 최소화합니다.
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

    /**
     * root에서 시작하여 깊이 우선(DFS) 방식으로 노드를 순회하는 [Sequence]를 생성한다.
     * Sequence의 지연 평가 특성으로 인해 [firstOrNull], [any], [take] 등의 조기 종료(Early-Exit)
     * 연산자를 사용할 때 불필요한 하위 노드 IPC(getChild) 호출과 대량 리스트 객체 할당을 방지한다.
     * 또한 무관한 시스템 UI나 키보드 서브트리는 자식 탐색을 조기 스킵(Pruning)하여 Binder IPC 부담을 줄인다.
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

        suspend fun SequenceScope<AccessibilityNodeInfo>.traverse(node: AccessibilityNodeInfo) {
            val pkg = node.packageName?.toString()
            if (pkg != null) {
                if (pkg in PRUNABLE_SYSTEM_PACKAGES) {
                    totalPrunedSubtrees++
                    return
                }
                if (packageFilter != null && !packageFilter(node.packageName)) {
                    totalPrunedSubtrees++
                    return
                }
            }

            if (packageFilter == null || packageFilter(node.packageName)) {
                yield(node)
            }
            val childCount = node.childCount
            for (i in 0 until childCount) {
                totalGetChildCalls++
                val child = node.getChild(i) ?: continue
                traverse(child)
            }
        }

        traverse(root)
    }
}
