// 역할: Flow 앱 화면에서 텍스트 입력창과 생성 버튼 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

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
        return nodes().firstOrNull { node ->
            node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                node.isEditable
        }
    }

    fun findSendNode(): AccessibilityNodeInfo? {
        val nodeList = nodes()
        for (candidate in SEND_DESCRIPTIONS) {
            val trimmed = candidate.trim()
            val match = nodeList.firstOrNull { node ->
                node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true ||
                    node.text?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
            }
            if (match != null) return match
        }
        return null
    }

    /**
     * 모델 선택 바텀시트가 현재 열려 있는지 확인한다.
     * (여러 모델 후보 노드가 화면에 동시에 2개 이상 노출되는 상태)
     */
    fun isModelSheetOpen(): Boolean {
        val count = nodes().count { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            MODEL_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) }
        }
        return count >= 2
    }

    /**
     * 현재 선택된 모델 버튼(옵션 패널 하단의 바)을 찾는다.
     */
    fun findCurrentModelSelectorButton(): AccessibilityNodeInfo? {
        val nodeList = nodes()
        return nodeList.lastOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            MODEL_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) } &&
                node.isClickable
        }
    }

    /**
     * 모델 선택 바텀시트/목록에서 목표 모델(예: 'Nano Banana Pro') 항목을 찾는다.
     */
    fun findModelOptionInList(modelName: String = NANO_BANANA_PRO): AccessibilityNodeInfo? {
        val trimmed = modelName.trim()
        return nodes().firstOrNull { node ->
            node.contentDescription?.toString()?.trim()?.equals(trimmed, ignoreCase = true) == true
        }
    }

    /**
     * 생성 옵션 패널(비율, 개수, 모델)을 펼치는 토글 버튼(예: '이미지\nx1', '이미지\nx4')을 찾는다.
     */
    /**
     * 이미지 생성 개수(1~4) 탭 노드를 찾는다 (예: 'x1\n탭 4개 중 1번째').
     */
    fun findImageCountOption(count: Int): AccessibilityNodeInfo? {
        val targetPrefix = "x$count"
        return nodes().firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            (desc.startsWith(targetPrefix, ignoreCase = true) || desc.equals(targetPrefix, ignoreCase = true)) &&
                node.isClickable
        }
    }

    /**
     * 현재 옵션 패널에서 선택되어 있는 이미지 생성 개수(1~4)를 반환한다.
     */
    fun findSelectedImageCount(): Int? {
        for (count in 1..4) {
            val node = findImageCountOption(count)
            if (node?.isSelected == true) return count
        }
        return null
    }

    fun findOptionPanelToggle(): AccessibilityNodeInfo? {
        val nodeList = nodes()
        return nodeList.firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            OPTION_TOGGLE_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) } &&
                desc.contains("x", ignoreCase = true)
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        return snapshotCache.getOrLoad(rootProvider()) { root ->
            val nodes = mutableListOf<AccessibilityNodeInfo>()

            fun visit(node: AccessibilityNodeInfo) {
                if (node.packageName?.toString() == AppDefaults.FLOW_PACKAGE_NAME) {
                    nodes += node
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(::visit)
                }
            }

            visit(root)
            nodes
        }
    }

    internal companion object {
        const val NANO_BANANA_PRO = "Nano Banana Pro"
        val SEND_DESCRIPTIONS = listOf("생성", "Generate", "만들기", "Create")
        val MODEL_KEYWORDS = listOf("Nano Banana", "Banana", "Imagen")
        val OPTION_TOGGLE_KEYWORDS = listOf("이미지", "Image")
    }
}
