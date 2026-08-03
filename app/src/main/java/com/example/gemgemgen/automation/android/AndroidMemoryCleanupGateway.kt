package com.example.gemgemgen.automation.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.usecase.MemoryCleanupGateway
import com.example.gemgemgen.automation.usecase.MemoryCleanupResult

class AndroidMemoryCleanupGateway(
    private val context: Context
) : MemoryCleanupGateway {
    override suspend fun cleanMemory(): MemoryCleanupResult {
        val service = GeminiAccessibilityService.activeService
            ?: return MemoryCleanupResult.AccessibilityUnavailable

        return service.cleanDeviceMemory {
            val launchIntent = Intent(DEVICE_CARE_DASHBOARD_ACTION).apply {
                setPackage(DEVICE_CARE_PACKAGE_NAME)
                component = ComponentName(
                    DEVICE_CARE_PACKAGE_NAME,
                    DEVICE_CARE_DASHBOARD_ACTIVITY
                )
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

    private companion object {
        const val DEVICE_CARE_PACKAGE_NAME = "com.samsung.android.lool"
        const val DEVICE_CARE_DASHBOARD_ACTION =
            "com.samsung.android.sm.ACTION_DASHBOARD"
        const val DEVICE_CARE_DASHBOARD_ACTIVITY =
            "com.samsung.android.sm.score.ui.ScoreBoardActivity"
    }
}
