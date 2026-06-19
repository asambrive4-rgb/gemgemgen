package com.example.gemgemgen.core.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.gemgemgen.core.ClipboardGateway

class AndroidClipboardGateway(
    private val context: Context
) : ClipboardGateway {
    override fun readText(): String {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        return clipboardManager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
    }

    override fun writeText(text: String) {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText("wildcard", text))
    }
}
