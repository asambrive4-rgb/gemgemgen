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

    fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }

        return false
    }

    fun findClickableNodeOrParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node

        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }

        return null
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
}

