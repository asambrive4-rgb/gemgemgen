// 역할: Google 앱 상세 설정 화면을 열어 강제 중지를 통해 메모리를 정리하도록 돕습니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.gemgemgen.automation.usecase.MemoryCleanupGateway
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult
import com.example.gemgemgen.core.AppDefaults

class AndroidMemoryCleanupGateway(
    private val context: Context,
    private val targetPackageName: String = AppDefaults.GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME
) : MemoryCleanupGateway {
    override suspend fun cleanMemory(): MemoryCleanupResult {
        val service = GeminiAccessibilityService.activeService
            ?: return MemoryCleanupResult.AccessibilityUnavailable

        return service.cleanDeviceMemory {
            val launchIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", targetPackageName, null)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }

            try {
                context.startActivity(launchIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
