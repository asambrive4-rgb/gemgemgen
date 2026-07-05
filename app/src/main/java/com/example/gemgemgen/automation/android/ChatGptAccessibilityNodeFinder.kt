package com.example.gemgemgen.automation.android

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class ChatGptAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?
) {
    private var cachedRoot: AccessibilityNodeInfo? = null
    private var cachedNodes: List<AccessibilityNodeInfo> = emptyList()
    private var cachedAtMillis: Long = 0L

    fun findInputNode(): AccessibilityNodeInfo? {
        return nodes().firstOrNull { node ->
            node.className?.toString()?.contains("EditText", ignoreCase = true) == true ||
                node.isEditable
        }
    }

    fun findMenuNode(): AccessibilityNodeInfo? {
        return findNodeByDescription("메뉴")
    }

    fun isMenuOpen(): Boolean {
        return findNodeByDescription("검색") != null &&
            findNodeByDescription("계정 설정") != null
    }

    fun findInitialChatNode(): AccessibilityNodeInfo? {
        return nodes().firstOrNull { node ->
            node.text?.toString()?.equals("채팅", ignoreCase = true) == true
        }
    }

    fun findNewChatNode(): AccessibilityNodeInfo? {
        return findNodeByDescription("새 채팅")
    }

    fun findSendNode(): AccessibilityNodeInfo? {
        return findNodeByDescription("메시지 보내기")
    }

    fun findTooManyRequestsCloseNode(): AccessibilityNodeInfo? {
        val hasTooManyRequestsMessage = nodes().any { node ->
            node.text?.toString()?.contains(TOO_MANY_REQUESTS_MESSAGE, ignoreCase = true) == true
        }
        if (!hasTooManyRequestsMessage) return null

        return findNodeByDescription(TOO_MANY_REQUESTS_CLOSE_DESCRIPTION)
    }

    private fun findNodeByDescription(value: String): AccessibilityNodeInfo? {
        return nodes().firstOrNull { node ->
            node.contentDescription?.toString()?.equals(value, ignoreCase = true) == true
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = rootProvider() ?: return emptyList()
        val now = SystemClock.uptimeMillis()
        if (root == cachedRoot && now - cachedAtMillis <= NODE_SNAPSHOT_CACHE_MS) {
            return cachedNodes
        }

        val nodes = mutableListOf<AccessibilityNodeInfo>()

        fun visit(node: AccessibilityNodeInfo) {
            if (node.packageName?.toString() == AppDefaults.CHATGPT_PACKAGE_NAME) {
                nodes += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(::visit)
            }
        }

        visit(root)
        return nodes.also {
            cachedRoot = root
            cachedNodes = it
            cachedAtMillis = now
        }
    }

    private companion object {
        const val NODE_SNAPSHOT_CACHE_MS = 32L
        const val TOO_MANY_REQUESTS_MESSAGE = "Too many requests"
        const val TOO_MANY_REQUESTS_CLOSE_DESCRIPTION = "닫기"
    }
}
