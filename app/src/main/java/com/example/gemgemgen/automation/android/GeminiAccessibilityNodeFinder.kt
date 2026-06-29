package com.example.gemgemgen.automation.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal class GeminiAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?,
    private val inputResourceId: String
) {
    fun findInputNode(): AccessibilityNodeInfo? {
        findNodeByViewId(inputResourceId)?.let { return it }

        return rootProvider()
            ?.let(::flattenNodes)
            ?.firstOrNull { node ->
                node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                    node.isEditable
            }
    }

    fun findNodeByTextOrDescription(value: String): AccessibilityNodeInfo? {
        return rootProvider()
            ?.let(::flattenNodes)
            ?.firstOrNull { node -> node.matchesTextOrDescription(value) }
    }

    fun findNewChatNearestToSearch(): AccessibilityNodeInfo? {
        val nodes = rootProvider()?.let(::flattenNodes) ?: return null
        val searchNode = nodes.firstOrNull { node ->
            node.matchesTextOrDescription("채팅 검색")
        } ?: return null
        val candidates = nodes.filter { node ->
            node.matchesTextOrDescription("새 채팅")
        }.mapNotNull { node ->
            val bounds = node.nodeBounds() ?: return@mapNotNull null
            node to bounds
        }
        val searchBounds = searchNode.nodeBounds() ?: return null

        return NearestNodeSelector.nearestTo(
            anchor = searchBounds,
            candidates = candidates
        ) { it.second }?.first
    }

    fun findSidebarScrollableNode(): AccessibilityNodeInfo? {
        val root = rootProvider() ?: return null
        val rootBounds = root.nodeBounds()
        val nodes = flattenNodes(root)
        if (!nodes.hasOpenSidebarSignal(rootBounds)) return null

        return nodes.asSequence()
            .filter { node -> node.isScrollable }
            .mapNotNull { node ->
                val bounds = node.nodeBounds() ?: return@mapNotNull null
                node to bounds
            }
            .filter { (_, bounds) ->
                bounds.isLikelySidebarScrollable(rootBounds = rootBounds)
            }
            .maxByOrNull { (_, bounds) -> bounds.area }
            ?.first
    }

    private fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        return try {
            rootProvider()
                ?.findAccessibilityNodeInfosByViewId(viewId)
                ?.firstOrNull()
        } catch (_: RuntimeException) {
            null
        }
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

    private fun AccessibilityNodeInfo.matchesTextOrDescription(value: String): Boolean {
        return text?.toString()?.contains(value, ignoreCase = true) == true ||
            contentDescription?.toString()?.contains(value, ignoreCase = true) == true
    }

    private fun List<AccessibilityNodeInfo>.hasOpenSidebarSignal(
        rootBounds: NodeBounds?
    ): Boolean {
        return any { node ->
            node.matchesTextOrDescription("사이드바 닫기")
        } || any { node ->
            node.matchesTextOrDescription("Gemini") &&
                node.nodeBounds()?.isLikelySidebarHeader(rootBounds) == true
        }
    }

    private fun AccessibilityNodeInfo.nodeBounds(): NodeBounds? {
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

    private fun NodeBounds.isLikelySidebarScrollable(
        rootBounds: NodeBounds?
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        if (rootBounds == null) return true

        val tallEnoughForSidebarList = height >= rootBounds.height / 3
        val startsNearLeftEdge = left <= rootBounds.left + rootBounds.width / 4
        val centerIsNotOnRightPane = centerX <= rootBounds.centerX

        return tallEnoughForSidebarList && startsNearLeftEdge && centerIsNotOnRightPane
    }

    private fun NodeBounds.isLikelySidebarHeader(rootBounds: NodeBounds?): Boolean {
        if (rootBounds == null) return true

        return top <= rootBounds.top + rootBounds.height / 8 &&
            left <= rootBounds.left + rootBounds.width / 2 &&
            centerX <= rootBounds.centerX
    }

    private val NodeBounds.width: Int
        get() = right - left

    private val NodeBounds.height: Int
        get() = bottom - top

    private val NodeBounds.area: Long
        get() = width.toLong() * height.toLong()
}

