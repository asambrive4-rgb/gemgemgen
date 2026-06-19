package com.example.gemgemgen.automation.android

import android.view.accessibility.AccessibilityNodeInfo
import com.example.gemgemgen.core.AppDefaults

internal class ChatGptAccessibilityNodeFinder(
    private val rootProvider: () -> AccessibilityNodeInfo?
) {
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

    private fun findNodeByDescription(value: String): AccessibilityNodeInfo? {
        return nodes().firstOrNull { node ->
            node.contentDescription?.toString()?.equals(value, ignoreCase = true) == true
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = rootProvider() ?: return emptyList()
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
        return nodes
    }
}
