package com.example.gemgemgen.automation.android

import android.content.Context
import android.provider.Settings
import com.example.gemgemgen.automation.usecase.OverlayPermissionGateway

class AndroidOverlayPermissionGateway(
    private val context: Context
) : OverlayPermissionGateway {
    override fun isGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
