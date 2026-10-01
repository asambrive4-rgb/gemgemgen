// 역할: 현재 기기의 접근성 자동화 런타임 인스턴스, 게이트웨이 제공자, 대상 앱 실행기를 제공하는 안드로이드 연결부입니다.
package com.example.gemgemgen.automation.android

import android.content.Context
import android.content.Intent
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.automation.usecase.ExecuteAutomationLoopUseCase
import com.example.gemgemgen.automation.usecase.ManageAnimationScaleUseCase
import com.example.gemgemgen.automation.usecase.ManageImeUseCase
import com.example.gemgemgen.automation.usecase.PromptAutomationGateway
import com.example.gemgemgen.automation.usecase.PromptAutomationGatewayProvider
import com.example.gemgemgen.automation.usecase.TargetAppLauncher
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGateway
import com.example.gemgemgen.automation.usecase.VariationPromptAutomationGatewayProvider
import com.example.gemgemgen.core.AppDefaults
import com.example.gemgemgen.wildcard.android.AndroidWildcardSetRepository

internal object ProcessAutomationHolder {
    @Volatile
    private var instance: ExecuteAutomationLoopUseCase? = null
    private val lock = Any()

    fun getOrCreate(create: () -> ExecuteAutomationLoopUseCase): ExecuteAutomationLoopUseCase {
        instance?.let { return it }
        return synchronized(lock) {
            instance ?: create().also { instance = it }
        }
    }

    fun current(): ExecuteAutomationLoopUseCase? = instance

    fun onAccessibilityLost() {
        current()?.onAccessibilityLost()
    }
}

object AndroidAutomationRuntimeProvider {
    fun get(context: Context): ExecuteAutomationLoopUseCase {
        val appContext = context.applicationContext
        return ProcessAutomationHolder.getOrCreate {
            ExecuteAutomationLoopUseCase(
                manageImeUseCase = ManageImeUseCase(AndroidImeSettings(appContext)),
                wildcardSetRepository = AndroidWildcardSetRepository(appContext),
                promptGatewayProvider = ActivePromptAutomationGatewayProvider,
                targetAppLauncher = AndroidTargetAppLauncher(appContext),
                manageAnimationScaleUseCase = ManageAnimationScaleUseCase(
                    settings = AndroidAnimationScaleSettings(appContext),
                    backupStore = SharedPreferencesAnimationScaleBackupStore(appContext)
                )
            )
        }
    }
}

object ActivePromptAutomationGatewayProvider : PromptAutomationGatewayProvider, VariationPromptAutomationGatewayProvider {
    override fun current(targetApp: AutomationTargetApp): PromptAutomationGateway? {
        return GeminiAccessibilityService.activeService?.gatewayFor(targetApp)
    }

    override fun current(): VariationPromptAutomationGateway? {
        return current(AutomationTargetApp.GEMINI)
    }
}

object ActiveVariationPromptAutomationGatewayProvider : VariationPromptAutomationGatewayProvider {
    override fun current(): VariationPromptAutomationGateway? =
        ActivePromptAutomationGatewayProvider.current()
}

class AndroidTargetAppLauncher(
    private val context: Context
) : TargetAppLauncher {
    override fun launch(targetApp: AutomationTargetApp): Boolean {
        val packageName = when (targetApp) {
            AutomationTargetApp.GEMINI -> AppDefaults.GEMINI_PACKAGE_NAME
            AutomationTargetApp.CHATGPT -> AppDefaults.CHATGPT_PACKAGE_NAME
            AutomationTargetApp.FLOW -> AppDefaults.FLOW_PACKAGE_NAME
        }
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?: return false

        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
