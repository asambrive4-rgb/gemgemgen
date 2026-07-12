package com.example.gemgemgen.core.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.gemgemgen.core.AppDefaults

/**
 * 외부 브라우저로 URL을 연다.
 * Grok 로그인 등에서 Firefox를 우선 사용한다.
 */
class AndroidExternalBrowserLauncher(
    context: Context
) {
    private val appContext = context.applicationContext

    /**
     * @return true면 어떤 브라우저든 실행 요청에 성공.
     */
    fun openUrlPreferFirefox(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false

        if (isPackageInstalled(AppDefaults.FIREFOX_PACKAGE_NAME)) {
            val firefoxIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(AppDefaults.FIREFOX_PACKAGE_NAME)
            }
            if (start(firefoxIntent)) return true
        }

        val defaultIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(defaultIntent)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun start(intent: Intent): Boolean {
        return try {
            if (intent.resolveActivity(appContext.packageManager) == null) {
                // package 고정 시 resolve가 null일 수 있어 그래도 시도
                if (intent.`package` == null) return false
            }
            appContext.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
