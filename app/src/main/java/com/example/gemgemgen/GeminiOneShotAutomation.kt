package com.example.gemgemgen

import android.content.Context
import android.content.Intent

class GeminiOneShotAutomation(
    private val imeManager: ImeManager,
    private val runLogger: RunLogger,
    private val clock: () -> Long,
    private val serviceProvider: () -> OneShotAutomationService?,
    private val launchGeminiApp: () -> Boolean
) {
    constructor(
        context: Context,
        imeManager: ImeManager = ImeManager.android(context),
        runLogger: RunLogger = RunLogger.android(context),
        clock: () -> Long = System::currentTimeMillis
    ) : this(
        imeManager = imeManager,
        runLogger = runLogger,
        clock = clock,
        serviceProvider = { GeminiAccessibilityService.activeService },
        launchGeminiApp = {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(AppDefaults.TARGET_PACKAGE_NAME)
            if (launchIntent == null) {
                false
            } else {
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }
    )

    private var currentRun: CurrentRun? = null

    fun run(onStateChange: (AutomationUiState) -> Unit) {
        if (currentRun != null) {
            onStateChange(AutomationUiState.Failure("이미 실행 중입니다."))
            return
        }

        val startedAtMillis = clock()
        val service = serviceProvider()
        if (service == null) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                lastStep = "접근성 서비스 확인",
                state = AutomationUiState.Failure("접근성 서비스가 켜져 있지 않습니다."),
                onStateChange = onStateChange
            )
            return
        }

        onStateChange(AutomationUiState.Running("Null Keyboard로 전환 중"))
        val imeSwitchResult = imeManager.switchToNullKeyboard()
        if (imeSwitchResult is ImeSwitchResult.Failure) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                lastStep = "Null Keyboard로 전환 중",
                state = AutomationUiState.Failure(
                    "${imeSwitchResult.message} WRITE_SECURE_SETTINGS 권한과 Null Keyboard 설치 상태를 확인해주세요."
                ),
                onStateChange = onStateChange
            )
            return
        }
        val imeSession = (imeSwitchResult as ImeSwitchResult.Success).session

        val run = CurrentRun(
            startedAtMillis = startedAtMillis,
            imeSession = imeSession,
            service = service
        )
        currentRun = run

        onStateChange(AutomationUiState.Running("Gemini 앱 실행 중"))
        run.lastStep = "Gemini 앱 실행 중"
        if (!launchGeminiApp()) {
            finishRun(
                run = run,
                state = AutomationUiState.Failure("Gemini 앱을 찾지 못했습니다."),
                onStateChange = onStateChange
            )
            return
        }

        service.runOneShot(
            prompt = TEST_PROMPT,
            onStateChange = runStateCallback(
                run = run,
                onStateChange = onStateChange
            )
        )
    }

    fun cancel(onStateChange: (AutomationUiState) -> Unit) {
        val run = currentRun ?: return
        run.service.cancelCurrentRun()
        finishRun(
            run = run,
            state = AutomationUiState.Stopped,
            onStateChange = onStateChange
        )
    }

    private fun runStateCallback(
        run: CurrentRun,
        onStateChange: (AutomationUiState) -> Unit
    ): (AutomationUiState) -> Unit {
        return callback@{ state ->
            when (state) {
                is AutomationUiState.Running -> {
                    run.lastStep = state.step
                    onStateChange(state)
                }
                AutomationUiState.Success,
                AutomationUiState.Stopped,
                is AutomationUiState.Failure -> finishRun(run, state, onStateChange)
                AutomationUiState.Idle -> onStateChange(state)
            }
        }
    }

    private fun finishRun(
        run: CurrentRun,
        state: AutomationUiState,
        onStateChange: (AutomationUiState) -> Unit
    ) {
        if (run.finished) return

        run.finished = true
        onStateChange(AutomationUiState.Running("원래 입력기로 복구 중"))

        val restoreResult = imeManager.restore(run.imeSession)
        val imeRestoreMessage = restoreResult.message()
        val finalState = when (restoreResult) {
            ImeRestoreResult.Success -> state
            is ImeRestoreResult.Failure -> AutomationUiState.Failure(
                "입력기 복구 실패. 원래 입력기: " +
                    "${restoreResult.originalImeId}, 현재 입력기: " +
                    "${restoreResult.currentImeId ?: "확인 불가"}"
            )
        }

        runLogger.append(
            AutomationRunLog(
                startedAtMillis = run.startedAtMillis,
                finishedAtMillis = clock(),
                status = AutomationRunLogStatus.fromState(finalState),
                lastStep = run.lastStep,
                message = finalState.message(),
                imeRestoreMessage = imeRestoreMessage
            )
        )
        currentRun = null
        onStateChange(finalState)
    }

    private fun finishWithoutRun(
        startedAtMillis: Long,
        lastStep: String,
        state: AutomationUiState,
        onStateChange: (AutomationUiState) -> Unit
    ) {
        runLogger.append(
            AutomationRunLog(
                startedAtMillis = startedAtMillis,
                finishedAtMillis = clock(),
                status = AutomationRunLogStatus.fromState(state),
                lastStep = lastStep,
                message = state.message(),
                imeRestoreMessage = "해당 없음"
            )
        )
        onStateChange(state)
    }

    private fun ImeRestoreResult.message(): String {
        return when (this) {
            ImeRestoreResult.Success -> "성공"
            is ImeRestoreResult.Failure -> "실패: 원래 입력기 ${originalImeId}, 현재 입력기 ${currentImeId ?: "확인 불가"}"
        }
    }

    private fun AutomationUiState.message(): String {
        return when (this) {
            AutomationUiState.Idle -> ""
            is AutomationUiState.Running -> step
            AutomationUiState.Success -> "성공"
            AutomationUiState.Stopped -> "사용자 중지"
            is AutomationUiState.Failure -> message
        }
    }

    private data class CurrentRun(
        val startedAtMillis: Long,
        val imeSession: ImeSwitchSession,
        val service: OneShotAutomationService,
        var lastStep: String = "시작",
        var finished: Boolean = false
    )

    companion object {
        const val TEST_PROMPT = "M3 자동 전송 테스트입니다. 숫자 1만 답변해."
    }
}
