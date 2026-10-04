// 역할: 안드로이드 시스템 클립보드 서비스를 사용하여 텍스트 복사·조회를 수행하고 Composable용 헬퍼를 제공합니다.
package com.example.gemgemgen.core.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

/**
 * Compose UI 트리에서 일관된 클립보드 입출력을 수행할 수 있도록 ClipboardGateway 인스턴스를 제공합니다.
 */
@Composable
fun rememberClipboardGateway(): ClipboardGateway {
    val context = LocalContext.current
    return remember(context) { AndroidClipboardGateway(context) }
}
