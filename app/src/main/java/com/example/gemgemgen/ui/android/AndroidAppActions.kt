package com.example.gemgemgen.ui.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.gemgemgen.automation.android.FloatingAutomationBarController
import com.example.gemgemgen.ui.MainViewModel
import com.example.gemgemgen.ui.WildcardManagerViewModel

internal fun runAutomationWithOverlayCheck(
    context: Context,
    activity: ComponentActivity?,
    viewModel: MainViewModel,
    floatingBarController: FloatingAutomationBarController?,
    onCancelAutomation: () -> Unit,
    onAutomationFinished: () -> Unit
) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(
            context,
            "플로팅 바를 쓰려면 다른 앱 위에 표시 권한이 필요합니다.",
            Toast.LENGTH_LONG
        ).show()
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
        return
    }

    val accepted = viewModel.runAutomation()
    if (accepted && viewModel.uiState.value.isRunning) {
        floatingBarController?.showOrUpdate(
            uiStateFlow = viewModel.uiState,
            onCancelAutomation = onCancelAutomation,
            onAutomationFinished = onAutomationFinished
        )
        activity?.moveTaskToBack(true)
    }
}

internal fun handleWildcardFolderSelected(
    context: Context,
    uri: Uri,
    viewModel: MainViewModel,
    wildcardViewModel: WildcardManagerViewModel?
) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        viewModel.saveWildcardFolder(uri.toString())
        wildcardViewModel?.onFolderChanged()
    } catch (error: SecurityException) {
        viewModel.showWildcardFolderSaveError(
            "폴더 권한 저장 실패: ${error.message ?: "다시 선택해주세요."}"
        )
    }
}
