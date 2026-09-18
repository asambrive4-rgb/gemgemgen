// 역할: 안드로이드 접근성 서비스와 연동되는 자동화 런타임 인스턴스를 제공합니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.usecase.LastRunSnapshotStore
import com.example.gemgemgen.automation.usecase.ManageAnimationScaleUseCase
import com.example.gemgemgen.automation.usecase.ManageImeUseCase
import com.example.gemgemgen.core.android.AndroidClipboardGateway
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository

object AndroidAutomationRuntimeProvider {
    fun get(context: Context): ExecuteAutomationLoopUseCase {
        val appContext = context.applicationContext
        return ProcessAutomationHolder.getOrCreate {
            ExecuteAutomationLoopUseCase(
                manageImeUseCase = ManageImeUseCase(AndroidImeSettings(appContext)),
                lastRunSnapshotStore = LastRunSnapshotStore(
                    SharedPreferencesLastRunSnapshotRepository(appContext)
                ),
                clipboardGateway = AndroidClipboardGateway(appContext),
                wildcardSetRepository = AndroidWildcardSetRepository(appContext),
                promptGatewayProvider = ActivePromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext),
                manageAnimationScaleUseCase = ManageAnimationScaleUseCase(
                    settings = AndroidAnimationScaleSettings(appContext),
                    backupStore = SharedPreferencesAnimationScaleBackupStore(appContext)
                ),
                promptHistoryStore = com.example.gemgemgen.automation.usecase.PromptHistoryStore(
                    SharedPreferencesPromptHistoryRepository(appContext)
                )
            )
        }
    }
}
