package com.example.gemgemgen

import android.content.Context
import android.content.Intent

class GeminiMvpAutomation(
    private val imeManager: ImeManager,
    private val runLogger: RunLogger,
    private val clock: () -> Long,
    private val serviceProvider: () -> OneShotAutomationService?,
    private val launchGeminiApp: () -> Boolean,
    private val loadWildcards: () -> List<WildcardSet>,
    private val promptGenerator: PromptGenerator = PromptGenerator(),
    private val generatePrompt: (String, List<WildcardSet>, Int) -> GeneratedPrompt = { prompt, wildcards, index ->
        promptGenerator.generate(
            basePrompt = prompt,
            wildcardSets = wildcards,
            repeatCount = 1
        ).single().copy(index = index)
    }
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
        },
        loadWildcards = { WildcardRepository(context).load() }
    )

    private var currentRun: CurrentRun? = null

    fun run(
        promptTemplate: String,
        repeatCountText: String,
        onStateChange: (AutomationUiState) -> Unit
    ) {
        if (currentRun != null) {
            onStateChange(AutomationUiState.Failure("이미 실행 중입니다."))
            return
        }

        val startedAtMillis = clock()
        val repeatCount = RepeatCountParser.parse(repeatCountText)
        val service = serviceProvider()
        if (service == null) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "접근성 서비스 확인",
                state = AutomationUiState.Failure("접근성 서비스가 켜져 있지 않습니다."),
                onStateChange = onStateChange
            )
            return
        }

        if (promptTemplate.isBlank()) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "프롬프트 확인",
                state = AutomationUiState.Failure("원본 프롬프트를 입력하거나 클립보드에서 가져오세요."),
                onStateChange = onStateChange
            )
            return
        }

        onStateChange(AutomationUiState.Running("와일드카드 파일 로드 중"))
        val wildcards = try {
            loadWildcards()
        } catch (error: Exception) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "와일드카드 파일 로드 중",
                state = AutomationUiState.Failure(
                    "와일드카드 파일을 읽지 못했습니다: ${error.message ?: "폴더를 다시 선택해주세요."}"
                ),
                onStateChange = onStateChange
            )
            return
        }

        onStateChange(AutomationUiState.Running("Null Keyboard로 전환 중"))
        val imeSwitchResult = imeManager.switchToNullKeyboard()
        if (imeSwitchResult is ImeSwitchResult.Failure) {
            finishWithoutRun(
                startedAtMillis = startedAtMillis,
                repeatCount = repeatCount,
                lastStep = "Null Keyboard로 전환 중",
                state = AutomationUiState.Failure(
                    "${imeSwitchResult.message} WRITE_SECURE_SETTINGS 권한과 Null Keyboard 설치 상태를 확인해주세요."
                ),
                onStateChange = onStateChange
            )
            return
        }

        val run = CurrentRun(
            startedAtMillis = startedAtMillis,
            imeSession = (imeSwitchResult as ImeSwitchResult.Success).session,
            service = service,
            promptTemplate = promptTemplate,
            repeatCount = repeatCount,
            wildcards = wildcards
        )
        currentRun = run

        updateRunState(run, "Gemini 앱 실행 중", onStateChange)
        if (!launchGeminiApp()) {
            finishRun(
                run = run,
                state = AutomationUiState.Failure("Gemini 앱을 찾지 못했습니다."),
                onStateChange = onStateChange
            )
            return
        }

        sendMarker(run, onStateChange)
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

    private fun sendMarker(
        run: CurrentRun,
        onStateChange: (AutomationUiState) -> Unit
    ) {
        updateRunState(run, "세션 마커 전송 중", onStateChange)
        run.service.sendPrompt(
            prompt = MARKER_PROMPT,
            onStateChange = childStateCallback(run, onStateChange),
            onDone = {
                run.markerStatus = "성공"
                sendNextPrompt(run, onStateChange)
            },
            startDelayMillis = APP_LAUNCH_WAIT_MS
        )
    }

    private fun sendNextPrompt(
        run: CurrentRun,
        onStateChange: (AutomationUiState) -> Unit
    ) {
        if (run.successCount >= run.repeatCount) {
            finishRun(run, AutomationUiState.Success, onStateChange)
            return
        }

        val nextIndex = run.successCount + 1
        run.currentIndex = nextIndex
        val generatedPrompt = generatePrompt(run.promptTemplate, run.wildcards, nextIndex)

        run.lastPrompt = generatedPrompt.finalPrompt
        updateRunState(run, "프롬프트 생성 완료", onStateChange)

        run.service.sendPrompt(
            prompt = generatedPrompt.finalPrompt,
            onStateChange = childStateCallback(run, onStateChange),
            onDone = {
                run.successCount += 1
                run.completedCount += 1
                sendNextPrompt(run, onStateChange)
            }
        )
    }

    private fun childStateCallback(
        run: CurrentRun,
        onStateChange: (AutomationUiState) -> Unit
    ): (AutomationUiState) -> Unit {
        return { state ->
            when (state) {
                is AutomationUiState.Running -> updateRunState(run, state.step, onStateChange)
                is AutomationUiState.Failure -> {
                    run.failureCount += 1
                    finishRun(run, state, onStateChange)
                }
                AutomationUiState.Success -> Unit
                AutomationUiState.Stopped -> finishRun(run, state, onStateChange)
                AutomationUiState.Idle -> onStateChange(state)
            }
        }
    }

    private fun updateRunState(
        run: CurrentRun,
        step: String,
        onStateChange: (AutomationUiState) -> Unit
    ) {
        run.lastStep = step
        onStateChange(
            AutomationUiState.Running(
                step = step,
                currentIndex = run.currentIndex.coerceAtMost(run.repeatCount),
                totalCount = run.repeatCount,
                lastPrompt = run.lastPrompt
            )
        )
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

        runLogger.append(run.toLog(finalState, imeRestoreMessage, clock()))
        currentRun = null
        onStateChange(finalState)
    }

    private fun finishWithoutRun(
        startedAtMillis: Long,
        repeatCount: Int,
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
                imeRestoreMessage = "해당 없음",
                repeatCount = repeatCount
            )
        )
        onStateChange(state)
    }

    private fun CurrentRun.toLog(
        state: AutomationUiState,
        imeRestoreMessage: String,
        finishedAtMillis: Long
    ): AutomationRunLog {
        return AutomationRunLog(
            startedAtMillis = startedAtMillis,
            finishedAtMillis = finishedAtMillis,
            status = AutomationRunLogStatus.fromState(state),
            lastStep = lastStep,
            message = state.message(),
            imeRestoreMessage = imeRestoreMessage,
            repeatCount = repeatCount,
            completedCount = completedCount,
            successCount = successCount,
            failureCount = failureCount,
            markerStatus = markerStatus
        )
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
        val promptTemplate: String,
        val repeatCount: Int,
        val wildcards: List<WildcardSet>,
        var markerStatus: String = "실패",
        var currentIndex: Int = 0,
        var completedCount: Int = 0,
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var lastStep: String = "시작",
        var lastPrompt: String = "",
        var finished: Boolean = false
    )

    companion object {
        const val MARKER_PROMPT = "자연수의 가장 첫 번째 숫자는? 숫자 하나로만 답변해."
        private const val APP_LAUNCH_WAIT_MS = 1500L
    }
}
