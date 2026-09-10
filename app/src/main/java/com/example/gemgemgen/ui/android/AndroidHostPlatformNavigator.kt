package com.example.gemgemgen.ui.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.example.gemgemgen.ui.MainActivity

/**
 * 화면(Composable) 계층에서 시스템 설정 인텐트 발송, 플랫폼 토스트,
 * 액티비티 포그라운드 전환 등 OS 세부사항(Details)을 격리하는 플랫폼 어댑터.
 */
class AndroidHostPlatformNavigator(private val context: Context) {

    /** 안드로이드 11+ 전체 파일 접근 권한 설정 화면으로 이동 */
    fun openAllFilesAccessSettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        try {
            context.startActivity(appSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    /** 다른 앱 위에 표시(오버레이) 권한 안내 및 설정 화면 이동 */
    fun openOverlayPermissionSettings() {
        Toast.makeText(
            context,
            "플로팅 바를 띄우려면 다른 앱 위에 표시 권한이 필요합니다.",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "오버레이 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** 시스템 접근성 설정 화면으로 이동 */
    fun openAccessibilitySettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "접근성 설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** 자동화 완료 후 메인 액티비티를 다시 화면 전면으로 복귀 */
    fun bringMainActivityToFront() {
        val appContext = context.applicationContext
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: Intent(appContext, MainActivity::class.java)
        appContext.startActivity(
            launchIntent
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    /** 외부 브라우저(Firefox 우선)로 URL 열기 및 실패 시 안내 토스트 표시 */
    fun openUrlPreferFirefox(
        browserLauncher: com.example.gemgemgen.core.android.AndroidExternalBrowserLauncher,
        url: String
    ) {
        val opened = browserLauncher.openUrlPreferFirefox(url)
        if (!opened) {
            Toast.makeText(
                context,
                "브라우저를 열 수 없습니다. Firefox 설치 여부를 확인해 주세요.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
