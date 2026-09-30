// 역할: 공통 NodeFinder 기반 위에서 Flow 전용 모델 선택 시트·생성 수 옵션·전송 노드를 탐색합니다.
package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class FlowAccessibilityNodeFinder(
    rootProvider: () -> AccessibilityNodeInfo?
) : BaseAccessibilityNodeFinder(rootProvider, setOf(AppDefaults.FLOW_PACKAGE_NAME)) {

    fun findInputNode(): AccessibilityNodeInfo? = findInputNodeBy()

    fun findSendNode(): AccessibilityNodeInfo? =
        cachedNode("send") { root ->
            findFirstNodeByExactCandidates(root, SEND_DESCRIPTIONS)
        }

    /**
     * 모델 선택 바텀시트가 현재 열려 있는지 확인한다.
     * (여러 모델 후보 노드가 화면에 동시에 2개 이상 노출되는 상태)
     */
    fun isModelSheetOpen(): Boolean {
        val root = rootProvider() ?: return false
        val count = traverseTargetPackage(root)
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
    fun findCurrentModelSelectorButton(): AccessibilityNodeInfo? =
        cachedNode("current_model_selector") { root ->
            val nativeCandidates = MODEL_KEYWORDS.flatMap { keyword ->
                findNodesByText(root, keyword)
            }
            if (nativeCandidates.isNotEmpty()) {
                val bottomMost = nativeCandidates.maxByOrNull { node ->
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    bounds.bottom
                }
                if (bottomMost != null) return@cachedNode bottomMost
            }

            traverseTargetPackage(root, reverse = true).firstOrNull { node ->
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                MODEL_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) }
            }
        }

    /**
     * 모델 선택 바텀시트/목록에서 목표 모델(예: 'Nano Banana Pro') 항목을 찾는다.
     */
    fun findModelOptionInList(modelName: String = NANO_BANANA_PRO): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val trimmed = modelName.trim()
        return traverseTargetPackage(root).firstOrNull { node ->
            node.matchesExactDescription(trimmed)
        }
    }

    /**
     * 이미지 생성 개수(1~4) 탭 노드를 찾는다 (예: 'x1\n탭 4개 중 1번째').
     */
    fun findImageCountOption(count: Int): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val targetPrefix = "x$count"
        return traverseTargetPackage(root).firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            desc.startsWith(targetPrefix, ignoreCase = true) || desc.equals(targetPrefix, ignoreCase = true)
        }
    }

    fun findOptionPanelToggle(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        return traverseTargetPackage(root).firstOrNull { node ->
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            OPTION_TOGGLE_KEYWORDS.any { keyword -> desc.contains(keyword, ignoreCase = true) } &&
                desc.contains("x", ignoreCase = true)
        }
    }

    internal companion object {
        const val NANO_BANANA_PRO = "Nano Banana Pro"
        val SEND_DESCRIPTIONS = listOf("생성", "Generate", "만들기", "Create")
        val MODEL_KEYWORDS = listOf("Nano Banana", "Banana", "Imagen")
        val OPTION_TOGGLE_KEYWORDS = listOf("이미지", "Image")
    }
}
