// 역할: 네이티브 인덱스 우선 조회, 역방향 지연 순회 및 스냅샷 캐시로 Binder IPC를 최소화하며 Flow 노드를 조기 종료 탐색합니다.
package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class FlowAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?
) {
    private val snapshotCache = AccessibilityNodeSnapshotCache()

    fun invalidateCache() {
        snapshotCache.clear()
    }

    fun findInputNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("input", root) {
            nodesSequence(root).firstOrNull { node ->
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                    node.isEditable
            }
        }
    }

    fun findSendNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("send", root) {
            for (candidate in SEND_DESCRIPTIONS) {
                val trimmed = candidate.trim()
                val nativeMatch = findNodesByText(root, trimmed).firstOrNull { node ->
                    node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true ||
                        node.text?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
                }
                if (nativeMatch != null) return@getOrFind nativeMatch
            }

            val candidateSet = SEND_DESCRIPTIONS_SET
            nodesSequence(root).firstOrNull { node ->
                val desc = node.contentDescription?.toString()?.trim()?.lowercase()
                val text = node.text?.toString()?.trim()?.lowercase()
                (desc != null && desc in candidateSet) ||
                    (text != null && text in candidateSet)
            }
        }
    }

    /**
     * 모델 선택 바텀시트가 현재 열려 있는지 확인한다.
     * (여러 모델 후보 노드가 화면에 동시에 2개 이상 노출되는 상태)
     */
    fun isModelSheetOpen(): Boolean {
        val root = rootProvider() ?: return false
        val count = nodesSequence(root)
            .filter { node ->
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                MODEL_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) }
            }
            .take(2)
            .count()
        return count >= 2
    }

    /**
     * 현재 선택된 모델 버튼(옵션 패널 하단의 바)을 찾는다.
     * 1단계: 스냅샷 캐시(400ms TTL)를 적용하여 동일 트리 스냅샷 내 중복 IPC를 차단한다.
     * 2단계: Fast Path로 네이티브 텍스트 인덱스를 조회하여 화면 최하단(바텀시트 영역) 노드를 즉시 반환한다.
     * 3단계: Fallback 시 역방향 지연 순회(Reverse DFS)로 화면 하단부터 탐색하여 첫 매칭 노드를 조기 종료(Early Exit)한다.
     */
    fun findCurrentModelSelectorButton(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return snapshotCache.getOrFind("current_model_selector", root) {
            // Fast Path: 네이티브 텍스트 인덱스로 단 1회 Binder IPC 고속 조회
            val nativeCandidates = mutableListOf<AccessibilityNodeInfo>()
            for (keyword in MODEL_KEYWORDS) {
                val matches = findNodesByText(root, keyword)
                if (matches.isNotEmpty()) {
                    nativeCandidates.addAll(matches)
                }
            }
            if (nativeCandidates.isNotEmpty()) {
                val bottomMost = nativeCandidates.maxByOrNull { node ->
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    bounds.bottom
                }
                if (bottomMost != null) return@getOrFind bottomMost
            }

            // Fallback: 역방향 지연 순회로 화면 하단부터 우선 탐색하여 첫 매칭 노드 조기 반환 (트리 완주 방지)
            nodesSequenceReverse(root).firstOrNull { node ->
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                MODEL_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) }
            }
        }
    }

    /**
     * 모델 선택 바텀시트/목록에서 목표 모델(예: 'Nano Banana Pro') 항목을 찾는다.
     */
    fun findModelOptionInList(modelName: String = NANO_BANANA_PRO): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val trimmed = modelName.trim()
        return nodesSequence(root).firstOrNull { node ->
            node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
        }
    }

    /**
     * 이미지 생성 개수(1~4) 탭 노드를 찾는다 (예: 'x1\n탭 4개 중 1번째').
     */
    fun findImageCountOption(count: Int): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val targetPrefix = "x$count"
        return nodesSequence(root).firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            desc.startsWith(targetPrefix, ignoreCase = true) || desc.equals(targetPrefix, ignoreCase = true)
        }
    }

    /**
     * 현재 옵션 패널에서 선택되어 있는 이미지 생성 개수(1~4)를 반환한다.
     */
    fun findSelectedImageCount(): Int? {
        val root = rootProvider() ?: return null
        return nodesSequence(root).firstNotNullOfOrNull { node ->
            if (!node.isSelected) return@firstNotNullOfOrNull null
            val desc = node.contentDescription?.toString()?.trim() ?: return@firstNotNullOfOrNull null
            when {
                desc.startsWith("x1", ignoreCase = true) -> 1
                desc.startsWith("x2", ignoreCase = true) -> 2
                desc.startsWith("x3", ignoreCase = true) -> 3
                desc.startsWith("x4", ignoreCase = true) -> 4
                else -> null
            }
        }
    }

    fun findOptionPanelToggle(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return nodesSequence(root).firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            OPTION_TOGGLE_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) } &&
                desc.contains("x", ignoreCase = true)
        }
    }

    private fun findNodesByText(root: AccessibilityNodeInfo, value: String): List<AccessibilityNodeInfo> {
        return try {
            root.findAccessibilityNodeInfosByText(value)
                ?.filter { it.packageName?.toString() == AppDefaults.FLOW_PACKAGE_NAME }
                .orEmpty()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun nodesSequence(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> {
        return AccessibilityNodeTraversal.lazyTraverse(root) { pkg ->
            pkg?.toString() == AppDefaults.FLOW_PACKAGE_NAME
        }
    }

    private fun nodesSequenceReverse(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> {
        return AccessibilityNodeTraversal.lazyTraverseReverse(root) { pkg ->
            pkg?.toString() == AppDefaults.FLOW_PACKAGE_NAME
        }
    }

    internal companion object {
        const val NANO_BANANA_PRO = "Nano Banana Pro"
        val SEND_DESCRIPTIONS = listOf("생성", "Generate", "만들기", "Create")
        val SEND_DESCRIPTIONS_SET = SEND_DESCRIPTIONS.map { it.trim().lowercase() }.toSet()
        val MODEL_KEYWORDS = listOf("Nano Banana", "Banana", "Imagen")
        val OPTION_TOGGLE_KEYWORDS = listOf("이미지", "Image")
    }
}
