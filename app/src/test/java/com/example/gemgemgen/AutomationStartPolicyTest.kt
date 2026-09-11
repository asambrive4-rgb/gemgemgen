// 역할: 자동화 실행 시작 전 필수 조건 검사 정책을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.AutomationStartPolicy
import com.example.gemgemgen.automation.domain.AutomationTargetApp
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationStartPolicyTest {

    private val readyEnvironment = EnvironmentStatus(
        isGeminiInstalled = true,
        isChatGptInstalled = true,
        isFlowInstalled = true,
        isAccessibilityServiceEnabled = true,
        hasWriteSecureSettingsPermission = true,
        isWildcardDirectoryAccessible = true
    )

    private val unreadyEnvironment = EnvironmentStatus(
        isGeminiInstalled = false,
        isChatGptInstalled = false,
        isAccessibilityServiceEnabled = false,
        hasWriteSecureSettingsPermission = false,
        isWildcardDirectoryAccessible = false
    )

    private val readyRemoteSenderStatus = RemoteAutomationStatus(
        mode = AutomationMode.SENDER,
        discoveredDeviceName = "TestDevice",
        isPaired = true
    )

    @Test
    fun hasPromptTemplate_validatesNonBlankString() {
        assertFalse(AutomationStartPolicy.hasPromptTemplate(""))
        assertFalse(AutomationStartPolicy.hasPromptTemplate("   "))
        assertFalse(AutomationStartPolicy.hasPromptTemplate("\n\t"))
        assertTrue(AutomationStartPolicy.hasPromptTemplate("Hello World"))
        assertTrue(AutomationStartPolicy.hasPromptTemplate(" a "))
    }

    @Test
    fun hasRunRequirements_checksEnvironmentReadinessAndPrompt() {
        assertTrue(
            AutomationStartPolicy.hasRunRequirements(
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt"
            )
        )
        assertFalse(
            AutomationStartPolicy.hasRunRequirements(
                environmentStatus = unreadyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt"
            )
        )
        assertFalse(
            AutomationStartPolicy.hasRunRequirements(
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "   "
            )
        )
    }

    @Test
    fun canRun_inNormalMode_checksRequirementsAndRunningState() {
        // Ready and not running -> true
        assertTrue(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )

        // Ready but already running -> false
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "prompt",
                isRunning = true,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )

        // Unready environment and not running -> false
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = unreadyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "prompt",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )

        // Blank prompt and not running -> false
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus()
            )
        )
    }

    @Test
    fun canRun_inSenderMode_checksPromptTemplateRemoteSendStatusAndRunningState() {
        // Can send, has prompt, not running -> true (even if local environment is not ready)
        assertTrue(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = unreadyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "remote prompt",
                isRunning = false,
                remoteAutomationStatus = readyRemoteSenderStatus
            )
        )

        // Cannot send (e.g. not paired) -> false
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "remote prompt",
                isRunning = false,
                remoteAutomationStatus = readyRemoteSenderStatus.copy(isPaired = false)
            )
        )

        // Blank prompt -> false
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "   ",
                isRunning = false,
                remoteAutomationStatus = readyRemoteSenderStatus
            )
        )

        // Already running -> false
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.SENDER,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "remote prompt",
                isRunning = true,
                remoteAutomationStatus = readyRemoteSenderStatus
            )
        )
    }

    @Test
    fun canRun_inReceiverMode_isAlwaysFalse() {
        assertFalse(
            AutomationStartPolicy.canRun(
                mode = AutomationMode.RECEIVER,
                environmentStatus = readyEnvironment,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "test prompt",
                isRunning = false,
                remoteAutomationStatus = readyRemoteSenderStatus
            )
        )
    }
}
